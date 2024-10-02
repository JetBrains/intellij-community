// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution

import com.intellij.execution.configurations.RemoteConnection
import com.intellij.execution.target.TargetEnvironment
import com.intellij.execution.target.localPort

/**
 * Allows resolving the stored debugger connection configuration against [TargetEnvironment] and to obtain [RemoteConnection] with the
 * resolved connection parameters for the IDE.
 */
internal class TargetDebuggerConnection(
  private val remoteConnection: RemoteConnection,
  val debuggerPortRequest: TargetEnvironment.TargetPortBinding,
) {
  private var remoteConnectionResolved: Boolean = false

  fun resolveRemoteConnection(environment: TargetEnvironment) {
    val localPort = environment.targetPortBindings[debuggerPortRequest]?.localPort
    remoteConnection.apply {
      debuggerHostName = "localhost"
      debuggerAddress = localPort.toString()
    }
    remoteConnectionResolved = true
  }

  fun getResolvedRemoteConnection(): RemoteConnection {
    if (!remoteConnectionResolved) {
      throw IllegalStateException("The connection parameters to the debugger must be resolved with the target environment")
    }
    return remoteConnection
  }
}