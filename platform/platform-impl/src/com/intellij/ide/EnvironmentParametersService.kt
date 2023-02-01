// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * Allows retrieving values for environment keys.
 *
 * For a long time, the IDE resolved ambiguities and uncertainties by direct request of user action via a modal dialog.
 * Since it is impossible to ask the user in a headless application,
 * the environment should contain the answers to all possible cases where normally user action would be required.
 */
@ApiStatus.Experimental
interface EnvironmentParametersService {

  /**
   * Retrieves a value from environment.
   *
   * @return If a value for [key] is defined in environment, then returns this value.
   * If a value for [key] is not defined, and it can be tolerated, then returns `null`.
   * If a value for [key] is not defined, and it cannot be tolerated, then the behavior is implementation-defined.
   * Most likely it will throw a [kotlinx.coroutines.CancellationException]
   */
  suspend fun getEnvironmentValue(project: Project?, key: EnvironmentKey): String?


}