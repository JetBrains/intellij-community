// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.generators

import com.intellij.ide.JavaUiBundle
import com.intellij.ide.LabelAndComponent
import com.intellij.ide.NewProjectWizard
import com.intellij.ide.wizard.*
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.configuration.JdkComboBox
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBList
import com.intellij.ui.layout.*
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.Nls
import javax.swing.Icon
import javax.swing.JComponent

class KotlinNewProjectWizard : NewProjectWizard<KotlinSettings> {
  override val language: String = "Kotlin"
  override var settingsFactory = { KotlinSettings() }

  private val propertyGraph: PropertyGraph = PropertyGraph()
  private val buildSystemProperty: GraphProperty<BuildSystemButton> = propertyGraph.graphProperty { BuildSystemButton(GradleGroovy) }

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

    val buildSystemButtons = BuildSystemType.EP_BUILD_SYSTEM.extensions
      .filter { it.name == GradleGroovy.name || it.name == Maven.name || it.name == Intellij.name }
      .map { BuildSystemButton(it) }

    var component: JComponent? = null
    panel {
      row {
        component = buttonSelector(buildSystemButtons, buildSystemProperty) {it.buildSystemType.name}.component
      }
    }

    return listOf(
      LabelAndComponent(JavaUiBundle.message("label.project.wizard.new.project.templates"), templateList),
      LabelAndComponent(JavaUiBundle.message("label.project.wizard.new.project.build.system"), component!!),
      LabelAndComponent(JavaUiBundle.message("label.project.wizard.new.project.jdk"),
                        JdkComboBox(null, ProjectSdksModel(), null, null, null, null))
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