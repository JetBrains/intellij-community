// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.WizardSettingsFactory
import com.intellij.ide.wizard.WizardSettingsProvider
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import javax.swing.JComponent
import javax.swing.JLabel

interface NewProjectWizard<S> : WizardSettingsFactory<S> {
  val language: String

  fun enabled(): Boolean = true
  fun settingsList(settings: S, context: WizardContext): List<SettingsComponent>
  fun setupProject(project: Project, settings: S, context: WizardContext)

  companion object {
    val EP_WIZARD = ExtensionPointName<NewProjectWizard<*>>("com.intellij.newProjectWizard")
  }
}

class NewProjectWizardWithSettings<S>(
  private val wizard: NewProjectWizard<S>
) : NewProjectWizard<S> by wizard, WizardSettingsProvider<S> {

  override val settings by lazy(::createSettings)

  fun settingsList(context: WizardContext): List<SettingsComponent> {
    settingsKey.set(context, settings)
    return settingsList(settings, context)
  }

  fun setupProject(project: Project, context: WizardContext) {
    settingsKey.set(context, settings)
    setupProject(project, settings, context)
  }
}


sealed class SettingsComponent(val component: JComponent)
class LabelAndComponent(val label: JLabel, component: JComponent) : SettingsComponent(component)
class JustComponent(component: JComponent) : SettingsComponent(component)
