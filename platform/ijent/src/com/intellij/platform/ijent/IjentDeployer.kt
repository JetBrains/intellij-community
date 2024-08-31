// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("IjentDeployer")
package com.intellij.platform.ijent

import com.intellij.platform.ijent.spi.DeployedIjent
import com.intellij.platform.ijent.spi.IjentDeployingStrategy
import com.intellij.platform.ijent.spi.connectToRunningIjent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job

/**
 * Starts IJent on some machine, defined by [com.intellij.platform.ijent.spi.IjentDeployingStrategy].
 *
 * [ijentName] is a label used for logging, debugging, thread names, etc.
 *
 * By default, the IJent executable exits when the IDE exits.
 * [com.intellij.platform.eel.IjentApi.close] and [com.intellij.platform.ijent.bindToScope] may be used to terminate IJent earlier.
 *
 * TODO Either define thrown exceptions or return something like Result.
 */
suspend fun IjentDeployingStrategy.deploy(ijentName: String): DeployedIjent {
  val (remotePathToBinary, ijentApi) = doDeploy(ijentName)
  return object : DeployedIjent {
    override val ijentApi: IjentApi = ijentApi
    override val remotePathToBinary: String = remotePathToBinary
  }
}

/** A specialized version of [com.intellij.platform.ijent.deploy] */
suspend fun IjentDeployingStrategy.Posix.deploy(ijentName: String): DeployedIjent.Posix {
  val (remotePathToBinary, ijentApi) = doDeploy(ijentName)
  ijentApi as IjentPosixApi
  return object : DeployedIjent.Posix {
    override val ijentApi: IjentPosixApi = ijentApi
    override val remotePathToBinary: String = remotePathToBinary
  }
}

/** A specialized version of [com.intellij.platform.ijent.deploy] */
suspend fun IjentDeployingStrategy.Windows.deploy(ijentName: String): DeployedIjent.Windows {
  val (remotePathToBinary, ijentApi) = doDeploy(ijentName)
  ijentApi as IjentWindowsApi
  return object : DeployedIjent.Windows {
    override val ijentApi: IjentWindowsApi = ijentApi
    override val remotePathToBinary: String = remotePathToBinary
  }
}

/** A shortcut for terminating an [IjentApi] when the [coroutineScope] completes. */
fun IjentApi.bindToScope(coroutineScope: CoroutineScope) {
  coroutineScope.coroutineContext.job.invokeOnCompletion {
    this@bindToScope.close()
  }
}

private suspend fun IjentDeployingStrategy.doDeploy(ijentName: String): Pair<String, IjentApi> =
  try {
    val targetPlatform = getTargetPlatform()
    val remotePathToBinary = copyFile(IjentExecFileProvider.getInstance().getIjentBinary(targetPlatform))
    val process = createProcess(remotePathToBinary)

    val ijentApi = connectToRunningIjent(ijentName, getConnectionStrategy(), targetPlatform, process)
    remotePathToBinary to ijentApi
  }
  finally {
    close()
  }
