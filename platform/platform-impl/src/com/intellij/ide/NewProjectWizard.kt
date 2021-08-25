// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide

import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.NlsContexts

interface NewProjectWizard : NewProjectWizardStep.Factory {
  val language: @NlsContexts.Label String

  fun enabled(): Boolean = true

  companion object {
    val EP_NAME = ExtensionPointName<NewProjectWizard>("com.intellij.newProjectWizard")
  }
}
