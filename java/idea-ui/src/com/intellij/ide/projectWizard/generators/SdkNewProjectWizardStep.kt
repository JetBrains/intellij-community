// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.generators

import com.intellij.ide.JavaUiBundle
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardStepSettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.roots.ui.configuration.JdkComboBox
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.roots.ui.configuration.sdkComboBox
import com.intellij.openapi.roots.ui.configuration.validateSdk
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.ui.layout.*

abstract class SdkNewProjectWizardStep<S : SdkNewProjectWizardStep.Settings<S>>(private val context: WizardContext) : NewProjectWizardStep<S> {

  lateinit var sdkComboBox: CellBuilder<JdkComboBox>

  protected abstract fun sdkTypeFilter(type: SdkTypeId): Boolean

  override fun setupUI(builder: RowBuilder) {
    with(builder) {
      row(JavaUiBundle.message("label.project.wizard.new.project.jdk")) {
        sdkComboBox = sdkComboBox(settings.sdksModel, settings.sdkProperty, context.project, ::sdkTypeFilter)
          .withValidationOnApply { validateSdk(settings.sdkProperty, settings.sdksModel) }
          .onApply { context.projectJdk = settings.sdk }
      }
    }
  }

  /**
   * Project sdk setups inside [validateSdk].
   * @see validateSdk
   * @see ProjectSdksModel.apply
   */
  override fun setupProject(project: Project) {}

  abstract class Settings<S : Settings<S>>(key: Key<S>, context: WizardContext) : NewProjectWizardStepSettings<S>(key, context) {
    val sdkProperty = propertyGraph.graphProperty<Sdk?> { null }
    val sdk by sdkProperty
    val sdksModel = ProjectSdksModel()

    init {
      val parentDisposable = context.disposable
      if (parentDisposable != null) {
        Disposer.register(parentDisposable, Disposable {
          sdksModel.disposeUIResources()
        })
      }
    }
  }
}