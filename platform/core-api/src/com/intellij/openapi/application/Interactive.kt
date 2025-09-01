// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Put into *root* builder to mark it interactive: all exceptions are displayed to user immediately
 */
@ApiStatus.Internal
class Interactive(val action: @Nls String) : AbstractCoroutineContextElement(Key) {
  companion object Key : CoroutineContext.Key<Interactive>
}

/**
 * Launches code in [Interactive] mode
 */
@ApiStatus.Internal
fun CoroutineScope.launchInteractive(action: @Nls String, context: CoroutineContext = EmptyCoroutineContext, block: suspend CoroutineScope.() -> Unit) {
  launch(context + Interactive(action)) { block() }
}
