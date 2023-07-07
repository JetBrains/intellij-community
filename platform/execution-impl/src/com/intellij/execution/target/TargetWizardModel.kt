// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target

import com.intellij.openapi.project.Project

interface TargetWizardModel {
  val project: Project

  val subject: TargetEnvironmentConfiguration

  val languageConfigForIntrospection: LanguageRuntimeConfiguration?

  /**
   * Applies changes to the [subject].
   *
   * It should not schedule any tasks or save configs that might duplicate since that method might be called multiple times.
   * See [commit].
   */
  fun applyChanges()

  /**
   * Applies final changes to the [subject] on finish action of the Wizard.
   *
   * That method should be called once and can be used for saving extra configs and scheduling tasks.
   */
  fun commit()
}