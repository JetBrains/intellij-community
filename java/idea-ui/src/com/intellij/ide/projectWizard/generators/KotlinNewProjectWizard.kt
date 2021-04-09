// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.generators

import com.intellij.ide.JavaUiBundle
import com.intellij.ide.wizard.BuildSystemButton
import com.intellij.ide.wizard.BuildSystemType.Companion.EP_BUILD_SYSTEM
import com.intellij.ide.wizard.LabelAndComponent
import com.intellij.ide.wizard.NewProjectWizard
import com.intellij.ide.wizard.NewProjectWizard.Companion.PLACE
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.configuration.JdkComboBox
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.Nls
import javax.swing.Icon

class KotlinNewProjectWizard : NewProjectWizard<KotlinSettings> {
  override val language: String = "Kotlin"
  override var settingsFactory = { KotlinSettings() }

  private fun getProjectTemplates() = listOf(
    NewProjectTemplate("Console application"),
    NewProjectTemplate("Frontend"),
    NewProjectTemplate("Full-stack web"),
    NewProjectTemplate("Multiplatform"),
    NewProjectTemplate("Multiplatform mobile"),
    NewProjectTemplate("Native"))

  override fun settingsList(settings: KotlinSettings): List<LabelAndComponent> {
    val templateList = JBList(getProjectTemplates()).apply {
      cellRenderer = SimpleListCellRenderer.create { label, value, _ -> label.text = value.name }
      border = JBUI.Borders.customLine(JBColor.border())
      addListSelectionListener { settings.template = selectedValue }
    }

    val buildSystemButtons = EP_BUILD_SYSTEM.extensions.map { BuildSystemButton(it, settings::buildSystemSettings) }
    buildSystemButtons.first().setSelected(true)

    return listOf(
      LabelAndComponent(JBLabel(JavaUiBundle.message("label.project.wizard.new.project.templates")), templateList),
      LabelAndComponent(JBLabel(JavaUiBundle.message("label.project.wizard.new.project.build.system")),
                        ActionManager.getInstance().createActionToolbar(PLACE, DefaultActionGroup(buildSystemButtons.toList()), true).component),
      LabelAndComponent(JBLabel(JavaUiBundle.message("label.project.wizard.new.project.jdk")), JdkComboBox(null, ProjectSdksModel(), null, null, null, null))
    )
  }

  override fun setupProject(project: Project?, settings: KotlinSettings) {
    settings
  }
}

class NewProjectTemplate(@Nls val name: String, val icon: Icon? = null)

class KotlinSettings {
  var buildSystemSettings = "Gradle"
  var template = NewProjectTemplate("Console application")
}