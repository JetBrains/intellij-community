// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide

import com.intellij.ide.NewProjectWizard.Companion.EP_WIZARD
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.UIBundle
import com.intellij.ui.layout.*
import com.intellij.util.ui.JBUI

class NewProjectStep : NewModuleStep<NewProjectStepSettings>() {
  private val settingsMap = mutableMapOf<String, List<LabelAndComponent>>()
  val wizards: List<NewProjectWizardWithSettings<out Any?>> = EP_WIZARD.extensions.filter { it.enabled() }
    .map { NewProjectWizardWithSettings(it) }
    .onEach { settingsMap[it.language] = it.settingsList() }

  override var settings = NewProjectStepSettings()

  val propertyGraph: PropertyGraph = PropertyGraph()
  private var languages = wizards.map { it.language }
  private val languageProperty = propertyGraph.graphProperty { languages.first() }

  init {
    languageProperty.afterPropagation {
      settingsMap.values.forEach { it.forEach { it.component.isVisible = false } }
      settingsMap[languageProperty.get()]?.forEach { it.component.isVisible = true }
    }
    languageProperty.set(languages.first())
  }

  override var panel: DialogPanel = panel {
    nameAndPath()
    row {
      row(UIBundle.message("label.project.wizard.new.project.language")) {
        buttonSelector(languages, languageProperty) { it }
      }
    }

    settingsMap.values.forEach {
      it.forEach { lc ->
        row {
          lc.label?.let {
            twoColumnRow(
              { component(it) },
              { component(lc.component) }
            )
          } ?: row { component(lc.component) }
        }.apply { visible = false }
      }
    }
    gitCheckbox()
  }.withBorder(JBUI.Borders.empty(10, 0))

  class NewProjectWizardWithSettings<T>(wizard: NewProjectWizard<T>) : NewProjectWizard<T> by wizard {
    var settings : T = settingsFactory.invoke()

    fun settingsList() = settingsList(settings)
    fun setupProject(project: Project?) = setupProject(project, settings)
  }
}

class NewProjectStepSettings {
  var language: String = "Java"
}