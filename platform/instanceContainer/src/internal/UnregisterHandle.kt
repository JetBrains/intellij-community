// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.instanceContainer.internal

import org.jetbrains.annotations.ApiStatus.NonExtendable

@NonExtendable
fun interface UnregisterHandle {
  /**
   * Undo the registration
   */
  fun unregister(): UnregistrationResult
}

data class RegistrationResult(
  /**
   * A handle to undo the registration
   */
  val unregisterHandle: UnregisterHandle,
)

data class UnregistrationResult(
  /**
   * A map of just unregistered instance holders
   */
  val unregisteredInstances: Map<String, InstanceHolder>,
)