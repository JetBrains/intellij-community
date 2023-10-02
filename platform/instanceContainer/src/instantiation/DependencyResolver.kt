// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.instanceContainer.instantiation

import org.jetbrains.annotations.ApiStatus.OverrideOnly
import java.lang.reflect.Constructor

@OverrideOnly
interface DependencyResolver {

  /**
   * Used for fast filtering constructors out before calling [isInjectable] and [resolveDependency].
   */
  fun isApplicable(constructor: Constructor<*>): Boolean

  /**
   * Used for fast filtering constructors out before calling [resolveDependency].
   */
  fun isInjectable(parameterType: Class<*>): Boolean

  /**
   * Tries to resolve the dependency.
   *
   * @param round index of the current try
   */
  fun resolveDependency(parameterType: Class<*>, round: Int): ArgumentSupplier?
}
