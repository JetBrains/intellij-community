// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide

import com.intellij.ide.NewProjectWizard.Companion.EP_WIZARD
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.UIBundle
import com.intellij.ui.layout.*
import com.intellij.util.ui.JBUI

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
    nameAndPath()
    gitCheckbox()
    row(UIBundle.message("label.project.wizard.new.project.language")) {
      buttonSelector(languages, settings.languageProperty) { it }
    }.largeGapAfter()

    settingsMap.entries.forEach {
      rows[it.key] =
        it.value.map { lc ->
          when (lc) {
          is LabelAndComponent -> row(lc.label) {
            component(lc.component)
          }
          is JustComponent -> row { component(lc.component)}
        }
          .onGlobalApply { if (lc.component is DialogPanel) lc.component.apply() }
          .apply { visible = false }
            .apply { largeGapAfter() }
        }
    }

    settings.languageProperty.set(languages.first())
  }.withBorder(JBUI.Borders.empty(10, 10))
}

class NewProjectStepSettings(val initialLanguage: String) {
  val propertyGraph: PropertyGraph = PropertyGraph()
  val languageProperty: GraphProperty<String> = propertyGraph.graphProperty { initialLanguage }
}
