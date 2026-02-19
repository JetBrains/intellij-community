// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.microservices.utils

import com.intellij.util.lazyPub

internal sealed class LazyChain<T> {

  abstract val value: T

  abstract fun chain(trans: (T) -> T): LazyChain<T>

  fun chainLazy(trans: (T) -> T): LazyChain<T> = Delayed { trans(this.value) }

  internal class Immediate<T>(override val value: T) : LazyChain<T>() {
    override fun chain(trans: (T) -> T): Immediate<T> = Immediate(trans(this.value))
  }

  private class Delayed<T>(val trans: () -> T) : LazyChain<T>() {
    override val value: T by lazyPub { trans.invoke() }

    override fun chain(trans: (T) -> T): LazyChain<T> = chainLazy(trans)
  }
}