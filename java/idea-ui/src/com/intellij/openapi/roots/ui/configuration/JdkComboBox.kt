// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration

import com.intellij.CommonBundle
import com.intellij.ide.JavaUiBundle
import com.intellij.ide.starters.JavaStartersBundle
import com.intellij.ide.util.PropertiesComponent
import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.util.projectWizard.ProjectWizardUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.ui.Messages
import com.intellij.ui.layout.*

fun Row.sdkComboBox(sdkModel: ProjectSdksModel, sdkProperty: GraphProperty<Sdk?>,
                    project: Project?, moduleBuilder: ModuleBuilder): CellBuilder<JdkComboBox> {
  sdkModel.reset(project)

  val sdkFilter = moduleBuilder::isSuitableSdkType
  val sdkComboBox = JdkComboBox(project, sdkModel, sdkFilter, JdkComboBox.getSdkFilter(sdkFilter), sdkFilter, null)
  val moduleType: ModuleType<*> = StdModuleTypes.JAVA

  val selectedJdkProperty = "jdk.selected." + moduleType.id
  val stateComponent = if (project == null) PropertiesComponent.getInstance() else PropertiesComponent.getInstance(project)

  sdkComboBox.addActionListener {
    val jdk: Sdk? = getTargetJdk(sdkComboBox, project)
    if (jdk != null) {
      stateComponent.setValue(selectedJdkProperty, jdk.name)
    }
    sdkProperty.set(jdk)
  }

  val lastUsedSdk = stateComponent.getValue(selectedJdkProperty)
  ProjectWizardUtil.preselectJdkForNewModule(project, lastUsedSdk, sdkComboBox, moduleBuilder, sdkFilter)

  return this.component(sdkComboBox)
}

private fun getTargetJdk(sdkComboBox: JdkComboBox, project: Project?): Sdk? {
  val selectedJdk = sdkComboBox.selectedJdk
  if (selectedJdk != null) return selectedJdk

  if (project != null && sdkComboBox.isProjectJdkSelected) {
    return ProjectRootManager.getInstance(project).projectSdk
  }

  return null
}

fun validateSdk(sdkProperty: GraphProperty<Sdk?>, sdkModel: ProjectSdksModel): Boolean {
  if (sdkProperty.get() == null) {
    if (Messages.showDialog(JavaUiBundle.message("prompt.confirm.project.no.jdk"),
                            JavaUiBundle.message("title.no.jdk.specified"),
                            arrayOf(CommonBundle.getYesButtonText(), CommonBundle.getNoButtonText()), 1,
                            Messages.getWarningIcon()) != Messages.YES) {
      return false
    }
  }

  try {
    sdkModel.apply(null, true)
  }
  catch (e: ConfigurationException) {
    //IDEA-98382 We should allow Next step if user has wrong SDK
    if (Messages.showDialog(JavaUiBundle.message("dialog.message.0.do.you.want.to.proceed", e.message),
                            e.title, arrayOf(CommonBundle.getYesButtonText(), CommonBundle.getNoButtonText()), 1,
                            Messages.getWarningIcon()) != Messages.YES) {
      return false
    }
  }

  return true
}

fun validateJavaVersion(sdkProperty: GraphProperty<Sdk?>, javaVersion: String?): Boolean {
  val sdk = sdkProperty.get()
  if (sdk != null) {
    val wizardVersion = JavaSdk.getInstance().getVersion(sdk)
    if (wizardVersion != null && javaVersion != null) {
      val selectedVersion = JavaSdkVersion.fromVersionString(javaVersion)
      if (selectedVersion != null && !wizardVersion.isAtLeast(selectedVersion)) {
        Messages.showErrorDialog(JavaStartersBundle.message("message.java.version.not.supported.by.sdk",
                                                            selectedVersion.description,
                                                            sdk.name,
                                                            wizardVersion.description),
                                 JavaStartersBundle.message("message.title.error"))
        return false
      }
    }
  }

  return true
}

fun setupNewModuleJdk(modifiableRootModel: ModifiableRootModel, selectedJdk: Sdk?, isCreatingNewProject: Boolean): Sdk? {
  if (ApplicationManager.getApplication().isUnitTestMode && selectedJdk == modifiableRootModel.sdk) {
    // do not change SDK in tests
    return selectedJdk
  }

  val sdk = selectedJdk ?: getProjectJdk(modifiableRootModel.project)
  if (sdk != null) {
    if (isCreatingNewProject || (!isCreatingNewProject && sdk == getProjectJdk(modifiableRootModel.project))) {
      modifiableRootModel.inheritSdk()
    }
    else {
      modifiableRootModel.sdk = sdk
    }
  }
  return sdk
}

private fun getProjectJdk(project: Project): Sdk? {
  return ProjectRootManager.getInstance(project).projectSdk
}