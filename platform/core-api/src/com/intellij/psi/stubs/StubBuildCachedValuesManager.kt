// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs

import com.intellij.lang.ASTNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.stubs.StubBuildCachedValuesManager.finishBuildingStubs
import com.intellij.psi.stubs.StubBuildCachedValuesManager.getCachedValueIfBuildingStubs
import com.intellij.psi.stubs.StubBuildCachedValuesManager.getCachedValueStubBuildOptimized
import com.intellij.psi.stubs.StubBuildCachedValuesManager.startBuildingStubs
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.ParameterizedCachedValue
import com.intellij.psi.util.ParameterizedCachedValueProvider
import com.intellij.util.ArrayUtil
import org.jetbrains.annotations.ApiStatus
import java.util.Objects
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Function
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * This class serves as an entry point for optimizing usage of cached values during stub building, i.e., during indexing.
 *
 * Each [com.intellij.psi.StubBuilder] should ensure that [startBuildingStubs] is called before starting the stub building process
 * and [finishBuildingStubs] is called after the stub building process is finished.
 *
 * The main scenario is to replace [CachedValuesManager.getCachedValue] or
 * [CachedValuesManager.getParameterizedCachedValue] calls, which are used during stub building,
 * with calls to [getCachedValueStubBuildOptimized]. The [CachedValuesManager.getCachedValue] call performs quite
 * costly checks to ensure that a cached value is up to date, which is an unnecessary work during stub building,
 * as none of the other AST elements of the file can change in the meanwhile. You should use [getCachedValueStubBuildOptimized]
 * overload with [ParameterizedCachedValueProvider], because it avoids instantiation of a lambda object,
 * saving some tiny, but precious, amount of time during indexing.
 *
 * The other scenario is to use [getCachedValueIfBuildingStubs] to cache some results only during stub building,
 * as using a regular [com.intellij.psi.util.CachedValue] would be overkill; i.e., it would be more expensive to check if a CachedValue
 * is up to date than to compute the value.
 */
object StubBuildCachedValuesManager {

  private val myStubBuildId = ThreadLocal<Long?>()
  private val myComputingCachedValueLevel = ThreadLocal<Int?>()
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

  @JvmStatic
  @get:ApiStatus.Internal
  val isComputingCachedValue: Boolean
    get() = (myComputingCachedValueLevel.get() ?: 0) > 0

  private val stubBuildId: Long?
    get() = myStubBuildId.get()

  @JvmStatic
  fun <T, P> getCachedValueIfBuildingStubs(
    dataHolder: PsiElement,
    stubBuildingKey: Key<StubBuildCachedValue<T>>,
    parameter: P,
    provider: Function<P, T>,
  ): T = computeCachedValue(
    { dataHolder },
    stubBuildingKey,
    { provider.apply(parameter) },
    { provider.apply(parameter) }
  )

  @JvmStatic
  fun <T, P> getCachedValueStubBuildOptimized(
    node: ASTNode,
    project: Project,
    key: Key<ParameterizedCachedValue<T, P>>,
    stubBuildingKey: Key<StubBuildCachedValue<T>>,
    provider: ParameterizedCachedValueProvider<T, P>,
    parameter: P,
  ): T =
    computeCachedValue(
      { node },
      stubBuildingKey,
      { provider.compute(parameter).getValue() },
      {
        CachedValuesManager.getManager(project).getParameterizedCachedValue(
          node, key, provider.wrapWithNonPhysicalPsiHandlerProviderIfNeeded(parameter),
          false, parameter
        )
      }
    )

  @JvmStatic
  fun <T, P : PsiElement> getCachedValueStubBuildOptimized(
    psiElement: P,
    provider: StubBuildCachedValueProvider<T, P>,
  ): T =
    computeCachedValue(
      { psiElement.takeIf { it !is PsiFile }?.getNode() ?: psiElement },
      provider.stubCacheKey,
      { provider.parametrizedCachedValueProvider.compute(psiElement).getValue() },
      {
        CachedValuesManager.getManager(psiElement.getProject()).getParameterizedCachedValue(
          psiElement,
          provider.parametrizedCacheKey,
          provider.parametrizedCachedValueProvider.wrapWithNonPhysicalPsiHandlerProviderIfNeeded(psiElement),
          false,
          psiElement
        )
      }
    )

