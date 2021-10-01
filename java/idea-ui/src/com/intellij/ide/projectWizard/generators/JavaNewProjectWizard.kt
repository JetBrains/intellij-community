// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.generators

import com.intellij.ide.JavaUiBundle
import com.intellij.ide.LabelAndComponent
import com.intellij.ide.NewProjectWizard
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.BuildSystemWithSettings
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
    var component: JComponent = JBLabel()
    panel {
      row {
        component = buttonSelector(settings.buildSystemButtons.value, settings.buildSystemProperty) { it.name }.component
      }
    }

    settings.propertyGraph.afterPropagation {
      settings.buildSystemButtons.value.forEach { it.advancedSettings().apply { isVisible = false } }
      settings.buildSystemProperty.get().advancedSettings().apply { isVisible = true }
    }

    val sdkCombo = JdkComboBox(null, ProjectSdksModel(), null, null, null, null)
      .apply { minimumSize = Dimension(0, 0) }
      .also { combo -> combo.addItemListener(ItemListener { settings.sdk = combo.selectedJdk }) }

    settings.buildSystemProperty.set(settings.buildSystemButtons.value.first())

    return listOf(
      LabelAndComponent(JBLabel(JavaUiBundle.message("label.project.wizard.new.project.build.system")), component),
      LabelAndComponent(JBLabel(JavaUiBundle.message("label.project.wizard.new.project.jdk")), sdkCombo)
    ).plus(settings.buildSystemButtons.value.map { LabelAndComponent(component = it.advancedSettings()) })
  }

  override fun setupProject(project: Project, settings: JavaSettings, context: WizardContext) {
    settings.buildSystemProperty.get().setupProject(project, settings)
  }
}

open class JavaBuildSystemWithSettings<P>(val buildSystemType: JavaBuildSystemType<P>) :
  BuildSystemWithSettings<JavaSettings, P>(buildSystemType)

class JavaSettings {
  var sdk: Sdk? = null
  val propertyGraph: PropertyGraph = PropertyGraph()
  val buildSystemButtons: Lazy<List<JavaBuildSystemWithSettings<out Any?>>> = lazy {
    JavaBuildSystemType.EP_NAME.extensionList.map {
      JavaBuildSystemWithSettings(it)
    }
  }

  val buildSystemProperty: GraphProperty<JavaBuildSystemWithSettings<*>> = propertyGraph.graphProperty {
    buildSystemButtons.value.first()
  }
}