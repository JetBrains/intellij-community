// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.instanceContainer.internal

import org.jetbrains.annotations.ApiStatus.NonExtendable

@NonExtendable
fun interface UnregisterHandle {

  /**
   * @return map of registered instance holders back to the code which invoked the registration
   */
  fun unregister(): Map<String, InstanceHolder>
}
