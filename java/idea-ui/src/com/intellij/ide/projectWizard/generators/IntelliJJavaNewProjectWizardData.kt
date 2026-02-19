// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.generators

import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.util.Key

interface IntelliJJavaNewProjectWizardData: IntelliJNewProjectWizardData {

  companion object {

    val KEY = Key.create<IntelliJJavaNewProjectWizardData>(IntelliJJavaNewProjectWizardData::class.java.name)

    @JvmStatic
    val NewProjectWizardStep.javaData: IntelliJJavaNewProjectWizardData?
      get() = data.getUserData(KEY)
  }
}