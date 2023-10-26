// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.instanceContainer.internal

import org.jetbrains.annotations.ApiStatus.OverrideOnly

@OverrideOnly
interface DynamicInstanceSupport {

  /**
   * Invoked if requested [instanceClass] is not found in the container,
   * in which case the implementation is supposed to supply [InstanceInitializer] for the [instanceClass],
   * or `null` if [instanceClass] cannot be initialized dynamically, e.g. it's an interface or an abstract class.
   */
  fun dynamicInstanceInitializer(instanceClass: Class<*>): InstanceInitializer?

  fun dynamicInstanceRegistered(dynamicInstanceHolder: InstanceHolder)
}
