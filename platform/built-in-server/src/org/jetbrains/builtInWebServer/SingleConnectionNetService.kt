package org.jetbrains.builtInWebServer

import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.project.Project
import com.intellij.util.Consumer
import com.intellij.util.net.NetUtils
import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.io.NettyUtil

import java.net.InetSocketAddress

abstract class SingleConnectionNetService(project: Project) : NetService(project) {
  protected @Volatile var processChannel: Channel? = null

  protected abstract fun configureBootstrap(bootstrap: Bootstrap, errorOutputConsumer: Consumer<String>)

  override fun connectToProcess(promise: AsyncPromise<OSProcessHandler>, port: Int, processHandler: OSProcessHandler, errorOutputConsumer: Consumer<String>) {
    val bootstrap = NettyUtil.oioClientBootstrap()
    configureBootstrap(bootstrap, errorOutputConsumer)
    NettyUtil.connect(bootstrap, InetSocketAddress(NetUtils.getLoopbackAddress(), port), promise)?.let {
      processChannel = it
      promise.setResult(processHandler)
    }
  }

  override fun closeProcessConnections() {
    val currentProcessChannel = processChannel
    if (currentProcessChannel != null) {
      processChannel = null
      NettyUtil.closeAndReleaseFactory(currentProcessChannel)
    }
  }
}