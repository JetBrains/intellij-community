// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.generators

import com.intellij.ide.JavaUiBundle
import com.intellij.ide.NewProjectWizard
import com.intellij.ide.projectWizard.generators.JavaNewProjectWizard.Settings
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.NewProjectWizardMultiStep
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ui.configuration.JdkComboBox
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.util.Key
import com.intellij.ui.layout.*
import java.awt.Dimension
import java.awt.event.ItemListener

class JavaNewProjectWizard : NewProjectWizard {
  override val name: String = "Java"

  override fun createStep(context: WizardContext) = Step(context)

  class Step(private val context: WizardContext) : NewProjectWizardMultiStep<Settings>(context, JavaBuildSystemType.EP_NAME) {
    override val label = JavaUiBundle.message("label.project.wizard.new.project.build.system")

    override val settings = Settings(context)

    override fun setupChildUI(builder: RowBuilder) {
      with(builder) {
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
      }
    }

    override fun setupChildProjectData(project: Project) {
      context.projectJdk = settings.sdk
      settings.sdk?.let { sdk ->
        val table = ProjectJdkTable.getInstance()
        runWriteAction {
          if (table.findJdk(sdk.name) == null) {
            table.addJdk(sdk)
          }
        }
      }
    }
  }

  class Settings(context: WizardContext) : NewProjectWizardMultiStep.Settings<Settings>(KEY, context) {
    var sdk: Sdk? = null

    companion object {
      val KEY = Key.create<Settings>(Settings::class.java.name)
    }
  }
}
