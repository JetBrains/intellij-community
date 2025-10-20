// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.multiplatform.shims

import fleet.util.multiplatform.Actual

@Actual
internal inline fun synchronizedImplJvm(lock: SynchronizedObject, block: () -> Any?): Any? =
  kotlin.synchronized(lock.lock, block)

@Actual
fun SynchronizedObjectJvm(): SynchronizedObject = SynchronizedObject(Any())