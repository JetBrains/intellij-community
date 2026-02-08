// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/**
 * Discovers module sets from provider objects using reflection.
 *
 * This file provides the reflection-based discovery mechanism that finds all module set
 * definitions in provider classes like [CommunityModuleSets] and [UltimateModuleSets].
 * Any public no-argument function returning [ModuleSet] is automatically discovered.
 *
 * **Discovery rules**:
 * - Function must be public
 * - Function must take no parameters
 * - Function must return [ModuleSet]
 *
 * **Caching**: Method handles are cached per class to avoid repeated reflection overhead.
 *
 * **Key function**: [discoverModuleSets] - discovers and invokes all module set functions
 * from a provider object.
 *
 * @see ModuleSet for the discovered type
 * @see <a href="../../docs/module-sets.md">Module Sets Documentation</a>
 */
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.productLayout.discovery

import org.jetbrains.intellij.build.productLayout.ModuleSet
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.concurrent.ConcurrentHashMap

/**
 * Cache for reflected method handles per class.
 * Avoids repeated reflection and method handle lookup for the same provider class.
 */
private val methodHandleCache = ConcurrentHashMap<Class<*>, List<MethodHandle>>()

/**
 * Discovers all module sets from an object using reflection.
 * Returns all public functions that return ModuleSet and take no parameters.
 *
 * Method handles are cached per class to avoid repeated reflection overhead.
 * This consolidates the duplicated discovery logic that was in both UltimateModuleSets and ultimateGenerator.
 */
fun discoverModuleSets(provider: Any): List<ModuleSet> {
  val clazz = provider.javaClass
  val handles = methodHandleCache.computeIfAbsent(clazz) { discoverMethodHandles(it) }

  val result = ArrayList<ModuleSet>(handles.size)
  for (handle in handles) {
    result.add(handle.invoke(provider) as ModuleSet)
  }
  return result
}

/**
 * Discovers and caches method handles for all public no-arg methods returning ModuleSet.
 */
private fun discoverMethodHandles(clazz: Class<*>): List<MethodHandle> {
  val lookup = MethodHandles.lookup()
  val methodType = MethodType.methodType(ModuleSet::class.java)

  val declaredMethods = clazz.declaredMethods
  val handles = ArrayList<MethodHandle>()
  for (method in declaredMethods) {
    if (method.parameterCount == 0 &&
        java.lang.reflect.Modifier.isPublic(method.modifiers) &&
        method.returnType == ModuleSet::class.java) {
      handles.add(lookup.findVirtual(clazz, method.name, methodType))
    }
  }
  return handles
}
