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
   * It's up to the implementation to decide which instances to return on which try.
   * The implementation should expect that the caller tries to resolve the constructor parameters until all dependencies are satisfied
   * (i.e. while (true) { round ++; doTry(round); }), but in fact there is only 3 tries at the moment.
   *
   * TODO get rid of this parameter, and return available instances on the first try.
   *  At the moment [round] is used to mimic the old behaviour of [com.intellij.serviceContainer.getGreediestSatisfiableConstructor]:
   *  - it returns only statically registered instances on the first try,
   *  - then it returns dynamic instances (=light services), but only if they were requested beforehand,
   *    i.e. depending on the moment, whether instance is created after some light service or before,
   *    the constructor dependencies might be resolved or not,
   *  - then it goes into injecting extension instances.
   *
   * @param round index of the current try
   */
  fun resolveDependency(parameterType: Class<*>, instanceClass: Class<*>, round: Int): ArgumentSupplier?
}
