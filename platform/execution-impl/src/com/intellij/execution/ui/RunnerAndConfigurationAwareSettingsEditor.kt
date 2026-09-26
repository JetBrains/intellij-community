// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ui

import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl
import org.jetbrains.annotations.ApiStatus

/**
 * Extracted from [RunConfigurationFragmentedEditor] to allow for delegation
 */
@ApiStatus.Internal
interface RunnerAndConfigurationAwareSettingsEditor {
  fun resetEditorFrom(s: RunnerAndConfigurationSettingsImpl)

  fun applyEditorTo(s: RunnerAndConfigurationSettingsImpl)

  fun targetChanged(targetName: String?)

  fun isInplaceValidationSupported(): Boolean
}
