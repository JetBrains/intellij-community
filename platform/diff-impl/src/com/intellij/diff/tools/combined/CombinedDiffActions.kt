// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.tools.combined

import com.intellij.diff.tools.util.FoldingModelSupport
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder
import com.intellij.diff.tools.util.base.TextDiffViewerUtil
import com.intellij.diff.tools.util.text.SmartTextDiffProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.project.DumbAware
import com.intellij.ui.ToggleActionButton

internal class CombinedToggleExpandByDefaultAction(val textSettings: TextDiffSettingsHolder.TextDiffSettings,
                                                   val foldingModels: List<FoldingModelSupport>) :
  ToggleActionButton(DiffBundle.message("collapse.unchanged.fragments"), AllIcons.Actions.Collapseall), DumbAware {

  override fun isVisible(): Boolean = textSettings.contextRange != -1

  override fun isSelected(e: AnActionEvent): Boolean = !textSettings.isExpandByDefault

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val expand = !state
    if (textSettings.isExpandByDefault == expand) return
    textSettings.isExpandByDefault = expand
    expandAll(expand)
  }

  private fun expandAll(expand: Boolean) {
    foldingModels.forEach { it.expandAll(expand) }
  }
}

internal class CombinedIgnorePolicySettingAction(settings: TextDiffSettingsHolder.TextDiffSettings) :
  TextDiffViewerUtil.IgnorePolicySettingAction(settings, *SmartTextDiffProvider.IGNORE_POLICIES)

internal class CombinedHighlightPolicySettingAction(settings: TextDiffSettingsHolder.TextDiffSettings) :
  TextDiffViewerUtil.HighlightPolicySettingAction(settings, *SmartTextDiffProvider.HIGHLIGHT_POLICIES)
