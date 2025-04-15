// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalAtomicApi::class)

package fleet.util

import fleet.util.multiplatform.Actual
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@Actual
fun <T> AtomicReference<T>.updateAndGetWasmJs(f: (T) -> T): T {
  val new = f(load())
  store(new)
  return new
}

@Actual
fun <T> AtomicReference<T>.getAndUpdateWasmJs(f: (T) -> T): T {
  val old = load()
  store(f(old))
  return old
}