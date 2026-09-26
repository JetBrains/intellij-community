// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.multiplatform.shims

import fleet.util.multiplatform.Actual
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

@Actual
suspend fun runInterruptibleImplNative(context: CoroutineContext, block: () -> Any?) = withContext(context) {
  block()
}