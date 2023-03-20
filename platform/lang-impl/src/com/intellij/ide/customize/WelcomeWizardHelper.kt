// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.ide.ApplicationInitializedListener
import com.intellij.ide.WelcomeWizardUtil
import com.intellij.ide.projectView.impl.ProjectViewSharedSettings
import com.intellij.ide.ui.UISettings
import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.util.registry.Registry
import kotlinx.coroutines.CoroutineScope

private class WelcomeWizardHelper : ApplicationInitializedListener {
  init {
    if (ApplicationManager.getApplication().isHeadlessEnvironment) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(asyncScope: CoroutineScope) {
    // project View settings
    WelcomeWizardUtil.getAutoScrollToSource()?.let {
      ProjectViewSharedSettings.instance.autoscrollToSource = it
    }
    WelcomeWizardUtil.getManualOrder()?.let {
      ProjectViewSharedSettings.instance.manualOrder = it
    }

    // debugger settings
    WelcomeWizardUtil.getDisableBreakpointsOnClick()?.let{
      Registry.get("debugger.click.disable.breakpoints").setValue(it)
    }

    // code insight settings
    WelcomeWizardUtil.getCompletionCaseSensitive()?.let {
      CodeInsightSettings.getInstance().COMPLETION_CASE_SENSITIVE = it
    }

    // code style settings
    WelcomeWizardUtil.getContinuationIndent()?.let {
      Language.getRegisteredLanguages()
        .asSequence()
        .map { CodeStyle.getDefaultSettings().getIndentOptions(it.associatedFileType) }
        .filter { it.CONTINUATION_INDENT_SIZE > WelcomeWizardUtil.getContinuationIndent() }
        .forEach { it.CONTINUATION_INDENT_SIZE = WelcomeWizardUtil.getContinuationIndent() }
    }

    // UI settings
    WelcomeWizardUtil.getTabsPlacement()?.let {
      UISettings.getInstance().editorTabPlacement = it
    }

    WelcomeWizardUtil.getAppearanceFontSize()?.let {
      val settings = UISettings.getInstance()
      settings.overrideLafFonts = true
      UISettings.getInstance().fontSize = it
    }

    WelcomeWizardUtil.getAppearanceFontFace()?.let {
      val settings = UISettings.getInstance()
      settings.overrideLafFonts = true
      settings.fontFace = it
    }
  }
}
