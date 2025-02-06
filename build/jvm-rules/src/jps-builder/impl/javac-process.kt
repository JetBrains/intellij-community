@file:Suppress("UnstableApiUsage")

package org.jetbrains.bazel.jvm.jps.impl

import com.intellij.util.io.BaseOutputReader
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.Utils
import org.jetbrains.jps.javac.ExternalJavacManager
import org.jetbrains.jps.javac.ExternalJavacManagerKey
import org.jetbrains.jps.service.SharedThreadPool
import java.io.IOException
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.Future

@Synchronized
internal fun ensureJavacServerStarted(context: CompileContext): ExternalJavacManager {
  var server = ExternalJavacManagerKey.KEY.get(context)
  if (server != null) {
    return server
  }

  val listenPort = findFreePort()
  server = object : ExternalJavacManager(Utils.getSystemRoot(), SharedThreadPool.getInstance(), 2 * 60 * 1000L /*keep idle builds for 2 minutes*/) {
    override fun createProcessHandler(processId: UUID?, process: Process, commandLine: String, keepProcessAlive: Boolean): ExternalJavacProcessHandler {
      return object : ExternalJavacProcessHandler(processId, process, commandLine, keepProcessAlive) {
        override fun executeTask(task: Runnable): Future<*> {
          return SharedThreadPool.getInstance().submit(task)
        }

        override fun readerOptions(): BaseOutputReader.Options {
          return BaseOutputReader.Options.NON_BLOCKING
        }
      }
    }
  }
  server.start(listenPort)
  ExternalJavacManagerKey.KEY.set(context, server)
  return server
}
private fun findFreePort(): Int {
  try {
    val serverSocket = ServerSocket(0)
    try {
      return serverSocket.getLocalPort()
    }
    finally {
      //workaround for linux : calling close() immediately after opening socket
      //may result that socket is not closed
      synchronized(serverSocket) {
        try {
          (serverSocket as Object).wait(1)
        }
        catch (_: Throwable) {
        }
      }
      serverSocket.close()
    }
  }
  catch (e: IOException) {
    e.printStackTrace(System.err)
    return ExternalJavacManager.DEFAULT_SERVER_PORT
  }
}