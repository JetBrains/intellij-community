// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target

import com.intellij.openapi.project.Project

abstract class TargetWizardModel {
  abstract val project: Project

  abstract val subject: TargetEnvironmentConfiguration

  abstract val languageConfigForIntrospection: LanguageRuntimeConfiguration?

  /**
   * Applies changes to the [subject].
   *
   * It should not schedule any tasks or save configs that might duplicate since that method might be called multiple times.
   * See [commit].
   */
  open fun applyChanges() = Unit

  /**
   * Prepares the target environment before custom tool creation.
   *
   * The implementation of this function should be idempotent, since it might be called twice.
   * Use this to persist configurations and perform synchronous operations (e.g., file upload)
   * that must complete before any remote commands can be executed.
   */
  open fun prepareTarget() = Unit

  /**
   * Applies final changes to the [subject] on finish action of the Wizard.
   *
   * That method should be called once.
   * Do not make it open since it ensures that [applyChanges] called before [doCommit].
   * Override [doCommit] for saving extra configs and scheduling tasks.
   */
  fun commit() {
    applyChanges()
    prepareTarget()
    doCommit()
  }

  /**
   * Performs saving extra configs and scheduling task.
   *
   * That method is designed to be called only once by [commit] just after [prepareTarget].
   */
  protected open fun doCommit() = Unit
}
