// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

import fleet.util.multiplatform.linkToActual
import kotlin.concurrent.atomics.AtomicReference

fun <T> AtomicReference<T>.updateAndGet(f: (T) -> T): T  = linkToActual()

fun <T> AtomicReference<T>.getAndUpdate(f: (T) -> T): T = linkToActual()