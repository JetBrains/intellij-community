// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.multiplatform.shims

import fleet.util.multiplatform.Actual
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@Actual("newSingleThreadCoroutineDispatcher")
fun newSingleThreadCoroutineDispatcherWasmJs(
  name: String,
  priority: DispatcherPriority
): HighPriorityCoroutineDispatcherResource =
  object : HighPriorityCoroutineDispatcherResource {
    override suspend fun <U> use(body: suspend CoroutineScope.(CoroutineContext) -> U): U {
      return coroutineScope { body(EmptyCoroutineContext) }
    }
  }
