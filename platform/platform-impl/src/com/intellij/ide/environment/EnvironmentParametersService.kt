// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.environment

import org.jetbrains.annotations.ApiStatus

/**
 * Allows retrieving values for [EnvironmentKey].
 *
 * For a long time, the IDE resolved ambiguities and uncertainties by direct request of user action via a modal dialog.
 * Since it is impossible to ask the user in a headless application,
 * the environment should contain the answers to all possible cases where normally a user action would be required.
 */
@ApiStatus.Experimental
interface EnvironmentParametersService {

  /**
   * Retrieves a value from environment or `null` if value for [key] is not defined.
   */
  suspend fun getEnvironmentValue(key: EnvironmentKey): String?

}