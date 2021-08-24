// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide

import com.intellij.ide.NewProjectWizard.Companion.EP_WIZARD
import com.intellij.ide.util.installNameGenerators
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.Key
import com.intellij.ui.UIBundle
import com.intellij.ui.layout.*
import com.intellij.util.containers.map2Array
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.lang.Integer.max
import javax.swing.DefaultComboBoxModel
import javax.swing.JLabel

class NewProjectStep(context: WizardContext) : NewModuleStepWithSettings<NewProjectStepSettings>(context) {
  override val settingsKey = NewProjectStepSettings.KEY
  override fun createSettings() = NewProjectStepSettings()

  override var panel: DialogPanel = panel {
    lateinit var label: CellBuilder<JLabel>
    row {
      label = label(UIBundle.message("label.project.wizard.new.project.name"))
      textField(baseSettings.nameProperty)
        .constraints(pushX)
        .focused()
      installNameGenerators(getBuilderId(), baseSettings.nameProperty)
    }.largeGapAfter()

    row(UIBundle.message("label.project.wizard.new.project.location")) {
      textFieldWithBrowseButton(baseSettings.pathProperty, UIBundle.message("dialog.title.project.name"), context.project,
        FileChooserDescriptorFactory.createSingleFolderDescriptor())
    }.largeGapAfter()

    gitCheckbox()

    val settingsMap = HashMap<String, List<SettingsComponent>>()
    val languageSettings = EP_WIZARD.extensionList
      .filter { it.enabled() }
      .map { NewProjectWizardWithSettings(it) }
      .map { NewProjectWizardSettingsComponent(it, it.settingsList(context)) }
      .onEach { settingsMap[it.settings.language] = it.settingsList }
    row(UIBundle.message("label.project.wizard.new.project.language")) {
      if (languageSettings.size > 4) {
        comboBox(DefaultComboBoxModel(languageSettings), settings.languageProperty)
      } else {
        buttonSelector(languageSettings, settings.languageProperty) { it.settings.language }
      }
    }.largeGapAfter()

    val rows = mutableMapOf<String, List<Row>>()
    settings.languageProperty.afterPropagation {
      rows.values.forEach { it.forEach { it.visible = false } }
      rows[settings.language.settings.language]?.forEach { it.visible = true }
    }

    var maxWidth = label.component.preferredSize.width
    settingsMap.entries.forEach {
      rows[it.key] =
        it.value.map { lc ->
          when (lc) {
            is LabelAndComponent -> {
              maxWidth = max(maxWidth, lc.label.preferredSize.width)
              row(lc.label) {
                component(lc.component)
              }
            }
            is JustComponent -> row { lc.component(CCFlags.growX) }
          }
            .onGlobalApply { if (lc.component is DialogPanel) lc.component.apply() }
            .apply { visible = false }
            .apply { largeGapAfter() }
        }
    }

    val minimumSize = label.component.minimumSize
    minimumSize?.let { label.component.minimumSize = Dimension(maxWidth, it.height) }

    settings.language = languageSettings.first()
  }.withBorder(JBUI.Borders.empty(10, 10))

  override fun setupProject(project: Project, settings: NewProjectStepSettings, context: WizardContext) {
    settings.language.settings.setupProject(project, context)
  }
}

class NewProjectWizardSettingsComponent<S>(
  val settings: NewProjectWizardWithSettings<S>,
  val settingsList: List<SettingsComponent>
)

class NewProjectStepSettings {
  val propertyGraph: PropertyGraph = PropertyGraph()

  val languageProperty = propertyGraph.graphProperty<NewProjectWizardSettingsComponent<*>> {
    throw UninitializedPropertyAccessException()
  }

  var language by languageProperty

  companion object {
    val KEY = Key.create<NewProjectStepSettings>(NewProjectStepSettings::class.java.name)
  }
}
