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
interface EnvironmentService {

  /**
   * Retrieves a value for [key] from the environment.
   *
   * The semantics of returned value is the following:
   * - If the environment has some defined value for [key], then this value is returned.
   * - Otherwise the service enters in its error-handling state.
   *
   * In particular, current implementations of this service have the following behavior in error-handling state:
   * - If the environment allows the usage of UI, then this method returns `null`, and the client may use a modal dialog to get a value.
   * - If the environment does not allow the usage of UI, then [java.util.concurrent.CancellationException] is thrown.
   * *Note:* the descriptions above are for platform behavior only. In general, error-handling behavior is implementation defined,
   * but all implementations must obey the semantics of returned value stated above.
   *
   * The supposed workflow is sketched in the following snippet:
   * ```
   * suspend fun getMyAwesomeSdkPath(): String {
   *   val value: String = service<EnvironmentService>.getValue(MY_AWESOME_SDK_KEY, null)
   *   // if we are in a headless environment and we have no value for a key,
   *   // then the next line will not be executed because `getValue` resumes with CancellationException
   *   if (value != null) {
   *     return value
   *   }
   *   return showModalDialogAndGetValue()
   * }
   * ```
   */
  suspend fun getEnvironmentValue(key: EnvironmentKey): String?

  /**
   * Retrieves a value for [key] from the environment.
   *
   * The semantics of returned value is the following:
   * - If the environment has some defined value for [key], then this value is returned.
   * - Otherwise [defaultValue] is returned.
   */
  suspend fun getEnvironmentValue(key: EnvironmentKey, defaultValue: String): String
}
