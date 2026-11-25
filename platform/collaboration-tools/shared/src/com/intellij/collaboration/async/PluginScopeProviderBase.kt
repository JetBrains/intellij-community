// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.async

import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * A base class for a plugin service which can supply a coroutine scope to places where structured concurrency is not possible
 * Can be subclassed and registered as a light service
 */
@ApiStatus.Internal
open class PluginScopeProviderBase(private val parentCs: CoroutineScope) {
  fun createDisposedScope(name: String, disposable: Disposable, context: CoroutineContext = EmptyCoroutineContext): CoroutineScope {
    return parentCs.childScope(name, context).apply {
      cancelledWith(disposable)
    }
  }

  fun <D : DialogWrapper> constructDialog(name: String, constructor: CoroutineScope.() -> D): D {
    val cs = parentCs.childScope(name)
    return cs.constructor().also {
      cs.cancelledWith(it.disposable)
    }
  }
}