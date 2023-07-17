// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.newUiOnboarding

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.util.KeyedLazyInstanceEP
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface NewUiOnboardingStep {
  suspend fun performStep(project: Project, disposable: CheckedDisposable): NewUiOnboardingStepData?

  fun isAvailable(): Boolean = true

  companion object {
    val EP_NAME: ExtensionPointName<KeyedLazyInstanceEP<NewUiOnboardingStep>> = ExtensionPointName.create("com.intellij.ide.newUiOnboarding.step")
  }
}