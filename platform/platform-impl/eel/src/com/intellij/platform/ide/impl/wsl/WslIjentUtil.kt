// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("WslIjentUtil")
@file:Suppress("RAW_RUN_BLOCKING")  // These functions are called by different legacy code, a ProgressIndicator is not always available.
@file:ApiStatus.Internal

package com.intellij.platform.ide.impl.wsl

import com.intellij.execution.wsl.WSLCommandLineOptions
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.openapi.project.Project
import com.intellij.platform.ijent.IjentPosixApi
import com.intellij.platform.ijent.IjentSession
import com.intellij.platform.ijent.createIjentSession
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
suspend fun WSLDistribution.createIjentSession(
  parentScope: CoroutineScope,
  project: Project?,
  ijentLabel: String,
  wslCommandLineOptionsModifier: (WSLCommandLineOptions) -> Unit = {},
): IjentSession<IjentPosixApi> {
  return WslIjentDeployingStrategy(
    scope = parentScope,
    ijentLabel = ijentLabel,
    distribution = this,
    project = project,
    wslCommandLineOptionsModifier = wslCommandLineOptionsModifier
  ).createIjentSession()
}