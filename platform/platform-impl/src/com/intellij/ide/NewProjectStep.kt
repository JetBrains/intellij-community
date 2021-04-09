// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide

import com.intellij.ide.wizard.LabelAndComponent
import com.intellij.ide.wizard.LanguageButton
import com.intellij.ide.wizard.NewProjectWizard
import com.intellij.ide.wizard.NewProjectWizard.Companion.EP_WIZARD
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.ContextHelpLabel
import com.intellij.ui.UIBundle
import com.intellij.ui.layout.*
import com.intellij.util.ui.JBUI
import java.awt.FlowLayout
import javax.swing.JPanel

class NewProjectStep : NewModuleStep<NewProjectStepSettings>() {
  private val settingsMap = mutableMapOf<String, List<LabelAndComponent>>()
  val wizards: List<NewProjectWizardWithSettings<out Any?>> = EP_WIZARD.extensions.filter { it.enabled() }.map { NewProjectWizardWithSettings(it) }
    .onEach { settingsMap[it.language] = it.settingsList() }

  override var settings = NewProjectStepSettings()

  private var languageButtons : List<LanguageButton> = wizards.map {
    val languageSettings = settings::language
    object : LanguageButton(it.language, languageSettings) {
      override fun setSelected(e: AnActionEvent, state: Boolean) {
        super.setSelected(e, state)
        settingsMap.values.forEach { it.forEach { it.label?.isVisible = false; it.component.isVisible = false } }
        settingsMap.values.forEach { it.forEach { it.label?.repaint(); it.component.repaint() } }
        settingsMap[it.language]?.forEach { it.label?.isVisible = state; it.component.isVisible = state }
      }
    }
  }

  override var panel: DialogPanel = panel {
    nameAndPath()
    row {
      twoColumnRow(
        { label(UIBundle.message("label.project.wizard.new.project.language")) },
        { component(createButtonsPanel(DefaultActionGroup(languageButtons))) }
      )
    }

    settingsMap.values.forEach {
      it.forEach { lc ->
        row {
          (lc.label?.let {
            twoColumnRow(
              { component(it) },
              { component(lc.component) }
            )
          } ?: component(lc.component)
          )
        }.apply { visible = false }
      }
    }
    gitCheckbox()
  }.withBorder(JBUI.Borders.empty(10, 10))
    .also {
      languageButtons.first().setSelected(true)
    }

  class NewProjectWizardWithSettings<T>(wizard: NewProjectWizard<T>) : NewProjectWizard<T> by wizard {
    var settings : T = settingsFactory.invoke()

    fun settingsList() = settingsList(settings)
    fun setupProject(project: Project?) = setupProject(project, settings)
  }

  companion object {
    fun createButtonsPanel(languageGroup: DefaultActionGroup) = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
      add(ActionManager.getInstance().createActionToolbar(NewProjectWizard.PLACE, languageGroup, true).component)
      add(ContextHelpLabel.create(IdeBundle.message("label.project.wizard.new.project.language.context.help")))
    }
  }
}

class NewProjectStepSettings {
  var language: String = "Java"
}