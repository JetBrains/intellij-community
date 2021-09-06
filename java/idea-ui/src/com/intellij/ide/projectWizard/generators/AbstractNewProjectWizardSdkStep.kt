// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.generators

import com.intellij.ide.JavaUiBundle
import com.intellij.ide.wizard.AbstractNewProjectWizardChildStep
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.roots.ui.configuration.JdkComboBox
import com.intellij.openapi.roots.ui.configuration.SdkListItem
import com.intellij.openapi.roots.ui.configuration.createSdkComboBox
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.roots.ui.configuration.validateSdk
import com.intellij.openapi.util.Disposer
import com.intellij.ui.dsl.builder.COLUMNS_MEDIUM
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.columns

abstract class AbstractNewProjectWizardSdkStep(
  parent: NewProjectWizardStep
) : AbstractNewProjectWizardChildStep<NewProjectWizardStep>(parent), NewProjectWizardSdkData {

  final override lateinit var sdkComboBox: Cell<JdkComboBox>

  final override val sdkProperty = propertyGraph.graphProperty<Sdk?> { null }
  final override val sdk by sdkProperty

  protected val sdksModel = ProjectSdksModel()

  protected open val sdkPropertyId: String = StdModuleTypes.JAVA.id

  protected open fun sdkTypeFilter(type: SdkTypeId): Boolean = true
  protected open fun sdkFilter(sdk: Sdk): Boolean = sdkTypeFilter(sdk.sdkType)
  protected open fun suggestedSdkItemFilter(item: SdkListItem.SuggestedItem): Boolean = true
  protected open fun creationSdkTypeFilter(type: SdkTypeId): Boolean = sdkTypeFilter(type)
  protected open fun onNewSdkAdded(sdk: Sdk) {}

  override fun setupUI(builder: Panel) {
    with(builder) {
      row(JavaUiBundle.message("label.project.wizard.new.project.jdk")) {
        val comboBox = createSdkComboBox(
          context.project,
          sdksModel,
          sdkProperty,
          sdkPropertyId,
          ::sdkTypeFilter,
          ::sdkFilter,
          ::suggestedSdkItemFilter,
          ::creationSdkTypeFilter,
          ::onNewSdkAdded
        )
        sdkComboBox = cell(comboBox)
          .columns(COLUMNS_MEDIUM)
          .validationOnApply { validateSdk(sdkProperty, sdksModel) }
          .onApply { context.projectJdk = sdk }
      }
    }
  }

  /**
   * Project sdk setups inside [validateSdk].
   * @see validateSdk
   * @see ProjectSdksModel.apply
   */
  override fun setupProject(project: Project) {}

  init {
    Disposer.register(context.disposable, Disposable {
      sdksModel.disposeUIResources()
    })
  }
}