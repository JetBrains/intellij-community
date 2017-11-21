/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.ide.customize

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.ide.WelcomeWizardUtil
import com.intellij.ide.projectView.impl.ProjectViewSharedSettings
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.UISettings
import com.intellij.lang.Language
import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.codeStyle.*

class WelcomeWizardHelper : ApplicationComponent {
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
