// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.mediator.launcher

import com.intellij.execution.process.mediator.client.ProcessMediatorClient
import com.intellij.execution.process.mediator.daemon.DaemonClientCredentials
import com.intellij.execution.process.mediator.daemon.ProcessMediatorServerDaemon
import com.intellij.util.io.MultiCloseable
import com.intellij.util.io.runClosingOnFailure
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.stub.MetadataUtils
import kotlinx.coroutines.CoroutineScope
import java.io.Closeable
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import kotlin.coroutines.EmptyCoroutineContext


interface ProcessMediatorConnection : Closeable {
  val client: ProcessMediatorClient

  companion object
}


private val LOOPBACK_IP = InetAddress.getLoopbackAddress().hostAddress

fun ProcessMediatorConnection.Companion.createDaemonConnection(
  processHandle: ProcessHandle?,
  port: Int,
  credentials: DaemonClientCredentials,
  clientBuilder: ProcessMediatorClient.Builder,
): ProcessMediatorConnection = MultiCloseable().runClosingOnFailure {
  // The order of registering matters! They will be closed in the LIFO order.
  processHandle?.let {
    registerCloseable { it.destroy() }
  }
  val channel = ManagedChannelBuilder.forAddress(LOOPBACK_IP, port)
    .usePlaintext()
    .build().also(::registerCloseable)

  val client = clientBuilder.createClient(channel, credentials).also(::registerCloseable)
  processHandle?.let {
    it.onExit()?.whenComplete { _, _ -> channel.shutdown() }
  }
  return ConnectionImpl(client, this)
}

fun ProcessMediatorConnection.Companion.startInProcessServer(
  coroutineScope: CoroutineScope = CoroutineScope(EmptyCoroutineContext),
  bindName: String = "testing",
  clientBuilder: ProcessMediatorClient.Builder = ProcessMediatorClient.Builder(coroutineScope),
): ProcessMediatorConnection = MultiCloseable().runClosingOnFailure {
  val credentials = DaemonClientCredentials.generate()
  val serverBuilder = InProcessServerBuilder.forName(bindName).directExecutor()
  // TODO close the channel on server shutdown
  ProcessMediatorServerDaemon(coroutineScope, serverBuilder, credentials).also(::registerCloseable)
  val channel = InProcessChannelBuilder.forName(bindName)
    .intercept(MetadataUtils.newAttachHeadersInterceptor(credentials.asMetadata()))
    .directExecutor()
    .build().also(::registerCloseable)

  val client = clientBuilder.createClient(channel, credentials).also(::registerCloseable)
  return ConnectionImpl(client, this)
}


private fun MultiCloseable.registerCloseable(channel: ManagedChannel) =
  registerCloseable { channel.shutdown().awaitTermination(5, TimeUnit.SECONDS) }

private class ConnectionImpl(
  override val client: ProcessMediatorClient,
  cleanup: AutoCloseable,
) : ProcessMediatorConnection, Closeable, AutoCloseable by cleanup {
  override fun toString(): String = "Connection(client=$client)"
}
