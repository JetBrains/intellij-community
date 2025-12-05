// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs

import com.intellij.lang.ASTNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.ParameterizedCachedValue
import com.intellij.psi.util.ParameterizedCachedValueProvider
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Function

/**
 * This class serves as an entry point for optimizing usage of cached values during stub building, i.e., during indexing.
 *
 * Each [com.intellij.psi.StubBuilder] should ensure that [startBuildingStubs] is called before starting the stub building process
 * and [finishBuildingStubs] is called after the stub building process is finished.
 *
 * The main scenario is to replace [com.intellij.psi.util.CachedValuesManager.getCachedValue] or
 * [com.intellij.psi.util.CachedValuesManager.getParameterizedCachedValue] calls, which are used during stub building,
 * with calls to [getCachedValueStubBuildOptimized]. The [com.intellij.psi.util.CachedValuesManager.getCachedValue] call performs quite
 * costly checks to ensure that a cached value is up to date, which is an unnecessary work during stub building,
 * as none of the other AST elements of the file can change in the meanwhile. You should use [getCachedValueStubBuildOptimized]
 * overload with [com.intellij.psi.util.ParameterizedCachedValueProvider], because it avoids instantiation of a lambda object,
 * saving some tiny, but precious, amount of time during indexing.
 *
 * The other scenario is to use [getCachedValueIfBuildingStubs] to cache some results only during stub building,
 * as using a regular [com.intellij.psi.util.CachedValue] would be overkill; i.e., it would be more expensive to check if a CachedValue
 * is up to date than to compute the value.
 */
@ApiStatus.Experimental
object StubBuildCachedValuesManager {

  private val myStubBuildId = ThreadLocal<Long?>()
  private val ourStubBuildIdCounter = AtomicLong()

  @JvmStatic
  @ApiStatus.Internal
  fun startBuildingStubs() {
    myStubBuildId.set(ourStubBuildIdCounter.getAndIncrement())
  }

  @JvmStatic
  @ApiStatus.Internal
  fun finishBuildingStubs() {
    myStubBuildId.remove()
  }

  @JvmStatic
  val isBuildingStubs: Boolean
    get() = myStubBuildId.get() != null

  private val stubBuildId: Long?
    get() = myStubBuildId.get()

  @JvmStatic
  fun <T, P> getCachedValueIfBuildingStubs(
      dataHolder: PsiElement,
      stubBuildingKey: Key<StubBuildCachedValue<T>>,
      parameter: P,
      provider: Function<P, T>
  ): T {
    val stubBuildId = stubBuildId
    if (stubBuildId != null) {
      val node = dataHolder.getNode()
      var current = node.getUserData(stubBuildingKey)
      if (current == null || current.buildId != stubBuildId) {
        current = StubBuildCachedValue<T>(stubBuildId, provider.apply(parameter))
        node.putUserData<StubBuildCachedValue<T>>(stubBuildingKey, current)
      }
      return current.value
    }
    return provider.apply(parameter)
  }

  @JvmStatic
  fun <T, P> getCachedValueStubBuildOptimized(
    node: ASTNode,
    project: Project,
    key: Key<ParameterizedCachedValue<T, P>>,
    stubBuildingKey: Key<StubBuildCachedValue<T>>,
    provider: ParameterizedCachedValueProvider<T, P>,
    parameter: P
  ): T {
    val stubBuildId = stubBuildId
    if (stubBuildId != null) {
      var current = node.getUserData(stubBuildingKey)
      if (current == null || current.buildId != stubBuildId) {
        val value = provider.compute(parameter)
        current = StubBuildCachedValue(stubBuildId, value.getValue())
        node.putUserData(stubBuildingKey, current)
      }
      return current.value
    }
    return CachedValuesManager.getManager(project).getParameterizedCachedValue(
      node, key, provider, false, parameter
    )
  }

  // Avoid using recursion manager and other complex logic when building stubs - improves speed by 10%.
  @JvmStatic
  fun <T, P> getCachedValueStubBuildOptimized(
      dataHolder: PsiElement,
      key: Key<ParameterizedCachedValue<T, P>>,
      stubBuildingKey: Key<StubBuildCachedValue<T>>,
      provider: ParameterizedCachedValueProvider<T, P>,
      parameter: P
  ): T {
    val stubBuildId = stubBuildId
    if (stubBuildId != null) {
      val node = dataHolder.getNode()
      var current = node.getUserData(stubBuildingKey)
      if (current == null || current.buildId != stubBuildId) {
        val value = provider.compute(parameter)
        current = StubBuildCachedValue(stubBuildId, value.getValue())
        node.putUserData(stubBuildingKey, current)
      }
      return current.value
    }
    return CachedValuesManager.getManager(dataHolder.getProject()).getParameterizedCachedValue(
      dataHolder, key, provider, false, parameter
    )
  }

  /**
   * This overload is provided for convenience. Consider
   * using ParameterizedCachedValueProvider without a lambda, which
   * improves performance by avoiding unnecessary lambda instantiation.
   */
  @JvmStatic
  fun <T> getCachedValueStubBuildOptimized(
      dataHolder: PsiElement,
      stubBuildingKey: Key<StubBuildCachedValue<T?>>,
      provider: CachedValueProvider<T>,
  ): T? {
    val stubBuildId = stubBuildId
    if (stubBuildId != null) {
      val node = dataHolder.getNode()
      var current = node.getUserData(stubBuildingKey)
      if (current == null || current.buildId != stubBuildId) {
        val value = provider.compute()
        current = StubBuildCachedValue(stubBuildId, value?.getValue())
        node.putUserData(stubBuildingKey, current)
      }
      return current.value
    }
    return CachedValuesManager.getCachedValue(dataHolder, provider)
  }

  class StubBuildCachedValue<T> internal constructor(internal val buildId: Long, internal val value: T)
}