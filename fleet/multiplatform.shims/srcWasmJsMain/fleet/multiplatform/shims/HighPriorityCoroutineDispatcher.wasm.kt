// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.multiplatform.shims

import fleet.util.multiplatform.Actual
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@Actual("newHighPriorityCoroutineDispatcher")
fun newHighPriorityCoroutineDispatcherWasm(name: String): HighPriorityCoroutineDispatcherResource =
  object : HighPriorityCoroutineDispatcherResource {
    override suspend fun <U> use(body: suspend (CoroutineContext) -> U): U {
      return body(EmptyCoroutineContext)
    }
  }
