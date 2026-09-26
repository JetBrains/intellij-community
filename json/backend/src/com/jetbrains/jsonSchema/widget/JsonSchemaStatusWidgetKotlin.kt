// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.widget

import com.intellij.openapi.Disposable
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.cancelOnDispose
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlin.coroutines.CoroutineContext

internal object JsonSchemaStatusWidgetKotlin {
  fun childScope(parentScope: CoroutineScope, name: String): CoroutineScope {
    return parentScope.childScope(name)
  }

  fun cancelOnDispose(scope: CoroutineScope, disposable: Disposable) {
    scope.coroutineContext.job.cancelOnDispose(disposable)
  }

  fun cancel(scope: CoroutineScope) {
    scope.cancel()
  }
}
