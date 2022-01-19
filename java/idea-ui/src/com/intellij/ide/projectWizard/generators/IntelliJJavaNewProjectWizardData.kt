// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.generators

import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.util.Key

interface IntelliJJavaNewProjectWizardData: IntelliJNewProjectWizardData {
  companion object {
    @JvmStatic val KEY = Key.create<IntelliJJavaNewProjectWizardData>(IntelliJJavaNewProjectWizardData::class.java.name)

    @JvmStatic val NewProjectWizardStep.javaData get() = data.getUserData(KEY)!!

    @JvmStatic var NewProjectWizardStep.sdk get() = javaData.sdk; set(it) { javaData.sdk = it }
    @JvmStatic var NewProjectWizardStep.moduleName get() = javaData.moduleName; set(it) { javaData.moduleName = it }
    @JvmStatic var NewProjectWizardStep.contentRoot get() = javaData.contentRoot; set(it) { javaData.contentRoot = it }
    @JvmStatic var NewProjectWizardStep.moduleFileLocation get() = javaData.moduleFileLocation; set(it) { javaData.moduleFileLocation = it }
    @JvmStatic var NewProjectWizardStep.addSampleCode get() = javaData.addSampleCode; set(it) { javaData.addSampleCode = it }
  }
}