// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardStepSettings
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.Key
import com.intellij.ui.UIBundle
import com.intellij.ui.layout.*

class NewProjectStep(context: WizardContext) : NewModuleStep(context) {

  override val steps = super.steps + Step(context)

  class Step(private val context: WizardContext) : NewProjectWizardStep<Settings> {
    override val settings = Settings(context)

    override fun setupUI(builder: RowBuilder) {
      with(builder) {
        val steps = NewProjectWizard.EP_NAME.extensionList
          .filter { it.enabled() }
          .map { LanguageStep(it.language, it.createStep(context)) }

        row(UIBundle.message("label.project.wizard.new.project.language")) {
          buttonSelector(steps, settings.languageProperty) { it.name }
        }

        val stepsControllers = HashMap<String, DialogPanel>()
        for (step in steps) {
          stepsControllers[step.name] = nestedPanel {
            step.setupUI(this)
          }.component
        }
        settings.languageProperty.afterChange {
          stepsControllers.values.forEach { it.isVisible = false }
          stepsControllers[settings.language.name]?.isVisible = true
        }
        settings.language = steps.first()
      }
    }

    override fun setupProject(project: Project) {
      settings.language.setupProject(project)
    }
  }

  class LanguageStep<S : NewProjectWizardStepSettings<S>>(
    val name: String,
    step: NewProjectWizardStep<S>
  ) : NewProjectWizardStep<S> by step {
    override fun toString() = name
  }

  class Settings(context: WizardContext) : NewProjectWizardStepSettings<Settings>(KEY, context) {
    val languageProperty = propertyGraph.graphProperty<LanguageStep<*>> { throw UninitializedPropertyAccessException() }

    var language by languageProperty

    companion object {
      val KEY = Key.create<Settings>(Settings::class.java.name)
    }
  }
}
