// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.multiplatform.shims

import fleet.util.multiplatform.linkToActual
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext


suspend fun <T> runInterruptible(context: CoroutineContext = EmptyCoroutineContext, block: () -> T): T = runInterruptibleImpl(context, block) as T

suspend fun runInterruptibleImpl(context: CoroutineContext, block: () -> Any?): Any? = linkToActual()