// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl

import com.intellij.psi.PsiManager
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import java.lang.ref.SoftReference

open class PsiCachedValueImpl<T>

@Deprecated(message = "Use PsiCachedValueImpl.Soft")
internal constructor(
  manager: PsiManager,
  private val myProvider: CachedValueProvider<T>,
  trackValue: Boolean
) : PsiCachedValue<T>(manager, trackValue), CachedValue<T> {

  @Deprecated(message = "Use PsiCachedValueImpl.Soft")
  @Suppress("DEPRECATION")
  constructor(manager: PsiManager, provider: CachedValueProvider<T>) : this(manager, provider, false)

  @Volatile
  private var data: SoftReference<Data<T>>? = null

  override fun getRawData(): Data<T>? {
    return com.intellij.reference.SoftReference.dereference(data)
  }

  override fun setData(data: Data<T>?) {
    this.data = data?.let { SoftReference(data) }
  }

  override fun getValue(): T? {
    return getValueWithLock<Any>(null)
  }

  override fun getValueProvider(): CachedValueProvider<T> = myProvider

  override fun <P> doCompute(param: P): CachedValueProvider.Result<T>? {
    return myProvider.compute()
  }

  @Suppress("DEPRECATION")
  class Soft<T>(
    manager: PsiManager,
    myProvider: CachedValueProvider<T>,
    trackValue: Boolean
  ) : PsiCachedValueImpl<T>(manager, myProvider, trackValue), CachedValue<T> {

    constructor(manager: PsiManager, provider: CachedValueProvider<T>) : this(manager, provider, false)

    override fun toString(): String {
      return "PsiCachedValue.Soft()"
    }
  }

  class Direct<T>(
    manager: PsiManager,
    private val myProvider: CachedValueProvider<T>,
    trackValue: Boolean
  ) : PsiCachedValue<T>(manager, trackValue), CachedValue<T> {

    constructor(manager: PsiManager, provider: CachedValueProvider<T>) : this(manager, provider, false)

    @Volatile
    private var data: Data<T>? = null

    override fun getRawData(): Data<T>? = data

    override fun setData(data: Data<T>?) {
      this.data = data
    }

    override fun getValueProvider(): CachedValueProvider<T> = myProvider

    override fun <P> doCompute(param: P): CachedValueProvider.Result<T>? {
      return myProvider.compute()
    }

    override fun getValue(): T? {
      return getValueWithLock<Any>(null)
    }

    override fun toString(): String {
      return "PsiCachedValue.Direct()"
    }
  }
}
