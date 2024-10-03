// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl

import com.intellij.psi.PsiManager
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.ParameterizedCachedValue
import com.intellij.psi.util.ParameterizedCachedValueProvider
import org.jetbrains.annotations.ApiStatus
import java.lang.ref.SoftReference

@ApiStatus.Internal
sealed class PsiParameterizedCachedValue<T, P> protected constructor(
  manager: PsiManager,
  private val myProvider: ParameterizedCachedValueProvider<T, P>,
) : PsiCachedValue<T>(manager), ParameterizedCachedValue<T, P> {

  override fun getValue(param: P): T? {
    return getValueWithLock(param)
  }

  override fun getValueProvider(): ParameterizedCachedValueProvider<T, P> = myProvider

  override fun <X> doCompute(param: X): CachedValueProvider.Result<T>? {
    return myProvider.compute(param as P)
  }

  class Soft<T, P>(
    manager: PsiManager,
    myProvider: ParameterizedCachedValueProvider<T, P>,
  ) : PsiParameterizedCachedValue<T, P>(manager, myProvider) {
    @Volatile
    private var data: SoftReference<Data<T>>? = null

    override fun isTrackValue(): Boolean = false

    override fun getRawData(): Data<T>? {
      return com.intellij.reference.SoftReference.dereference(data)
    }

    override fun setData(data: Data<T>?) {
      this.data = data?.let { SoftReference(data) }
    }

    override fun toString(): String {
      return "PsiParameterizedCachedValue.Soft()"
    }
  }

  class SoftTracked<T, P>(
    manager: PsiManager,
    myProvider: ParameterizedCachedValueProvider<T, P>,
  ) : PsiParameterizedCachedValue<T, P>(manager, myProvider) {
    @Volatile
    private var data: SoftReference<Data<T>>? = null

    override fun isTrackValue(): Boolean = true

    override fun getRawData(): Data<T>? {
      return com.intellij.reference.SoftReference.dereference(data)
    }

    override fun setData(data: Data<T>?) {
      this.data = data?.let { SoftReference(data) }
    }

    override fun toString(): String {
      return "PsiParameterizedCachedValue.SoftTracked()"
    }
  }

  class Direct<T, P>(
    manager: PsiManager,
    myProvider: ParameterizedCachedValueProvider<T, P>,
  ) : PsiParameterizedCachedValue<T, P>(manager, myProvider) {
    @Volatile
    private var data: Data<T>? = null

    override fun isTrackValue(): Boolean = false

    override fun getRawData(): Data<T>? = data

    override fun setData(data: Data<T>?) {
      this.data = data
    }

    override fun toString(): String {
      return "PsiParameterizedCachedValue.Direct()"
    }
  }

  class DirectTracked<T, P>(
    manager: PsiManager,
    myProvider: ParameterizedCachedValueProvider<T, P>,
  ) : PsiParameterizedCachedValue<T, P>(manager, myProvider) {
    @Volatile
    private var data: Data<T>? = null

    override fun isTrackValue(): Boolean = true

    override fun getRawData(): Data<T>? = data

    override fun setData(data: Data<T>?) {
      this.data = data
    }

    override fun toString(): String {
      return "PsiParameterizedCachedValue.DirectTracked()"
    }
  }
}
