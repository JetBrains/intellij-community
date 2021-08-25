// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.generators

import com.intellij.ide.JavaUiBundle
import com.intellij.ide.NewProjectWizard
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardStepSettings
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ui.configuration.JdkComboBox
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.Key
import com.intellij.ui.layout.*
import java.awt.Dimension
import java.awt.event.ItemListener

class JavaNewProjectWizard : NewProjectWizard {
  override val language: String = "Java"

  override fun createStep(context: WizardContext) = Step(context)

  class Step(private val context: WizardContext) : NewProjectWizardStep<Settings> {
    override val settings = Settings(context)

    override fun setupUI(builder: RowBuilder) = with(builder) {
      val steps = JavaBuildSystemType.EP_NAME.extensionList
        .map { BuildSystemStep(it.name, it.createStep(context)) }

      row(JavaUiBundle.message("label.project.wizard.new.project.build.system")) {
        buttonSelector(steps, settings.buildSystemProperty) { it.name }
      }
      row(JavaUiBundle.message("label.project.wizard.new.project.jdk")) {
        val sdkModel = ProjectSdksModel()
          .also { it.syncSdks() }
        val sdkCombo = JdkComboBox(null, sdkModel, { it is JavaSdkType }, null, null, null)
          .apply { minimumSize = Dimension(0, 0) }
          .also { combo -> combo.addItemListener(ItemListener { settings.sdk = combo.selectedJdk }) }
          .also { combo ->
            val defaultProject = ProjectManager.getInstance().defaultProject
            val defaultProjectSdk = ProjectRootManager.getInstance(defaultProject).projectSdk
            if (defaultProjectSdk != null && defaultProjectSdk.sdkType is JavaSdkType) {
              combo.selectedJdk = defaultProjectSdk
            }
          }
        sdkCombo()
      }

      val stepsControllers = HashMap<String, DialogPanel>()
      for (step in steps) {
        stepsControllers[step.name] = nestedPanel {
          step.setupUI(this)
        }.component
      }
      settings.buildSystemProperty.afterChange {
        stepsControllers.values.forEach { it.isVisible = false }
        stepsControllers[settings.buildSystem.name]?.isVisible = true
      }
      settings.buildSystem = steps.first()
    }

    override fun setupProject(project: Project) {
      settings.sdk?.let { sdk ->
        val table = ProjectJdkTable.getInstance()
        runWriteAction {
          if (table.findJdk(sdk.name) == null) {
            table.addJdk(sdk)
          }
        }
      }

      context.projectJdk = settings.sdk
      settings.buildSystem.setupProject(project)
    }
  }

  class BuildSystemStep<S : NewProjectWizardStepSettings<S>>(
    val name: String,
    step: NewProjectWizardStep<S>
  ) : NewProjectWizardStep<S> by step

  class Settings(context: WizardContext) : NewProjectWizardStepSettings<Settings>(KEY, context) {
    val buildSystemProperty = propertyGraph.graphProperty<BuildSystemStep<*>> { throw UninitializedPropertyAccessException() }

    var buildSystem by buildSystemProperty
    var sdk: Sdk? = null

    companion object {
      val KEY = Key.create<Settings>(Settings::class.java.name)
    }
  }
}
