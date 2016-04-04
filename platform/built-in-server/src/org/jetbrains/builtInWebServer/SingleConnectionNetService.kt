package org.jetbrains.builtInWebServer

import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.project.Project
import com.intellij.util.Consumer
import com.intellij.util.net.loopbackSocketAddress
import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.catchError
import org.jetbrains.concurrency.resolvedPromise
import org.jetbrains.io.*
import java.util.concurrent.atomic.AtomicReference

abstract class SingleConnectionNetService(project: Project) : NetService(project) {
  protected val processChannel = AtomicReference<Channel>()

  private @Volatile var port = -1
  private @Volatile var bootstrap: Bootstrap? = null

  protected abstract fun configureBootstrap(bootstrap: Bootstrap, errorOutputConsumer: Consumer<String>)

  override final fun connectToProcess(promise: AsyncPromise<OSProcessHandler>, port: Int, processHandler: OSProcessHandler, errorOutputConsumer: Consumer<String>) {
    val bootstrap = oioClientBootstrap()
    configureBootstrap(bootstrap, errorOutputConsumer)

    this.bootstrap = bootstrap
    this.port = port

    bootstrap.connect(loopbackSocketAddress(port), promise)?.let {
      promise.catchError {
        processChannel.set(it)
        addCloseListener(it)
        promise.setResult(processHandler)
      }
    }
  }

  protected fun connectAgain(): Promise<Channel> {
    val channel = processChannel.get()
    if (channel != null) {
      return resolvedPromise(channel)
    }

    val promise = AsyncPromise<Channel>()
    bootstrap!!.connect(loopbackSocketAddress(port), promise)?.let {
      promise.catchError {
        processChannel.set(it)
        addCloseListener(it)
        promise.setResult(it)
      }
    }
    return promise
  }

  private fun addCloseListener(it: Channel) {
    it.closeFuture().addChannelListener {
      val channel = it.channel()
      processChannel.compareAndSet(channel, null)
      channel.eventLoop().shutdownIfOio()
    }
  }

  override fun closeProcessConnections() {
    processChannel.getAndSet(null)?.let { it.closeAndShutdownEventLoop() }
  }
}