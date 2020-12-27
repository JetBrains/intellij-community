// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.process.mediator

import com.intellij.execution.process.mediator.daemon.DaemonClientCredentials
import com.intellij.execution.process.mediator.daemon.ProcessMediatorDaemon
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.stub.MetadataUtils
import java.net.InetAddress

private val LOOPBACK_IP = InetAddress.getLoopbackAddress().hostAddress

internal class ProcessMediatorDaemonImpl(private val processHandle: ProcessHandle?,
                                         private val port: Int,
                                         private val credentials: DaemonClientCredentials) : ProcessMediatorDaemon {

  override fun createChannel(): ManagedChannel {
    return ManagedChannelBuilder.forAddress(LOOPBACK_IP, port).usePlaintext()
      .intercept(MetadataUtils.newAttachHeadersInterceptor(credentials.asMetadata()))
      .build().also { channel ->
        processHandle?.onExit()?.whenComplete { _, _ -> channel.shutdown() }
      }
  }

  override fun stop() = Unit

  override fun blockUntilShutdown() {
    processHandle?.onExit()?.get()
  }
}
