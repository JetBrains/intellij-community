// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.multiplatform.shims

import fleet.util.multiplatform.linkToActual
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.jvm.JvmInline

@OptIn(ExperimentalContracts::class)
inline fun <T> synchronized(lock: SynchronizedObject, block: () -> T): T {
  contract {
    callsInPlace(block, InvocationKind.EXACTLY_ONCE)
  }
  @Suppress("LEAKED_IN_PLACE_LAMBDA") // Contract is preserved, invoked immediately or throws
  return synchronizedImpl(lock, block) as T
}

inline fun synchronizedImpl(lock: SynchronizedObject, block: () -> Any?): Any? = linkToActual()

fun SynchronizedObject(): SynchronizedObject = linkToActual()

@JvmInline
value class SynchronizedObject(val lock: Any)