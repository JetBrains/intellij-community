// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UnstableApiUsage", "SSBasedInspection")

package org.jetbrains.bazel.jvm.worker.java

import com.intellij.util.io.BaseOutputReader
import io.opentelemetry.api.trace.Span
import org.jetbrains.jps.api.GlobalOptions
import org.jetbrains.jps.builders.JpsBuildBundle
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.Utils
import org.jetbrains.jps.javac.ExternalJavacManager
import org.jetbrains.jps.javac.ExternalJavacManagerKey
import org.jetbrains.jps.javac.ExternalJavacProcess
import org.jetbrains.jps.javac.PlainMessageDiagnostic
import org.jetbrains.jps.model.java.JpsJavaSdkType
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.service.SharedThreadPool
import java.io.IOException
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.Future
import javax.tools.Diagnostic
import javax.tools.DiagnosticListener
import javax.tools.JavaFileObject

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

internal fun getForkedJavacSdk(
  diagnostic: DiagnosticListener<JavaFileObject>,
  module: JpsModule,
  targetLanguageLevel: Int,
  span: Span,
): Pair<String, Int>? {
  val associatedSdk = getAssociatedSdk(module)
  var canRunAssociatedJavac = false
  if (associatedSdk != null) {
    val sdkVersion = associatedSdk.second
    canRunAssociatedJavac = sdkVersion >= ExternalJavacProcess.MINIMUM_REQUIRED_JAVA_VERSION
    if (isTargetReleaseSupported(sdkVersion, targetLanguageLevel)) {
      if (canRunAssociatedJavac) {
        return Pair(associatedSdk.first.homePath, sdkVersion)
      }
    }
    else {
      span.addEvent("""Target bytecode version $targetLanguageLevel is not supported by SDK $sdkVersion associated with module ${module.name}""")
    }
  }

  val fallbackJdkHome = System.getProperty(GlobalOptions.FALLBACK_JDK_HOME, null)
  if (fallbackJdkHome == null) {
    span.addEvent("Fallback JDK is not specified. (See ${GlobalOptions.FALLBACK_JDK_HOME} option)")
  }
  val fallbackJdkVersion = System.getProperty(GlobalOptions.FALLBACK_JDK_VERSION, null)
  if (fallbackJdkVersion == null) {
    span.addEvent("Fallback JDK version is not specified. (See ${GlobalOptions.FALLBACK_JDK_VERSION} option)")
  }

  if (associatedSdk == null && (fallbackJdkHome == null || fallbackJdkVersion == null)) {
    diagnostic.report(PlainMessageDiagnostic(Diagnostic.Kind.ERROR, JpsBuildBundle.message("build.message.cannot.start.javac.process.for.0.unknown.jdk.home", module.name)))
    return null
  }

  // either associatedSdk or fallbackJdk is configured, but associatedSdk cannot be used
  if (fallbackJdkHome != null) {
    val fallbackVersion = JpsJavaSdkType.parseVersion(fallbackJdkVersion)
    if (isTargetReleaseSupported(fallbackVersion, targetLanguageLevel)) {
      if (fallbackVersion >= ExternalJavacProcess.MINIMUM_REQUIRED_JAVA_VERSION) {
        return Pair(fallbackJdkHome, fallbackVersion)
      }
      else {
        span.addEvent("Version string for fallback JDK is '" + fallbackJdkVersion + "' (recognized as version '" + fallbackVersion + "')." +
          " At least version " + ExternalJavacProcess.MINIMUM_REQUIRED_JAVA_VERSION + " is required to launch javac process.")
      }
    }
  }

  // at this point, fallbackJdk is not suitable either
  if (associatedSdk != null) {
    if (canRunAssociatedJavac) {
      // although target release is not supported, attempt to start javac, so that javac properly reports this error
      return Pair(associatedSdk.first.homePath, associatedSdk.second)
    }
    else {
      diagnostic.report(PlainMessageDiagnostic(Diagnostic.Kind.ERROR,
        JpsBuildBundle.message(
          "build.message.unsupported.javac.version",
          module.name,
          associatedSdk.second,
          ExternalJavacProcess.MINIMUM_REQUIRED_JAVA_VERSION,
          targetLanguageLevel
        )
      ))
    }
  }

  return null
}