// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.customize

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.ide.WelcomeWizardUtil
import com.intellij.ide.projectView.impl.ProjectViewSharedSettings
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.UISettings
import com.intellij.lang.Language
import com.intellij.openapi.components.BaseComponent
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.codeStyle.CodeStyleSettingsManager

class WelcomeWizardHelper : BaseComponent {
  override fun initComponent() {
    //Project View settings
    WelcomeWizardUtil.getAutoScrollToSource()?.let {
        ProjectViewSharedSettings.instance.autoscrollToSource = it
    }
    WelcomeWizardUtil.getManualOrder()?.let {
      ProjectViewSharedSettings.instance.manualOrder = it
    }

    //Debugger settings
    WelcomeWizardUtil.getDisableBreakpointsOnClick()?.let{
      Registry.get("debugger.click.disable.breakpoints").setValue(it)
    }

    //Code insight settings
    WelcomeWizardUtil.getCompletionCaseSensitive()?.let {
      CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE = it
    }
    //Code style settings
    WelcomeWizardUtil.getContinuationIndent()?.let {
      Language.getRegisteredLanguages()
        .map { CodeStyleSettingsManager.getInstance().currentSettings.getIndentOptions(it.associatedFileType) }
        .filter { it.CONTINUATION_INDENT_SIZE > WelcomeWizardUtil.getContinuationIndent() }
        .forEach { it.CONTINUATION_INDENT_SIZE = WelcomeWizardUtil.getContinuationIndent() }
    }
    //UI settings
    WelcomeWizardUtil.getTabsPlacement()?.let {
      UISettings.instance.editorTabPlacement = it
    }
    WelcomeWizardUtil.getAppearanceFontSize()?.let {
      UISettings.instance.overrideLafFonts = true
      UISettings.instance.fontSize = it
    }
    WelcomeWizardUtil.getAppearanceFontFace()?.let {
      UISettings.instance.overrideLafFonts = true
      UISettings.instance.fontFace = it
    }
    LafManager.getInstance().updateUI()
  }
}
