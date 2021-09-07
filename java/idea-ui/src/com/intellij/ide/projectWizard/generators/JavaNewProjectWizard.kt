// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.generators

import com.intellij.ide.JavaUiBundle
import com.intellij.ide.wizard.*
import com.intellij.openapi.module.StdModuleTypes
import com.intellij.openapi.observable.properties.GraphPropertyImpl.Companion.graphProperty
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.projectRoots.impl.DependentSdkType
import com.intellij.openapi.roots.ui.configuration.JdkComboBox
import com.intellij.openapi.roots.ui.configuration.sdkComboBox
import com.intellij.ui.dsl.builder.COLUMNS_MEDIUM
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.columns

class JavaNewProjectWizard : NewProjectWizard {
  override val name: String = "Java"

  override fun createStep(parent: NewProjectWizardLanguageStep) = Step(parent)

  class Step(parent: NewProjectWizardLanguageStep)
    : AbstractNewProjectWizardMultiStep<NewProjectWizardLanguageStep, Step>(parent, JavaBuildSystemType.EP_NAME),
      NewProjectWizardLanguageData by parent,
      NewProjectWizardBuildSystemData, NewProjectWizardSdkData {

    override val self = this

    override val label = JavaUiBundle.message("label.project.wizard.new.project.build.system")

    override val buildSystemProperty by ::stepProperty
    override val buildSystem by ::step

    override lateinit var sdkComboBox: Cell<JdkComboBox>
    override val sdkProperty = propertyGraph.graphProperty<Sdk?> { null }
    override val sdk by sdkProperty

    override fun setupCommonUI(builder: Panel) {
      with(builder) {
        row(JavaUiBundle.message("label.project.wizard.new.project.jdk")) {
          val sdkTypeFilter = { it: SdkTypeId -> it is JavaSdkType && it !is DependentSdkType }
          sdkComboBox = sdkComboBox(context, sdkProperty, StdModuleTypes.JAVA.id, sdkTypeFilter)
            .columns(COLUMNS_MEDIUM)
        }
      }
    }
  }
}
