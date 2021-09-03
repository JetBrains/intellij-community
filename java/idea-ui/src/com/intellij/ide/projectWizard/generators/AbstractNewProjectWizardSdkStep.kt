// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.generators

import com.intellij.ide.JavaUiBundle
import com.intellij.ide.wizard.AbstractNewProjectWizardChildStep
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.roots.ui.configuration.JdkComboBox
import com.intellij.openapi.roots.ui.configuration.createSdkComboBox
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.roots.ui.configuration.validateSdk
import com.intellij.openapi.util.Disposer
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign

abstract class AbstractNewProjectWizardSdkStep(
  parent: NewProjectWizardStep
) : AbstractNewProjectWizardChildStep<NewProjectWizardStep>(parent), NewProjectWizardSdkData {

  final override lateinit var sdkComboBox: Cell<JdkComboBox>

  final override val sdkProperty = propertyGraph.graphProperty<Sdk?> { null }
  final override val sdk by sdkProperty

  private val sdksModel = ProjectSdksModel()

  protected abstract fun sdkTypeFilter(type: SdkTypeId): Boolean

  override fun setupUI(builder: Panel) {
    with(builder) {
      row(JavaUiBundle.message("label.project.wizard.new.project.jdk")) {
        sdkComboBox = cell(createSdkComboBox(sdksModel, sdkProperty, context.project, ::sdkTypeFilter))
          .horizontalAlign(HorizontalAlign.FILL)
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