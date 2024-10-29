// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl

import com.intellij.psi.PsiManager
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import org.jetbrains.annotations.ApiStatus
import java.lang.ref.SoftReference

sealed class AbstractPsiCachedValue<T>(
  manager: PsiManager,
  private val myProvider: CachedValueProvider<T>
) : PsiCachedValue<T>(manager), CachedValue<T> {
  override fun getValue(): T? {
    return getValueWithLock<Any>(null)
  }

  final override fun getValueProvider(): CachedValueProvider<T> = myProvider

  final override fun <P> doCompute(param: P): CachedValueProvider.Result<T>? {
    return myProvider.compute()
  }
}

open class PsiCachedValueImpl<T>

@ApiStatus.ScheduledForRemoval
@Deprecated(message = "Use PsiCachedValueImpl.Soft")
internal constructor(
  manager: PsiManager,
  private val myProvider: CachedValueProvider<T>,
  private val trackValue: Boolean
) : PsiCachedValue<T>(manager), CachedValue<T> {
  @Suppress("DEPRECATION")
  @Deprecated(message = "Use PsiCachedValueImpl.Soft")
  constructor(manager: PsiManager, provider: CachedValueProvider<T>) : this(manager, provider, false)

  override fun isTrackValue(): Boolean = trackValue

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

  final override fun getValueProvider(): CachedValueProvider<T> = myProvider

  final override fun <P> doCompute(param: P): CachedValueProvider.Result<T>? {
    return myProvider.compute()
  }

  class Soft<T>(
    manager: PsiManager,
    myProvider: CachedValueProvider<T>,
  ) : AbstractPsiCachedValue<T>(manager, myProvider), CachedValue<T> {
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
      return "PsiCachedValue.Soft()"
    }
  }

  class SoftTracked<T>(
    manager: PsiManager,
    myProvider: CachedValueProvider<T>,
  ) : AbstractPsiCachedValue<T>(manager, myProvider), CachedValue<T> {
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
      return "PsiCachedValue.SoftTracked()"
    }
  }

  class Direct<T>(
    manager: PsiManager,
    myProvider: CachedValueProvider<T>,
  ) : AbstractPsiCachedValue<T>(manager, myProvider), CachedValue<T> {
    @Volatile
    private var data: Data<T>? = null

    override fun isTrackValue(): Boolean = false

    override fun getRawData(): Data<T>? = data

    override fun setData(data: Data<T>?) {
      this.data = data
    }

    override fun getValue(): T? {
      return getValueWithLock<Any>(null)
    }

    override fun toString(): String {
      return "PsiCachedValue.Direct()"
    }
  }

  class DirectTracked<T>(
    manager: PsiManager,
    myProvider: CachedValueProvider<T>,
  ) : AbstractPsiCachedValue<T>(manager, myProvider), CachedValue<T> {
    @Volatile
    private var data: Data<T>? = null

    override fun isTrackValue(): Boolean = true

    override fun getRawData(): Data<T>? = data

    override fun setData(data: Data<T>?) {
      this.data = data
    }

    override fun getValue(): T? {
      return getValueWithLock<Any>(null)
    }

    override fun toString(): String {
      return "PsiCachedValue.DirectTracked()"
    }
  }
}
