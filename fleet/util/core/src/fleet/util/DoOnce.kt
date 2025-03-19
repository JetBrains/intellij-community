// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util

import fleet.multiplatform.shims.ConcurrentHashSet
import kotlin.jvm.JvmInline

sealed class DoOnce {
  companion object : DoOnce()

  @JvmInline
  value class Id(val id: String)

  private val done = ConcurrentHashSet<String>()

  fun <T> doOnce(id: String, body: () -> T): T? {
    return if (done.add(id)) body() else null
  }

  suspend fun <T> doOnceSuspend(id: String, body: suspend () -> T): T? {
    return if (done.add(id)) body() else null
  }
}

