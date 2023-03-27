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
   * Retrieves a value for [key] from the environment and returns `null` if value is not defined.
   *
   * This method can be used if a client can provide their own ways to mitigate the absence of a value.
   * If the key is necessary, then [requestEnvironmentValue] should be used
   */
  suspend fun getEnvironmentValue(key: EnvironmentKey): String?

  /**
   * The same as [getEnvironmentValue], but avoids nullability issues when [key] has a default value.
   */
  suspend fun getEnvironmentValue(key: DefaultedEnvironmentKey): String = getEnvironmentValue(key as EnvironmentKey) ?: key.defaultValue

  /**
   * Retrieves a value from the environment and performs environment-specific action if value is not defined.
   *
   * In particular, current implementations of this service have the following semantics on an absent key:
   * - If the environment allows the usage of UI, then this method returns `null`, and the client may use a modal dialog to get a value.
   * - If the environment does not allow the usage of UI, then [java.util.concurrent.CancellationException] is thrown, so [requestEnvironmentValue] never
   * returns `null` in the headless environment.
   *
   * The supposed workflow is sketched in the following snippet:
   * ```
   * suspend fun requestUserDecision() : ChoiceData {
   *   val value : String = service<EnvironmentService>.requestEnvironmentValue(MY_AWESOME_KEY)
   *   // if we are in a headless environment and we have no value for a key,
   *   // then the next line will not be executed because of an exception
   *   if (value != null) {
   *     return ChoiceData.ofString(value)
   *   }
   *   return showModalDialogAndGetValue()
   * }
   * ```
   *
   * The documentation above is about platform behavior only. Custom implementations of this service may possess other behavior.
   */
  suspend fun requestEnvironmentValue(key: EnvironmentKey): String?

}