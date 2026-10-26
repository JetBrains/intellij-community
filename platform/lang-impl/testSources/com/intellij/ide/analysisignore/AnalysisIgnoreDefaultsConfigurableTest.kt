// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.analysisignore

import com.intellij.openapi.fileTypes.impl.IgnoredFilesAndFoldersPanel
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import com.intellij.util.ui.UIUtil
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.swing.JComponent
import javax.swing.JList

private const val ENABLED = "ide.analysisignore.file.enabled"

private const val DEFAULTS = "ide.analysisignore.defaults.enabled"

@TestApplication
class AnalysisIgnoreDefaultsConfigurableTest {
  private val settings get() = AnalysisIgnoreDefaultsSettings.getInstance()

  @AfterEach
  fun tearDown() {
    settings.setLines(AnalysisIgnoreDefaults.BUILT_IN_LINES)
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  @RegistryKey(key = DEFAULTS, value = "true")
  fun `the page shows while the defaults are on`() {
    assertTrue(AnalysisIgnoreDefaultsConfigurable.Provider().canCreateConfigurable())
  }

  @Test
  @RegistryKey(key = ENABLED, value = "true")
  @RegistryKey(key = DEFAULTS, value = "false")
  fun `the page is absent while the defaults are off`() {
    assertFalse(AnalysisIgnoreDefaultsConfigurable.Provider().canCreateConfigurable())
  }

  @Test
  fun `the list shows the built-in lines and the page is not modified`() {
    val (lines, modified) = withComponent(AnalysisIgnoreDefaultsConfigurable()) { configurable, component ->
      valuesOf(defaultsListOf(component)) to configurable.isModified
    }

    assertEquals(AnalysisIgnoreDefaults.BUILT_IN_LINES.sorted(), lines)
    assertFalse(modified)
  }

  @Test
  fun `the list shows the edited default lines`() {
    settings.setLines(listOf("node_modules", "/build/"))

    val lines = withComponent(AnalysisIgnoreDefaultsConfigurable()) { _, component -> valuesOf(defaultsListOf(component)) }

    assertEquals(listOf("/build/", "node_modules"), lines)
  }

  @Test
  fun `apply stores the lines of the list`() {
    val modified = withComponent(AnalysisIgnoreDefaultsConfigurable()) { configurable, component ->
      checkNotNull(UIUtil.findComponentOfType(component, IgnoredFilesAndFoldersPanel::class.java)).setPatterns(listOf("/build/"))
      val modified = configurable.isModified
      configurable.apply()
      modified
    }

    assertTrue(modified)
    assertTrue(settings.isCustomized)
    assertEquals(listOf("/build/"), settings.lines)
  }

  @Test
  fun `a line selected before the component exists is highlighted after the reset`() {
    val configurable = AnalysisIgnoreDefaultsConfigurable()
    runInEdtAndGet { configurable.selectDefaultExclusion("/target/") }

    assertEquals("/target/", highlightedDefaultExclusionOf(configurable))
  }

  @Test
  fun `no line is highlighted without a request`() {
    assertNull(highlightedDefaultExclusionOf(AnalysisIgnoreDefaultsConfigurable()))
  }
}

/**
 * Creates the component of [configurable] and resets it, as the Settings dialog does. Returns the line that the list selects with the
 * highlight of the Settings search, or `null`.
 */
internal fun highlightedDefaultExclusionOf(configurable: AnalysisIgnoreDefaultsConfigurable): String? =
  withComponent(configurable) { _, component ->
    val list = defaultsListOf(component)
    val index = list.selectedIndex
    if (index < 0 || !isSearchMatch(list, index)) null else list.model.getElementAt(index)
  }

private fun <T> withComponent(
  configurable: AnalysisIgnoreDefaultsConfigurable,
  block: (AnalysisIgnoreDefaultsConfigurable, JComponent) -> T,
): T = runInEdtAndGet {
  val component = configurable.createComponent()
  try {
    configurable.reset()
    block(configurable, component)
  }
  finally {
    configurable.disposeUIResources()
  }
}

private fun defaultsListOf(component: JComponent): JList<String> {
  @Suppress("UNCHECKED_CAST")
  return checkNotNull(UIUtil.findComponentOfType(component, JBList::class.java)) as JList<String>
}

private fun valuesOf(list: JList<String>): List<String> = (0 until list.model.size).map { list.model.getElementAt(it) }

/** Returns `true` if the renderer paints the item at [index] with the highlight of the Settings search. */
private fun isSearchMatch(list: JList<String>, index: Int): Boolean {
  val renderer = list.cellRenderer.getListCellRendererComponent(list, list.model.getElementAt(index), index, false, false)
  val iterator = (renderer as SimpleColoredComponent).iterator()
  while (iterator.hasNext()) {
    iterator.next()
    if (iterator.textAttributes.style and SimpleTextAttributes.STYLE_SEARCH_MATCH != 0) return true
  }
  return false
}
