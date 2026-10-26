// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.analysisignore

import com.intellij.lang.LangBundle
import com.intellij.openapi.fileTypes.impl.IgnoredFilesAndFoldersPanel
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

/**
 * The page of the [default exclusions][AnalysisIgnoreDefaults.lines] under Languages & Frameworks. The user edits the lines in a list,
 * and the page stores them in [AnalysisIgnoreDefaultsSettings].
 */
@ApiStatus.Internal
class AnalysisIgnoreDefaultsConfigurable : SearchableConfigurable, Configurable.NoScroll {
  private var listPanel: IgnoredFilesAndFoldersPanel? = null

  // A line that the next reset selects and highlights, see [selectDefaultExclusion].
  private var lineToSelect: String? = null

  override fun getId(): String = ID

  override fun getDisplayName(): String = LangBundle.message("analysis.ignore.settings.defaults.display.name")

  override fun createComponent(): JComponent {
    val listPanel = IgnoredFilesAndFoldersPanel(
      LangBundle.message("analysis.ignore.settings.default.exclusions.hint"),
      AnalysisIgnoreDefaults::isSupportedLine,
      LangBundle.message("analysis.ignore.settings.default.exclusions.error.unsupported"),
      false,
    )
    this.listPanel = listPanel
    return panel {
      row {
        cell(listPanel).align(Align.FILL)
      }.resizableRow()
    }
  }

  /** The list sorts the lines, so the order of the lines does not count. */
  override fun isModified(): Boolean {
    val listPanel = listPanel ?: return false
    return listPanel.patterns.toSet() != AnalysisIgnoreDefaultsSettings.getInstance().lines.toSet()
  }

  override fun apply() {
    val listPanel = listPanel ?: return
    if (isModified) AnalysisIgnoreDefaultsSettings.getInstance().setLines(listPanel.patterns)
  }

  override fun reset() {
    val listPanel = listPanel ?: return
    listPanel.setPatterns(AnalysisIgnoreDefaultsSettings.getInstance().lines)
    lineToSelect?.let {
      listPanel.highlightPattern(it)
      lineToSelect = null
    }
  }

  override fun disposeUIResources() {
    listPanel = null
  }

  /**
   * Selects [line] in the list with the highlight of the Settings search. Before the component exists, the next [reset] does it. "Cancel
   * Exclusion" calls it for the default line that excludes an item.
   */
  @RequiresEdt
  fun selectDefaultExclusion(line: String) {
    val listPanel = listPanel
    if (listPanel == null) {
      lineToSelect = line
    }
    else {
      listPanel.highlightPattern(line)
    }
  }

  /** Shows the page only while the [defaults][AnalysisIgnoreDefaults.areEnabled] are on. */
  class Provider : ConfigurableProvider() {
    override fun canCreateConfigurable(): Boolean = AnalysisIgnoreDefaults.areEnabled()

    override fun createConfigurable(): Configurable = AnalysisIgnoreDefaultsConfigurable()
  }

  private companion object {
    const val ID: String = "analysis.ignore.defaults"
  }
}
