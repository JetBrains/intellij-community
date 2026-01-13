// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.tcp

import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelMachine
import com.intellij.platform.ijent.IjentApi
import com.intellij.platform.ijent.IjentSession
import com.intellij.platform.ijent.tcp.IjentIsolatedTcpDeployingStrategy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

enum class TcpEelDeployingMode {
  Greedy,
  Lazy,
}

abstract class TcpEelMachine(
  override val internalName: String,
  val coroutineScope: CoroutineScope,
  val strategyFactory: () -> IjentIsolatedTcpDeployingStrategy,
) : EelMachine {
  private val deferredEelSession = CompletableDeferred<IjentSession<IjentApi>>()

  @VisibleForTesting
  val deployingState: AtomicBoolean = AtomicBoolean(false)
  override suspend fun toEelApi(descriptor: EelDescriptor): EelApi {
    val session = deferredEelSession.await()
    /** Do not need additional cache, since the current implementation of [IjentSession.getIjentInstance] doing caching */
    return session.getIjentInstance(descriptor)
  }

  internal suspend fun waitForDeployment() {
    deferredEelSession.await()
  }

  /**
   * Start deployment of the Ijent instance to the remote host. The deployment is start immediately on the background to speed up the process.
   * Unfortunately, if the session is deployed, but no usages are made to the underlying file system, the deployment will not be stopped
   *
   * That means that there could be possible an indefinite (as persistance is implemented and enabled) number of running Ijent instances to the different remote machines.
   */
  @ApiStatus.Internal
  fun deploy() {
    if (deployingState.compareAndSet(false, true)) {
      coroutineScope.async {
        try {
          deferredEelSession.complete(strategyFactory().createIjentSession())
        }
        catch (e: Throwable) {
          deferredEelSession.completeExceptionally(e)

          if (e is CancellationException) throw e
        }
      }
    }
  }

  override fun ownsPath(path: Path): Boolean {
    val internalName = TcpEelPathParser.extractInternalMachineId(path) ?: return false
    return internalName == this.internalName
  }
}