  /**
   * This overload is provided for convenience. Consider
   * using ParameterizedCachedValueProvider without a lambda, which
   * improves performance by avoiding unnecessary lambda instantiation.
   */
  @JvmStatic
  fun <T> getCachedValueStubBuildOptimized(
    psiElement: PsiElement,
    stubBuildingKey: Key<StubBuildCachedValue<T>>,
    provider: CachedValueProvider<T>,
  ): T =
    computeCachedValue(
      { psiElement.takeIf { it !is PsiFile }?.getNode() ?: psiElement },
      stubBuildingKey,
      {
        provider.compute().apply {
          if (this == null)
            throw IllegalStateException("Cached value provider returned null result. It is not allowed when using getCachedValueStubBuildOptimized.")
        }!!.value
      },
      { CachedValuesManager.getCachedValue(psiElement, provider) }
    )

  class StubBuildCachedValueProvider<ResultType, ParameterType>(
    key: String,
    val parametrizedCachedValueProvider: ParameterizedCachedValueProvider<ResultType, ParameterType>,
  ) {
    val stubCacheKey: Key<StubBuildCachedValue<ResultType>> = Key.create("$key.stub.building")
    val parametrizedCacheKey: Key<ParameterizedCachedValue<ResultType, ParameterType>> = Key.create(key)
  }

  class StubBuildCachedValue<T> internal constructor(internal val buildId: Long, internal val value: T)

  @Suppress("NOTHING_TO_INLINE")
  private inline fun <T, P> ParameterizedCachedValueProvider<T, P>.wrapWithNonPhysicalPsiHandlerProviderIfNeeded(parameter: P): ParameterizedCachedValueProvider<T, P> =
    if (parameter is PsiElement && !parameter.isPhysical())
      NonPhysicalPsiHandlerProvider(this)
    else
      this

  @OptIn(ExperimentalContracts::class)
  private inline fun <T> computeCachedValue(
    dataHolderProvider: () -> UserDataHolder,
    stubBuildingKey: Key<StubBuildCachedValue<T>>,
    stubCachedValueProvider: () -> T,
    regularCacheValueProvider: () -> T,
  ): T {
    contract {
      callsInPlace(dataHolderProvider, InvocationKind.AT_MOST_ONCE)
      callsInPlace(stubCachedValueProvider, InvocationKind.AT_MOST_ONCE)
      callsInPlace(regularCacheValueProvider, InvocationKind.AT_MOST_ONCE)
    }

    val stubBuildId = stubBuildId
    if (stubBuildId != null) {
      val dataHolder = dataHolderProvider()
      var current = dataHolder.getUserData(stubBuildingKey)
      if (current == null || current.buildId != stubBuildId) {
        val level = myComputingCachedValueLevel.get() ?: 0
        myComputingCachedValueLevel.set(level + 1)
        val value = try {
          stubCachedValueProvider()
        }
        finally {
          if (level == 0) {
            myComputingCachedValueLevel.remove()
          }
          else {
            myComputingCachedValueLevel.set(level)
          }
        }
        current = StubBuildCachedValue(stubBuildId, value)
        dataHolder.putUserData(stubBuildingKey, current)
      }
      return current.value
    }
    return regularCacheValueProvider()
  }

  private class NonPhysicalPsiHandlerProvider<T, P>(private val delegate: ParameterizedCachedValueProvider<T, P>) :
    ParameterizedCachedValueProvider<T, P> {
    override fun compute(context: P): CachedValueProvider.Result<T>? {
      val result = delegate.compute(context)
      if (result != null && context is PsiElement && !context.isPhysical()) {
        val file = context.getContainingFile()
        if (file != null) {
          val adjusted = CachedValueProvider.Result.create<T>(
            result.getValue(), *ArrayUtil.append(result.dependencyItems, file, ArrayUtil.OBJECT_ARRAY_FACTORY))
          return adjusted
        }
      }
      return result
    }

    override fun equals(other: Any?): Boolean {
      if (other == null || javaClass != other.javaClass) return false
      val provider = other as NonPhysicalPsiHandlerProvider<*, *>
      return delegate == provider.delegate
    }

    override fun hashCode(): Int = Objects.hashCode(delegate)

    override fun toString(): String = delegate.toString()
  }
}