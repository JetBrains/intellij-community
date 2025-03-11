// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.multiplatform.shims

import fleet.util.multiplatform.Actual
import kotlin.coroutines.CoroutineContext

@Actual("runInterruptibleImpl")
suspend fun runInterruptibleImplWasmJs(context: CoroutineContext, block: () -> Any?) = block()