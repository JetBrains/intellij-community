// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.multiplatform.shims

import fleet.util.multiplatform.Actual
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.alloc
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.Foundation.NSThread
import platform.posix.pthread_threadid_np


@OptIn(ExperimentalForeignApi::class)
@Actual
fun currentThreadIdNative(): Long = memScoped {
  val idVar = alloc<ULongVar>()
  val rc = pthread_threadid_np(null, idVar.ptr)
  if (rc == 0) idVar.value.toLong() else 0L
}


@Actual
fun currentThreadNameNative(): String = NSThread.currentThread.name ?: "thread-${currentThreadIdNative()}"

