// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.multiplatform.shims

import fleet.util.multiplatform.linkToActual
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.CoroutineContext

fun newSingleThreadCoroutineDispatcher(
  name: String,
  priority: DispatcherPriority = DispatcherPriority.NORMAL
): HighPriorityCoroutineDispatcherResource = linkToActual()

interface HighPriorityCoroutineDispatcherResource {
  suspend fun <U> use(body: suspend CoroutineScope.(CoroutineContext) -> U): U
}

enum class DispatcherPriority {
  HIGH, NORMAL, LOW
}