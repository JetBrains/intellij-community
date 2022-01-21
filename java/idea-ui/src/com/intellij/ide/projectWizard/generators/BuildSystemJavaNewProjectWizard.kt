// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.generators

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
  }
}
