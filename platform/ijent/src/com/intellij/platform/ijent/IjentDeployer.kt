// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("IjentDeployer")
package com.intellij.platform.ijent

import com.intellij.platform.ijent.spi.DeployedIjent
import com.intellij.platform.ijent.spi.IjentDeployingStrategy
import com.intellij.platform.ijent.spi.connectToRunningIjent

/**
 * Starts IJent on some machine, defined by [com.intellij.platform.ijent.spi.IjentDeployingStrategy].
 *
 * By default, the IJent executable exits when
 * the coroutine scope of [IjentProcessMediator] from [IjentDeployingStrategy.createProcess] exits.
 * [com.intellij.platform.ijent.IjentApi.close] may be used to terminate IJent earlier.
 *
 * TODO Either define thrown exceptions or return something like Result.
 */
suspend fun IjentDeployingStrategy.deploy(): DeployedIjent {
  val (remotePathToBinary, ijentApi) = doDeploy()
  return object : DeployedIjent {
    override val ijentApi: IjentApi = ijentApi
    override val remotePathToBinary: String = remotePathToBinary
  }
}

/** A specialized version of [com.intellij.platform.ijent.deploy] */
suspend fun IjentDeployingStrategy.Posix.deploy(): DeployedIjent.Posix {
  val (remotePathToBinary, ijentApi) = doDeploy()
  ijentApi as IjentPosixApi
  return object : DeployedIjent.Posix {
    override val ijentApi: IjentPosixApi = ijentApi
    override val remotePathToBinary: String = remotePathToBinary
  }
}

/** A specialized version of [com.intellij.platform.ijent.deploy] */
suspend fun IjentDeployingStrategy.Windows.deploy(): DeployedIjent.Windows {
  val (remotePathToBinary, ijentApi) = doDeploy()
  ijentApi as IjentWindowsApi
  return object : DeployedIjent.Windows {
    override val ijentApi: IjentWindowsApi = ijentApi
    override val remotePathToBinary: String = remotePathToBinary
  }
}

private suspend fun IjentDeployingStrategy.doDeploy(): Pair<String, IjentApi> =
  try {
    val targetPlatform = getTargetPlatform()
    val remotePathToBinary = copyFile(IjentExecFileProvider.getInstance().getIjentBinary(targetPlatform))
    val mediator = createProcess(remotePathToBinary)

    val ijentApi = connectToRunningIjent(getConnectionStrategy(), targetPlatform, mediator)
    remotePathToBinary to ijentApi
  }
  finally {
    close()
  }
