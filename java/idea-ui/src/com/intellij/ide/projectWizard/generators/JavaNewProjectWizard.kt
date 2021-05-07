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
import com.intellij.ui.components.JBLabel
import com.intellij.ui.layout.*
import java.awt.Dimension
import javax.swing.JComponent

class JavaNewProjectWizard : NewProjectWizard<JavaSettings> {
  override val language: String = "Java"
  override var settingsFactory = { JavaSettings() }

  private val propertyGraph: PropertyGraph = PropertyGraph()
  private val buildSystemProperty: GraphProperty<BuildSystemButton> = propertyGraph.graphProperty { BuildSystemButton(GradleGroovy) }

  override fun settingsList(settings: JavaSettings): List<LabelAndComponent> {
    val buildSystemButtons = BuildSystemType.EP_BUILD_SYSTEM.extensions
      .filter { it.name == GradleGroovy.name || it.name == Maven.name || it.name == Intellij.name }
      .map { BuildSystemButton(it) }

    buildSystemProperty.set(buildSystemButtons.first())

    var component: JComponent = JBLabel()
    panel {
      row {
        component = buttonSelector(buildSystemButtons, buildSystemProperty) {it.buildSystemType.name}.component
      }
    }

    return listOf(
      LabelAndComponent(JavaUiBundle.message("label.project.wizard.new.project.build.system"), component),
      LabelAndComponent(JavaUiBundle.message("label.project.wizard.new.project.jdk"),
                        JdkComboBox(null, ProjectSdksModel(), null, null, null, null)
                          .apply { minimumSize = Dimension(0, 0) })
    )
  }

  override fun setupProject(project: Project?, settings: JavaSettings) {
    settings
  }
}

class JavaSettings {
  var version: String = "1.0"
  var buildSystemSettings: String = "Gradle Groovy"
}