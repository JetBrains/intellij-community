// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.idea

import com.intellij.openapi.application.Application
import kotlinx.coroutines.Deferred
import org.jetbrains.annotations.ApiStatus
import kotlin.coroutines.CoroutineContext

@ApiStatus.Internal
class InitAppContext(
  @JvmField val context: CoroutineContext,
  @JvmField val args: List<String>,
  @JvmField val appDeferred: Deferred<Application>,
)