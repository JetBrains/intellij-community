// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.extensions

import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.impl.ExtensionProcessingHelper.computeSafeIfAny
import com.intellij.openapi.extensions.impl.ExtensionProcessingHelper.findFirstSafe
import com.intellij.util.ThreeState
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.util.function.Function
import java.util.function.Predicate

/**
 * Do not use.
 *
 * Provides access to a project-level or module-level extension point. Since extensions are supposed to be stateless, storing different
 * instances of an extension for each project or module just wastes the memory and complicates code, so **it's strongly recommended not
 * to introduce new project-level and module-level extension points**. If you need to have [Project][com.intellij.openapi.project.Project]
 * or [Module][com.intellij.openapi.module.Module] instance in some extension's method, pass it as a parameter and use the default
 * application-level extension point.
 */
class ProjectExtensionPointName<T : Any>(name: @NonNls String) : BaseExtensionPointName<T>(name) {
  fun getPoint(areaInstance: AreaInstance): ExtensionPoint<T> = getPointImpl(areaInstance)

  fun getExtensions(areaInstance: AreaInstance): List<T> = getPointImpl(areaInstance).extensionList

  fun <V : T> findExtension(instanceOf: Class<V>, areaInstance: AreaInstance): V? {
    return getPointImpl(areaInstance).findExtension(instanceOf, false, ThreeState.UNSURE)
  }

  fun <V : T> findExtensionOrFail(instanceOf: Class<V>, areaInstance: AreaInstance): V {
    return getPointImpl(areaInstance).findExtension(instanceOf, true, ThreeState.UNSURE)!!
  }

  fun hasAnyExtensions(areaInstance: AreaInstance): Boolean = getPointImpl(areaInstance).size() != 0

  fun findFirstSafe(areaInstance: AreaInstance, predicate: Predicate<in T>): T? {
    return findFirstSafe(predicate = predicate, sequence = getPointImpl(areaInstance).asSequence())
  }

  fun <R> computeSafeIfAny(areaInstance: AreaInstance, processor: Function<T, R?>): R? {
    return computeSafeIfAny(processor = processor::apply, sequence = getPointImpl(areaInstance).asSequence())
  }

  fun addExtensionPointListener(areaInstance: AreaInstance, listener: ExtensionPointListener<T>, parentDisposable: Disposable?) {
    getPointImpl(areaInstance).addExtensionPointListener(listener, false, parentDisposable)
  }

  fun addChangeListener(areaInstance: AreaInstance, listener: Runnable, parentDisposable: Disposable?) {
    getPointImpl(areaInstance).addChangeListener(listener, parentDisposable)
  }

  @ApiStatus.Experimental
  fun asSequence(areaInstance: AreaInstance): Sequence<T> = getPointImpl(areaInstance).asSequence()
}