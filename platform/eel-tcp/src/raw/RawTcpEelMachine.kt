// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.tcp.raw

import com.intellij.platform.eel.EelPlatform
import com.intellij.platform.eel.tcp.TcpEelMachine
import com.intellij.platform.ijent.spi.IjentConnectionContext
import com.intellij.platform.ijent.spi.IjentConnectionStrategy
import com.intellij.platform.ijent.spi.IjentTcpSessionMediator
import com.intellij.platform.ijent.tcp.IjentIsolatedTcpDeployingStrategy
import com.intellij.platform.ijent.tcp.TcpDeployInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope

class RawTcpEelMachine(
  private val deploy: TcpDeployInfo.FixedPort,
  private val coroutineScope: CoroutineScope,
) : TcpEelMachine(RawTcpConsts.internalName(deploy)) {
  override suspend fun createStrategy(): IjentIsolatedTcpDeployingStrategy {
    return object : IjentIsolatedTcpDeployingStrategy() {
      override suspend fun deploy(): IjentConnectionContext {
        return IjentConnectionContext(
          targetPlatform = EelPlatform.getFor("linux", "x86-64")!!, // TODO,
          remoteBinaryPath = "/it/doesnt/matter/its/dev/version",
          connectionStrategy = IjentConnectionStrategy.Tcp(deploy, null),
          mediator = IjentTcpSessionMediator(
            ijentProcessScope = coroutineScope,
            processExit = CompletableDeferred(),
            remotePid = CompletableDeferred(),
          )
        )
      }
    }
  }
}