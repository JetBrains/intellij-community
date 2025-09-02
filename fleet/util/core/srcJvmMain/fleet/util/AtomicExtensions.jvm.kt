// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

import fleet.util.multiplatform.Actual
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.asJavaAtomic

@Actual
fun <T> AtomicReference<T>.updateAndGetJvm(f: (T) -> T): T {
  return asJavaAtomic().updateAndGet(f)
}

@Actual
fun <T> AtomicReference<T>.getAndUpdateJvm(f: (T) -> T): T {
  return asJavaAtomic().getAndUpdate(f)
}