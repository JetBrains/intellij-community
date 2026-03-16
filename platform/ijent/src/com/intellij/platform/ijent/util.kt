// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import com.intellij.openapi.application.contextModality
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlin.coroutines.CoroutineContext

suspend fun currentCoroutineDispatchingContext(): CoroutineContext {
  val currentCoroutineContext = currentCoroutineContext()
  val currentCoroutineDispatcher = currentCoroutineDispatcher()
  val currentContextModality = currentCoroutineContext.contextModality()
  return if (currentContextModality != null) currentCoroutineDispatcher + currentCoroutineContext else currentCoroutineDispatcher
}

@OptIn(ExperimentalStdlibApi::class)
suspend fun currentCoroutineDispatcher(): CoroutineDispatcher {
  return currentCoroutineContext()[CoroutineDispatcher] ?: Dispatchers.Default
}

@OptIn(ExperimentalStdlibApi::class)
fun CoroutineScope.coroutineDispatcher(): CoroutineDispatcher = this.coroutineContext[CoroutineDispatcher] ?: Dispatchers.Default