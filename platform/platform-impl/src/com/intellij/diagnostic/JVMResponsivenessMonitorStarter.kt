// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.ide.ApplicationInitializedListener
import com.intellij.openapi.components.service
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

/** Starts [JVMResponsivenessMonitor] on app start */
@ApiStatus.Internal
internal class JVMResponsivenessMonitorStarter : ApplicationInitializedListener {
  override suspend fun execute(asyncScope: CoroutineScope) {
    service<JVMResponsivenessMonitor>()
  }
}
