// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl

import com.intellij.psi.PsiManager
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.ParameterizedCachedValue
import com.intellij.psi.util.ParameterizedCachedValueProvider
import java.lang.ref.SoftReference

abstract class PsiParameterizedCachedValue<T, P> internal constructor(
  manager: PsiManager,
  private val myProvider: ParameterizedCachedValueProvider<T, P>,
  trackValue: Boolean
) : PsiCachedValue<T>(manager, trackValue), ParameterizedCachedValue<T, P> {

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
    trackValue: Boolean
  ) : PsiParameterizedCachedValue<T, P>(manager, myProvider, trackValue) {

    constructor(manager: PsiManager, provider: ParameterizedCachedValueProvider<T, P>) : this(manager, provider, false)

    @Volatile
    private var data: SoftReference<Data<T>>? = null

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

  class Direct<T, P>(
    manager: PsiManager,
    myProvider: ParameterizedCachedValueProvider<T, P>,
    trackValue: Boolean
  ) : PsiParameterizedCachedValue<T, P>(manager, myProvider, trackValue) {

    constructor(manager: PsiManager, provider: ParameterizedCachedValueProvider<T, P>) : this(manager, provider, false)

    @Volatile
    private var data: Data<T>? = null

    override fun getRawData(): Data<T>? = data

    override fun setData(data: Data<T>?) {
      this.data = data
    }

    override fun toString(): String {
      return "PsiParameterizedCachedValue.Direct()"
    }
  }
}
