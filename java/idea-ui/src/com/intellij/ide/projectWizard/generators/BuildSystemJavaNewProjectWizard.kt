// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.generators

import com.intellij.ide.projectWizard.NewProjectWizardCollector
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.ide.wizard.NewProjectWizardMultiStepFactory
import com.intellij.openapi.extensions.ExtensionPointName

/**
 * Describes java build system step in new project wizard.
 *
 * @see NewProjectWizardMultiStepFactory
 */
interface BuildSystemJavaNewProjectWizard : NewProjectWizardMultiStepFactory<JavaNewProjectWizard.Step> {
  companion object {
    var EP_NAME = ExtensionPointName<BuildSystemJavaNewProjectWizard>("com.intellij.newProjectWizard.java.buildSystem")

    private val collector = NewProjectWizardCollector.BuildSystemCollector(EP_NAME.extensions.map { it.name })

    @JvmStatic
    fun logBuildSystemChanged(context: WizardContext, name: String) = collector.logBuildSystemChanged(context, name)
  }
}
