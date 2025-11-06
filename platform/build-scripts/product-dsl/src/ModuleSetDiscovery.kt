// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.productLayout

import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

/**
 * Discovers all module sets from an object using reflection.
 * Returns all public functions that return ModuleSet and take no parameters.
 * 
 * This consolidates the duplicated discovery logic that was in both UltimateModuleSets and ultimateGenerator.
 */
fun discoverModuleSets(provider: Any): List<ModuleSet> {
  val lookup = MethodHandles.lookup()
  val clazz = provider.javaClass
  val methodType = MethodType.methodType(ModuleSet::class.java)

  val declaredMethods = clazz.declaredMethods
  val result = ArrayList<ModuleSet>(declaredMethods.size)
  for (method in declaredMethods) {
    if (method.parameterCount == 0 &&
        java.lang.reflect.Modifier.isPublic(method.modifiers) &&
        method.returnType == ModuleSet::class.java) {
      val moduleSet = lookup.findVirtual(clazz, method.name, methodType).invoke(provider) as ModuleSet
      result.add(moduleSet)
    }
  }
  return result
}
