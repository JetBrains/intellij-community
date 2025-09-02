// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job

internal fun <T> removeOnCompletion(list: MutableList<T>, item: T, coroutineScope: CoroutineScope) {
  coroutineScope.coroutineContext.job.invokeOnCompletion { list.remove(item) }
}