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
   * Retrieves a value from the environment.
   *
   * If the environment does not have a value for the [key], then the behavior of this method is implementation-defined.
   * In particular, current implementations of this service have the following semantics on an absent key:
   * - If the environment allows the usage of UI, then this method returns `null`, and the client may use a modal dialog to get a value.
   * - If the environment does not allow the usage of UI, then [java.util.concurrent.CancellationException] is thrown, so [requestEnvironmentValue] never
   * returns `null` in the headless environment.
   *
   * The supposed workflow is sketched in the following snippet:
   * ```
   * suspend fun requestUserDecision() : ChoiceData {
   *   val value = service<EnvironmentParametersService>.getEnvironmentValue(MY_AWESOME_KEY)
   *   // if we are in a headless environment and we have no value for a key,
   *   // then the next line will not be executed because of an exception
   *   if (value != null) {
   *     return value
   *   }
   *   return showModalDialogAndGetValue()
   * }
   * ```
   *
   * The documentation above is about platform behavior only. Custom implementations of this service may possess other behavior.
   */
  suspend fun requestEnvironmentValue(key: EnvironmentKey): String?

  /**
   * The same as [requestEnvironmentValue], but always returns `null` when a value for [key] is absent.
   *
   * Please avoid using this method if you are not sure that you need it.
   * The environment has more capabilities to obtain a value than the client code.
   *
   * For specifying default values, consider using [EnvironmentKey.defaultValue].
   */
  suspend fun getEnvironmentValueOrNull(key: EnvironmentKey): String?

}