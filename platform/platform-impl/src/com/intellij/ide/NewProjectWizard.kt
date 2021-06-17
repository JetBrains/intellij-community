// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import javax.swing.JComponent
import javax.swing.JLabel

interface NewProjectWizard<T> {
  val language: String
  var settingsFactory: () -> T

  fun enabled(): Boolean = true
  fun settingsList(settings: T): List<SettingsComponent> = emptyList()
  fun setupProject(project: Project, settings: T, context: WizardContext) { }

  companion object {
    var EP_WIZARD = ExtensionPointName<NewProjectWizard<*>>("com.intellij.newProjectWizard")
  }
}

class NewProjectWizardWithSettings<T>(wizard: NewProjectWizard<T>) : NewProjectWizard<T> by wizard {
  var settings : T = settingsFactory.invoke()

  fun settingsList() = settingsList(settings)
  fun setupProject(project: Project, context: WizardContext) = setupProject(project, settings, context)
}


sealed class SettingsComponent(val component: JComponent)
class LabelAndComponent(val label: JLabel? = null, component: JComponent) : SettingsComponent(component)
class JustComponent(component: JComponent) : SettingsComponent(component)
