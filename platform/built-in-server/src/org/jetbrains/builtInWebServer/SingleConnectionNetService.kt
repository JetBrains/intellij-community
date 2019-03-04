// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.builtInWebServer

import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.project.Project
import com.intellij.util.Consumer
import com.intellij.util.io.*
import com.intellij.util.net.loopbackSocketAddress
import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.catchError
import org.jetbrains.concurrency.resolvedPromise
import org.jetbrains.io.NettyUtil.nioClientBootstrap
import java.util.concurrent.atomic.AtomicReference

abstract class SingleConnectionNetService(project: Project) : NetService(project) {
  protected val processChannel = AtomicReference<Channel>()

  @Volatile
  private var port = -1
  @Volatile
  private var bootstrap: Bootstrap? = null

  protected abstract fun configureBootstrap(bootstrap: Bootstrap, errorOutputConsumer: Consumer<String>)

  final override fun connectToProcess(promise: AsyncPromise<OSProcessHandler>, port: Int, processHandler: OSProcessHandler, errorOutputConsumer: Consumer<String>) {
    val bootstrap = nioClientBootstrap()
    configureBootstrap(bootstrap, errorOutputConsumer)

    this.bootstrap = bootstrap
    this.port = port

    val connectResult = bootstrap.connectRetrying(loopbackSocketAddress(port))
    connectResult.channel?.let {
      promise.catchError {
        processChannel.set(it)
        addCloseListener(it)
        promise.setResult(processHandler)
      }
    }
    handleErrors(connectResult, promise)
  }

  protected fun connectAgain(): Promise<Channel> {
    val channel = processChannel.get()
    if (channel != null) {
      return resolvedPromise(channel)
    }

    val promise = AsyncPromise<Channel>()
    val connectResult = bootstrap!!.connectRetrying(loopbackSocketAddress(port))
    connectResult.channel?.let {
      promise.catchError {
        processChannel.set(it)
        addCloseListener(it)
        promise.setResult(it)
      }
    }
    handleErrors(connectResult, promise)

    return promise
  }

  private fun handleErrors(connectResult: ConnectToChannelResult, promise: AsyncPromise<*>) {
    connectResult
      .handleError(java.util.function.Consumer {
        promise.setError(it)
      })
      .handleThrowable(java.util.function.Consumer {
        promise.setError(it)
      })
  }

  private fun addCloseListener(it: Channel) {
    it.closeFuture().addChannelListener {
      val channel = it.channel()
      processChannel.compareAndSet(channel, null)
      channel.eventLoop().shutdownIfOio()
    }
  }

  override fun closeProcessConnections() {
    processChannel.getAndSet(null)?.closeAndShutdownEventLoop()
  }
}