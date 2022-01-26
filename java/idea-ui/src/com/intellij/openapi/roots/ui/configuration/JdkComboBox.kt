// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration

import com.intellij.CommonBundle
import com.intellij.ide.JavaUiBundle
import com.intellij.ide.starters.JavaStartersBundle
import com.intellij.ide.util.PropertiesComponent
import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.util.projectWizard.ProjectWizardUtil
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts.DialogMessage
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.layout.*
import com.intellij.ui.layout.Row as RowV1


@Deprecated("Please, migrate on Kotlin UI DSL Version 2")
fun RowV1.sdkComboBox(
  sdkModel: ProjectSdksModel,
  sdkProperty: GraphProperty<Sdk?>,
  project: Project?,
  moduleBuilder: ModuleBuilder
): CellBuilder<JdkComboBox> {
  return component(createSdkComboBox(project, sdkModel, sdkProperty, StdModuleTypes.JAVA.id, moduleBuilder::isSuitableSdkType))
}

@Deprecated("Please, recompile code", level = DeprecationLevel.HIDDEN)
fun Row.sdkComboBox(
  context: WizardContext,
  sdkProperty: GraphProperty<Sdk?>,
  sdkPropertyId: String,
  sdkTypeFilter: ((SdkTypeId) -> Boolean)? = null,
  sdkFilter: ((Sdk) -> Boolean)? = null,
  suggestedSdkItemFilter: ((SdkListItem.SuggestedItem) -> Boolean)? = null,
  creationSdkTypeFilter: ((SdkTypeId) -> Boolean)? = null,
  onNewSdkAdded: ((Sdk) -> Unit)? = null
) = sdkComboBox(
  context, sdkProperty as ObservableMutableProperty<Sdk?>, sdkPropertyId,
  sdkTypeFilter, sdkFilter, suggestedSdkItemFilter, creationSdkTypeFilter, onNewSdkAdded
)

fun Row.sdkComboBox(
  context: WizardContext,
  sdkProperty: ObservableMutableProperty<Sdk?>,
  sdkPropertyId: String,
  sdkTypeFilter: ((SdkTypeId) -> Boolean)? = null,
  sdkFilter: ((Sdk) -> Boolean)? = null,
  suggestedSdkItemFilter: ((SdkListItem.SuggestedItem) -> Boolean)? = null,
  creationSdkTypeFilter: ((SdkTypeId) -> Boolean)? = null,
  onNewSdkAdded: ((Sdk) -> Unit)? = null
): Cell<JdkComboBox> {
  val sdksModel = ProjectSdksModel()

  Disposer.register(context.disposable, Disposable {
    sdksModel.disposeUIResources()
  })

  val comboBox = createSdkComboBox(
    context.project, sdksModel, sdkProperty, sdkPropertyId,
    sdkTypeFilter, sdkFilter, suggestedSdkItemFilter, creationSdkTypeFilter, onNewSdkAdded
  )

  return cell(comboBox)
    .validationOnApply { validateSdk(sdkProperty, sdksModel) }
    .onApply { context.projectJdk = sdkProperty.get() }
}

fun createSdkComboBox(
  project: Project?,
  sdkModel: ProjectSdksModel,
  sdkProperty: ObservableMutableProperty<Sdk?>,
  sdkPropertyId: String,
  sdkTypeFilter: ((SdkTypeId) -> Boolean)? = null,
  sdkFilter: ((Sdk) -> Boolean)? = null,
  suggestedSdkItemFilter: ((SdkListItem.SuggestedItem) -> Boolean)? = null,
  creationSdkTypeFilter: ((SdkTypeId) -> Boolean)? = null,
  onNewSdkAdded: ((Sdk) -> Unit)? = null
): JdkComboBox {

  sdkModel.reset(project)

  val sdkComboBox = JdkComboBox(project, sdkModel, sdkTypeFilter, sdkFilter, suggestedSdkItemFilter, creationSdkTypeFilter, onNewSdkAdded)

  val selectedJdkProperty = "jdk.selected.$sdkPropertyId"
  val stateComponent = if (project == null) PropertiesComponent.getInstance() else PropertiesComponent.getInstance(project)

  sdkComboBox.addActionListener {
    val jdk: Sdk? = getTargetJdk(sdkComboBox, project)
    if (jdk != null) {
      stateComponent.setValue(selectedJdkProperty, jdk.name)
    }
    sdkProperty.set(jdk)
  }

  val lastUsedSdk = stateComponent.getValue(selectedJdkProperty)
  ProjectWizardUtil.preselectJdkForNewModule(project, lastUsedSdk, sdkComboBox, sdkTypeFilter ?: { true })

  return sdkComboBox
}

private fun getTargetJdk(sdkComboBox: JdkComboBox, project: Project?): Sdk? {
  val selectedJdk = sdkComboBox.selectedJdk
  if (selectedJdk != null) return selectedJdk

  if (project != null && sdkComboBox.isProjectJdkSelected) {
    return ProjectRootManager.getInstance(project).projectSdk
  }

  return null
}

fun ValidationInfoBuilder.validateSdk(sdkProperty: ObservableProperty<Sdk?>, sdkModel: ProjectSdksModel): ValidationInfo? {
  return validateAndGetSdkValidationMessage(sdkProperty, sdkModel)?.let { error(it) }
}

fun validateSdk(sdkProperty: ObservableProperty<Sdk?>, sdkModel: ProjectSdksModel): Boolean {
  return validateAndGetSdkValidationMessage(sdkProperty, sdkModel) == null
}

private fun validateAndGetSdkValidationMessage(sdkProperty: ObservableProperty<Sdk?>, sdkModel: ProjectSdksModel): @DialogMessage String? {
  if (sdkProperty.get() == null) {
    if (Messages.showDialog(JavaUiBundle.message("prompt.confirm.project.no.jdk"),
                            JavaUiBundle.message("title.no.jdk.specified"),
                            arrayOf(CommonBundle.getYesButtonText(), CommonBundle.getNoButtonText()), 1,
                            Messages.getWarningIcon()) != Messages.YES) {
      return JavaUiBundle.message("title.no.jdk.specified")
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
      return e.message ?: e.title
    }
  }
  return null
}

fun validateJavaVersion(sdkProperty: ObservableProperty<Sdk?>, javaVersion: String?, technologyName: String? = null): Boolean {
  val sdk = sdkProperty.get()
  if (sdk != null) {
    val wizardVersion = JavaSdk.getInstance().getVersion(sdk)
    if (wizardVersion != null && javaVersion != null) {
      val selectedVersion = JavaSdkVersion.fromVersionString(javaVersion)
      if (selectedVersion != null && !wizardVersion.isAtLeast(selectedVersion)) {
        val message = if (technologyName == null) {
          JavaStartersBundle.message("message.java.version.not.supported.by.sdk",
            selectedVersion.description,
            sdk.name)
        }
        else {
          JavaStartersBundle.message("message.java.version.not.supported.by.sdk.for.technology",
            technologyName,
            selectedVersion.description,
            sdk.name,
            wizardVersion.description)
        }

        Messages.showErrorDialog(message, JavaStartersBundle.message("message.title.error"))

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