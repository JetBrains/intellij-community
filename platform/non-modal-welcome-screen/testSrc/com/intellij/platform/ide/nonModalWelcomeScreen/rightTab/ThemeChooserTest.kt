// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.nonModalWelcomeScreen.rightTab

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.LafReference
import com.intellij.ide.ui.ThemeListProvider
import com.intellij.openapi.application.UI
import com.intellij.platform.ide.nonModalWelcomeScreen.rightTab.WelcomeScreenRightTabComboBoxModel.ThemeModel
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.components.JBList
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
internal class ThemeChooserTest {
  private val model = ThemeModel()

  private val themes: List<LafReference> get() = model.themes(filter = null)

  private val shownThemes get() = ThemeListProvider.getInstance().getShownThemes().infos.flatMap { it.items }

  private fun isDark(theme: LafReference): Boolean = shownThemes.first { it.id == theme.themeId }.isDark

  @Test
  fun aBrightnessFilterAcceptsOnlyThemesOfItsBrightness() {
    assertTrue(shownThemes.any { it.isDark } && shownThemes.any { !it.isDark }, "the test needs a light theme and a dark theme")
    for (theme in shownThemes) {
      assertEquals(theme.isDark, ThemeBrightnessFilter.DARK.accepts(theme), theme.name)
      assertEquals(!theme.isDark, ThemeBrightnessFilter.LIGHT.accepts(theme), theme.name)
    }
    assertEquals(ThemeBrightnessFilter.DARK, ThemeBrightnessFilter.of(isDark = true))
    assertEquals(ThemeBrightnessFilter.LIGHT, ThemeBrightnessFilter.of(isDark = false))
  }

  @Test
  fun lightAndDarkThemesSplitAllThemes() {
    val light = model.themes(ThemeBrightnessFilter.LIGHT)
    val dark = model.themes(ThemeBrightnessFilter.DARK)

    assertTrue(light.none(::isDark))
    assertTrue(dark.all(::isDark))
    assertEquals(themes.map { it.themeId }.toSet(), (light + dark).map { it.themeId }.toSet())
    assertEquals(themes.size, light.size + dark.size)
  }

  @Test
  fun customThemesGoAfterTheOtherThemesAndKeepTheirOrder() {
    val items = listOf("a", "B", "c", "D", "e")

    assertEquals(listOf("a", "c", "e", "B", "D"), items.customLast { it.first().isUpperCase() })
    assertEquals(items, items.customLast { false })
    assertEquals(items, items.customLast { true })
  }

  @Test
  fun everyBundledThemeComesBeforeEveryThemeOfAPlugin() {
    val isCustom = themes.map(model::isCustom)

    assertEquals(isCustom.sorted(), isCustom)
  }

  @Test
  fun aPinnedThemeIsShownInsteadOfTheActiveTheme() {
    val pinned = themes.last()
    model.pinnedItem = pinned
    try {
      assertEquals(pinned.name, model.currentItemName())
    }
    finally {
      model.pinnedItem = null
    }
  }

  @Test
  fun theIconOfAPinnedThemeFollowsTheBrightnessOfThePinnedTheme() {
    try {
      model.pinnedItem = themes.first(::isDark)
      assertEquals(AllIcons.MeetNewUi.DarkTheme, model.currentIcon())

      model.pinnedItem = themes.first { !isDark(it) }
      assertEquals(AllIcons.MeetNewUi.LightTheme, model.currentIcon())
    }
    finally {
      model.pinnedItem = null
    }
  }

  @Test
  fun anItemThatIsNotAThemeIsNotPreviewed() {
    assertFalse(model.preview(LafReference("More in Appearance", "")))
  }

  @Test
  fun rowsOfTheThemeListHaveOneSize(): Unit = timeoutRunBlocking {
    val moreItem = LafReference("More in Appearance", "")
    withContext(Dispatchers.UI) {
      val list = JBList<LafReference>()
      val renderer = createThemeListRenderer(list, model, moreItem, activeThemeId = themes.first().themeId) { null }

      val sizes = (themes + moreItem).map { renderer.getListCellRendererComponent(list, it, 0, false, false).preferredSize }

      assertEquals(1, sizes.map { it.width }.toSet().size, "the width of the rows")
      assertEquals(JBUI.scale(24), sizes.first().height)
      assertEquals(JBUI.scale(24), sizes.dropLast(1).map { it.height }.toSet().single(), "the height of the theme rows")
    }
  }
}
