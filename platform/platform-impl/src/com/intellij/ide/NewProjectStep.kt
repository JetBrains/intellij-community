// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide

import com.intellij.ide.NewProjectWizard.Companion.EP_WIZARD
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.UIBundle
import com.intellij.ui.layout.*
import com.intellij.util.containers.map2Array
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.lang.Integer.max
import javax.swing.DefaultComboBoxModel
import javax.swing.JLabel

class NewProjectStep : NewModuleStep<NewProjectStepSettings>() {
  private val settingsMap = mutableMapOf<String, List<SettingsComponent>>()
  private val rows = mutableMapOf<String, List<Row>>()

  val wizards: List<NewProjectWizardWithSettings<out Any?>> = EP_WIZARD.extensions.filter { it.enabled() }
    .map { NewProjectWizardWithSettings(it) }
    .onEach { settingsMap[it.language] = it.settingsList() }

  private var languages = wizards.map { it.language }
  override var settings = NewProjectStepSettings(languages.first())

  init {
    settings.languageProperty.afterPropagation {
      rows.values.forEach { it.forEach { it.visible = false } }
      rows[settings.languageProperty.get()]?.forEach { it.visible = true }
    }
  }

  override var panel: DialogPanel = panel {
    lateinit var label: CellBuilder<JLabel>
    row {
      label = label(UIBundle.message("label.project.wizard.new.project.name"))
      textField(baseSettings::name)
    }.largeGapAfter()

    row(UIBundle.message("label.project.wizard.new.project.location")) {
      textFieldWithBrowseButton(baseSettings::path, UIBundle.message("dialog.title.project.name"), /*context.project*/null,
                                FileChooserDescriptorFactory.createSingleFolderDescriptor())
    }.largeGapAfter()

    gitCheckbox()
    row(UIBundle.message("label.project.wizard.new.project.language")) {
      if (languages.size > 4) {
        comboBox(DefaultComboBoxModel(languages.map2Array { it }), settings.languageProperty)
      } else {
        buttonSelector(languages, settings.languageProperty) { it }
      }
    }.largeGapAfter()

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

    settings.languageProperty.set(languages.first())
  }.withBorder(JBUI.Borders.empty(10, 10))
}

class NewProjectStepSettings(val initialLanguage: String) {
  val propertyGraph: PropertyGraph = PropertyGraph()
  val languageProperty: GraphProperty<String> = propertyGraph.graphProperty { initialLanguage }
}
