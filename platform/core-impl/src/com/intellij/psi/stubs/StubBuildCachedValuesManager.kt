// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.CompositeElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.ParameterizedCachedValue
import com.intellij.psi.util.ParameterizedCachedValueProvider
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Function

@ApiStatus.Internal
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
    node: CompositeElement,
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
    return CachedValuesManager.getManager(node.getManager().getProject()).getParameterizedCachedValue(
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

  class StubBuildCachedValue<T>(val buildId: Long, val value: T)
}
