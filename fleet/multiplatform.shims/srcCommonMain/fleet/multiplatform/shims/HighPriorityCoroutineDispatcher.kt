// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.multiplatform.shims

import fleet.util.multiplatform.linkToActual
import kotlin.coroutines.CoroutineContext

fun newHighPriorityCoroutineDispatcher(name: String): HighPriorityCoroutineDispatcherResource = linkToActual()

interface HighPriorityCoroutineDispatcherResource {
  suspend fun <U> use(body: suspend (CoroutineContext) -> U): U
}
