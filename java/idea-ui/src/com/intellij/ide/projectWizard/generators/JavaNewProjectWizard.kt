// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.generators

import com.intellij.ide.JavaUiBundle
import com.intellij.ide.LabelAndComponent
import com.intellij.ide.NewProjectWizard
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ui.configuration.JdkComboBox
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.ui.components.JBLabel
import com.intellij.ui.layout.*
import java.awt.Dimension
import java.awt.event.ItemListener
import javax.swing.JComponent

class JavaNewProjectWizard : NewProjectWizard<JavaSettings> {
  override val language: String = "Java"
  override var settingsFactory = { JavaSettings() }

  override fun settingsList(settings: JavaSettings): List<LabelAndComponent> {
    val buildSystemButtons = JavaBuildSystemType.EP_NAME.extensionList

    var component: JComponent = JBLabel()
    panel {
      row {
        component = buttonSelector(buildSystemButtons, settings.buildSystemProperty) { it.name }.component
      }
    }

    val buildSystemAdvancedSettings: List<LabelAndComponent> =
      settings.buildSystemProperty.get().advancedSettings.onEach {
        it.component.isVisible = true
        it.label?.isVisible = true
      }

    settings.propertyGraph.afterPropagation {
      buildSystemButtons.forEach {
        it.advancedSettings.forEach {
          it.component.isVisible = false
          it.label?.isVisible = false
        }
      }
      settings.buildSystemProperty.get().advancedSettings.forEach {
        it.label?.isVisible = true
        it.component.isVisible = true
      }
    }

    val sdkCombo = JdkComboBox(null, ProjectSdksModel(), null, null, null, null)
      .apply { minimumSize = Dimension(0, 0) }.also { it.addItemListener(ItemListener { settings.sdk = it.item as Sdk? }) }

    return listOf(
      LabelAndComponent(JBLabel(JavaUiBundle.message("label.project.wizard.new.project.build.system")), component),
      LabelAndComponent(JBLabel(JavaUiBundle.message("label.project.wizard.new.project.jdk")), sdkCombo)
    ).plus(buildSystemAdvancedSettings)
  }

  override fun setupProject(project: Project?, settings: JavaSettings, context: WizardContext) {
    settings.buildSystemProperty.get().setupProject(settings)
  }
}

class JavaSettings {
  var sdk: Sdk? = null
  val propertyGraph: PropertyGraph = PropertyGraph()
  val buildSystemProperty: GraphProperty<JavaBuildSystemType> = propertyGraph.graphProperty {
    JavaBuildSystemType.EP_NAME.extensions.first()
  }
}