// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("WslIjentJavaUtil")
@file:Suppress("RAW_RUN_BLOCKING")  // These functions are called by different legacy code, a ProgressIndicator is not always available.
package com.intellij.execution.wsl

import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import kotlinx.coroutines.runBlocking

/**
 * An adapter for [com.intellij.platform.ijent.IjentExecApi.fetchLoginShellEnvVariables] for Java.
 */
@RequiresBackgroundThread
@RequiresBlockingContext
fun fetchLoginShellEnv(
  wslIjentManager: WslIjentManager,
  wslDistribution: WSLDistribution,
  project: Project?,
  rootUser: Boolean,
): Map<String, String> =
  runBlocking {
    wslIjentManager.getIjentApi(wslDistribution, project, rootUser).exec.fetchLoginShellEnvVariables()
  }