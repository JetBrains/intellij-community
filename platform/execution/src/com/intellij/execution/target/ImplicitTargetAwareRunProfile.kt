// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.target

import com.intellij.execution.configurations.RunProfile

/**
 * Implement if Targets API is used for execution, but implementing [com.intellij.execution.target.TargetEnvironmentAwareRunProfile]
 * is not suitable to avoid common target UI (like `Run on` combobox).
 * 
 * Implementing this interface will include target ID into collected usage statistics.
 */
interface ImplicitTargetAwareRunProfile : RunProfile {
  val targetType: TargetEnvironmentType<*>?
}