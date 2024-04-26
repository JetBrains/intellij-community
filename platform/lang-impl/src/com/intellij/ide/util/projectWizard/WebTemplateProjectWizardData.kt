// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.projectWizard

import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NotNullLazyValue
import com.intellij.platform.ProjectGeneratorPeer

interface WebTemplateProjectWizardData<T> {

  val peer: NotNullLazyValue<ProjectGeneratorPeer<T>>

  companion object {

    val KEY: Key<WebTemplateProjectWizardData<*>> = Key.create(WebTemplateProjectWizardData::class.java.name)

    @JvmStatic
    val NewProjectWizardStep.webTemplateData: WebTemplateProjectWizardData<*>?
      get() = data.getUserData(KEY)
  }
}