// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.util.PlatformUtils

class HTMLNewProjectWizard : NewProjectWizard<HTMLSettings> {
  override val language: String = "HTML"

  override val settingsKey = HTMLSettings.KEY
  override fun createSettings() = HTMLSettings()

  override fun enabled() = PlatformUtils.isCommunityEdition()

  override fun settingsList(settings: HTMLSettings, context: WizardContext) = emptyList<SettingsComponent>()
  override fun setupProject(project: Project, settings: HTMLSettings, context: WizardContext) {}
}

class HTMLSettings {
  companion object {
    val KEY = Key.create<HTMLSettings>(HTMLSettings::class.java.name)
  }
}