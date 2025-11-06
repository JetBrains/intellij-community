// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.tcp

import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelMachine
import com.intellij.platform.ijent.IjentApi
import com.intellij.platform.ijent.IjentSession
import com.intellij.platform.ijent.tcp.IjentIsolatedTcpDeployingStrategy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.absolutePathString

enum class TcpEelDeployingMode {
  Greedy,
  Lazy,
}

class TcpEelMachine(
  val host: String, /* FIXME: Use only resolved hosts? */
  val coroutineScope: CoroutineScope,
  val deployingMode: TcpEelDeployingMode = TcpEelDeployingMode.Greedy,
) : EelMachine {
  override val internalName: String = "tcp-$host"
  private val deferredEelSession = CompletableDeferred<IjentSession<IjentApi>>()
  override suspend fun toEelApi(descriptor: EelDescriptor): EelApi {
    val session = deferredEelSession.await()
    /** Do not need additional cache, since the current implementation of [IjentSession.getIjentInstance] doing caching */
    return session.getIjentInstance(descriptor)
  }

  /**
   * Start deployment of the Ijent instance to the remote host. The deployment is start immediately on the background to speed up the process.
   * Unfortunately, if the session is deployed, but no usages are made to the underlying file system, the deployment will not be stopped
   *
   * That means that there could be possible an indefinite (as persistance is implemented and enabled) number of running Ijent instances to the different remote machines.
   */
  @ApiStatus.Internal
  fun deploy(strategy: IjentIsolatedTcpDeployingStrategy) {
    val coroutineStart = when (deployingMode) {
      TcpEelDeployingMode.Greedy -> CoroutineStart.DEFAULT
      TcpEelDeployingMode.Lazy -> CoroutineStart.LAZY
    }

    coroutineScope.async(start = coroutineStart) {
      deferredEelSession.complete(strategy.createIjentSession())
    }
  }

  override fun ownsPath(path: Path): Boolean {
    val extractedEndpoint = path.absolutePathString().extractTcpEndpoint() ?: return false
    return extractedEndpoint.host == host
  }
}
