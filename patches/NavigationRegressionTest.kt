package com.monoposto.championship

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import android.graphics.Bitmap
import java.io.File
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.monoposto.championship.data.*
import com.monoposto.championship.domain.ChampionshipEngine
import com.monoposto.championship.ui.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationRegressionTest {
    @get:Rule val compose = createComposeRule()
    private fun fixture(): Championship {
        val c = ChampionshipFactory.create("Navigation QA", "2026", 3)
        val results = c.drivers.mapIndexed { i, d -> DriverRaceResult(d.id, i + 1, ResultStatus.FINISHED, teamId = d.teamId) }
        return ChampionshipEngine.recalculateAll(c.copy(races = c.races.map { it.copy(results = results) }))
    }
    private fun screenshot(name: String) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(context.getExternalFilesDir(null), "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun constructorThenDriverThenRaceWithOverlappingIds() {
        val c = fixture()
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repository = ChampionshipRepository(ChampionshipDatabase.get(context))
        kotlinx.coroutines.runBlocking { repository.save(c) }
        val vm = ChampionshipViewModel(context)
        compose.setContent { AppTheme { MonopostoApp(vm) } }
        compose.waitUntil(10_000) { !vm.state.value.loading }
        // NavigationBarItem merges icon contentDescription with label → must use unmerged tree
        compose.onNodeWithContentDescription("Standings", useUnmergedTree = true).performClick()
        c.teams.forEach { team ->
            compose.onNodeWithText("Constructors").performClick()
            compose.onNode(hasScrollAction()).performScrollToNode(hasText(team.name))
            compose.onNodeWithText(team.name).performClick()
            compose.onNodeWithText("Driver contribution").assertIsDisplayed()
            if (team.id == c.teams.first().id) screenshot("constructor")
            compose.onNodeWithContentDescription("Back").performClick()
        }
        compose.onNodeWithText("Drivers", useUnmergedTree = true).performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(c.drivers[0].name))
        compose.onNodeWithText(c.drivers[0].name).performClick()
        compose.onNodeWithText("Race-by-race performance").assertIsDisplayed()
        screenshot("driver")
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Races", useUnmergedTree = true).performClick()
        c.races.forEach { race ->
            compose.onNode(hasScrollAction()).performScrollToNode(hasText(race.name))
            compose.onNodeWithText(race.name).performClick()
            compose.onNodeWithText("View / edit race result").assertIsDisplayed()
            compose.onNodeWithContentDescription("Back").performClick()
        }
        kotlinx.coroutines.runBlocking { repository.delete(c.id) }
    }
    @Test fun homeAndStatsRenderWithLongNames() {
        val base = fixture()
        val c = base.copy(name = "A championship with a very long name for accessibility", drivers = base.drivers.map { it.copy(name = it.name + " — a long driver name") })
        var stats by mutableStateOf(false)
        compose.setContent { AppTheme { if (stats) StatsScreen(c, {}, {}) else HomeScreen(c, {}, {}) } }
        compose.onNodeWithText("Race control").assertIsDisplayed()
        screenshot("home")
        compose.runOnIdle { stats = true }
        compose.waitForIdle()
        compose.onNodeWithText("Season statistics").assertIsDisplayed()
        screenshot("stats")
    }
    @Test fun missingConstructorShowsRecoverableState() {
        compose.setContent { AppTheme { TeamDetailScreen(fixture(), -999) {} } }
        compose.onNodeWithText("This entry is no longer available").assertIsDisplayed()
    }
}
