// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.Key
import com.intellij.ui.UIBundle
import com.intellij.ui.layout.*
import javax.swing.DefaultComboBoxModel

class NewProjectStep(private val context: WizardContext) : NewModuleStep(context) {
  private val settings = NewProjectStepSettings()

  override fun setupUI(builder: LayoutBuilder) {
    super.setupUI(builder)
    with(builder) {
      val wizards = NewProjectWizard.EP_NAME.extensionList
        .filter { it.enabled() }
        .map { NewProjectWizardWithSettings(it) }
      row(UIBundle.message("label.project.wizard.new.project.language")) {
        if (languageSettings.size > 4) {
          comboBox(DefaultComboBoxModel(wizards), settings.wizardProperty)
        } else {
          buttonSelector(wizards, settings.wizardProperty) { it.language }
        }
      }.largeGapAfter()

      val rows = mutableMapOf<String, List<Row>>()
      settings.wizardProperty.afterPropagation {
        rows.values.forEach { it.forEach { it.visible = false } }
        rows[settings.wizard.language]?.forEach { it.visible = true }
      }

      for (wizard in wizards) {
        rows[wizard.language] = wizard.settingsList(context).map { lc ->
          when (lc) {
            is LabelAndComponent -> row(lc.label) {
              component(lc.component)
            }
            is JustComponent -> row {
              lc.component(CCFlags.growX)
            }
          }
            .onGlobalApply { if (lc.component is DialogPanel) lc.component.apply() }
            .apply { visible = false }
            .apply { largeGapAfter() }
        }
      }

      settings.wizard = wizards.first()
    }
  }

  override fun setupProject(project: Project) {
    super.setupProject(project)
    settings.wizard.setupProject(project, context)
  }

  init {
    NewProjectStepSettings.KEY.set(context, settings)
  }
}

class NewProjectStepSettings {
  val propertyGraph: PropertyGraph = PropertyGraph()

  val wizardProperty = propertyGraph.graphProperty<NewProjectWizardWithSettings<*>> {
    throw UninitializedPropertyAccessException()
  }

  var wizard by wizardProperty

  companion object {
    val KEY = Key.create<NewProjectStepSettings>(NewProjectStepSettings::class.java.name)
  }
}
