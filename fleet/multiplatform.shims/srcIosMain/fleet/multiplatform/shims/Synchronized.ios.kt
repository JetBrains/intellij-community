// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.multiplatform.shims

import fleet.util.multiplatform.Actual
import kotlinx.coroutines.*
import kotlinx.atomicfu.locks.withLock as withLock2

@Actual
inline fun synchronizedImplNative(lock: SynchronizedObject, block: () -> Any?): Any? =
  (lock.lock as kotlinx.atomicfu.locks.SynchronizedObject).withLock2(block)

@OptIn(InternalCoroutinesApi::class)
@Actual
fun SynchronizedObjectNative(): SynchronizedObject = SynchronizedObject(kotlinx.atomicfu.locks.SynchronizedObject())
