// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.generators

import com.intellij.ide.wizard.NewProjectWizardMultiStepFactory
import com.intellij.openapi.extensions.ExtensionPointName

interface JavaBuildSystemType : NewProjectWizardMultiStepFactory {
  companion object {
    var EP_NAME = ExtensionPointName<JavaBuildSystemType>("com.intellij.newProjectWizard.buildSystem.java")
  }
}
