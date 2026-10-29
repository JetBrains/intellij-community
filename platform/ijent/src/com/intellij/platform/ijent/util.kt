// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent

import com.intellij.platform.util.annotations.VisibleToClasses
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext

@OptIn(ExperimentalStdlibApi::class)
@VisibleToClasses(
  "com.intellij.docker.ijent.DockerIjentDeployingStrategy",
  "com.intellij.kubernetes.ijent.KubernetesPodIjentEelMachine",
  "com.intellij.platform.ide.impl.wsl.ProductionWslIjentManager",
  "com.intellij.platform.ide.impl.wsl.WslIjentUtil",
  "com.intellij.platform.ijent.ssh.impl.ijentOverSshJ.SshOverShellDeployKt",
  "com.intellij.platform.ijent.testFramework.functional.fixture.IjentWslFixture",
)
suspend fun currentCoroutineDispatcher(): CoroutineDispatcher {
  return currentCoroutineContext()[CoroutineDispatcher] ?: Dispatchers.Default
}

@OptIn(ExperimentalStdlibApi::class)
@VisibleToClasses(
  "com.intellij.platform.ijent.community.ui.actions.IjentDockerVerificationAction",
  "com.intellij.platform.ijent.functional.DockerBootstrapOverShellSessionTest",
  "com.intellij.platform.ijent.functional.IjentLogTest",
  "com.intellij.platform.ijent.functional.MacosBootstrapOverShellSessionTest",
  "com.intellij.platform.ijent.wsl.IjentWslVerificationAction",
  "com.jetbrains.rider.utils.threading.AsyncItemsProcessor",
)
fun CoroutineScope.coroutineDispatcher(): CoroutineDispatcher = this.coroutineContext[CoroutineDispatcher] ?: Dispatchers.Default