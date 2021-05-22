// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine

import com.intellij.execution.configurations.RemoteConnection
import org.jetbrains.annotations.ApiStatus

/**
 * Provide instance of this class to RemoteState in order to perform deferred attaching to virtual machine.
 */
@ApiStatus.Internal
class DelayedRemoteConnection(useSockets: Boolean, hostName: String, address: String, serverMode: Boolean) :
  RemoteConnection(useSockets, hostName, address, serverMode) {
  /**
   * Must be invoked on EDT.
   */
  var attachRunnable: Runnable? = null
}