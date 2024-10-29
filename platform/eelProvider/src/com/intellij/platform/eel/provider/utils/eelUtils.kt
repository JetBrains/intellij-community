// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.provider.utils

import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.platform.eel.EelExecApi

fun EelExecApi.fetchLoginShellEnvVariablesBlocking(): Map<String, String> {
  return runBlockingMaybeCancellable { fetchLoginShellEnvVariables() }
}