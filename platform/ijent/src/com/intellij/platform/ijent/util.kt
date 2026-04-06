// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlin.coroutines.CoroutineContext

fun defaultCoroutineDispatchingContext(): CoroutineContext =
  ModalityState.any().asContextElement() + Dispatchers.IO

@OptIn(ExperimentalStdlibApi::class)
suspend fun currentCoroutineDispatcher(): CoroutineDispatcher {
  return currentCoroutineContext()[CoroutineDispatcher] ?: Dispatchers.Default
}

@OptIn(ExperimentalStdlibApi::class)
fun CoroutineScope.coroutineDispatcher(): CoroutineDispatcher = this.coroutineContext[CoroutineDispatcher] ?: Dispatchers.Default