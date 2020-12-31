// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("EXPERIMENTAL_API_USAGE")

package com.intellij.execution.process.mediator.daemon

import com.google.protobuf.Empty
import com.intellij.execution.process.mediator.grpc.CredentialsAuthServerInterceptor
import com.intellij.execution.process.mediator.grpc.ExceptionAsStatus
import com.intellij.execution.process.mediator.handshake.HandshakeFileWriter
import com.intellij.execution.process.mediator.handshake.HandshakeSocketWriter
import com.intellij.execution.process.mediator.handshake.HandshakeStreamWriter
import com.intellij.execution.process.mediator.handshake.HandshakeWriter
import com.intellij.execution.process.mediator.rpc.AdjustQuotaRequest
import com.intellij.execution.process.mediator.rpc.DaemonGrpcKt
import com.intellij.execution.process.mediator.rpc.Handshake
import com.intellij.execution.process.mediator.util.MachUtil
import com.intellij.execution.process.mediator.util.UnixUtil
import com.intellij.execution.process.mediator.util.rsaEncrypt
import io.grpc.Server
import io.grpc.ServerBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.plus
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.system.exitProcess


@Suppress("LeakingThis")
open class ProcessMediatorServerDaemon(coroutineScope: CoroutineScope,
                                       builder: ServerBuilder<*>,
                                       credentials: DaemonClientCredentials) : ProcessMediatorDaemon,
                                                                               CoroutineScope by coroutineScope {

  private val quotaManager = TimeQuotaManager(this)
  private val processManager = ProcessManager(this + quotaManager.asJob())

  private val server: Server

  val port get() = server.port

  init {
    this.server = builder
      .intercept(CredentialsAuthServerInterceptor(credentials))
      .addService(ProcessManagerServerService.createServiceDefinition(processManager, quotaManager))
      .addService(DaemonService())
      .build()
      .start()
      .also {
        System.err.println("Started server on port ${it.port}")
      }
  }

  override fun stop() {
    requestShutdown()
  }

  fun requestShutdown(): Unit = synchronized(server) {
    if (!server.isShutdown) {
      System.err.println("Server shutdown requested")
      quotaManager.use {  // to close it
        processManager.use {
          server.shutdown()
        }
      }
    }
    else {
      System.err.println("Server shutdown requested, but it's already been shut down")
    }
  }

  override fun blockUntilShutdown() {
    server.awaitTermination()
  }

  inner class DaemonService : DaemonGrpcKt.DaemonCoroutineImplBase() {
    override suspend fun adjustQuota(request: AdjustQuotaRequest): Empty {
      ExceptionAsStatus.wrap {
        val quotaOptions = QuotaOptions(timeLimitMs = request.timeLimitMs,
                                        isRefreshable = request.isRefreshable)
        quotaManager.adjustQuota(quotaOptions)
      }
      return Empty.getDefaultInstance()
    }

    override suspend fun shutdown(request: Empty): Empty {
      ExceptionAsStatus.wrap {
        requestShutdown()
      }
      return Empty.getDefaultInstance()
    }
  }
}

private fun createDaemonProcessCommandLine(vararg args: String): ProcessBuilder {
  return ProcessBuilder(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java",
                        *ProcessMediatorDaemonRuntimeClasspath.getProperties().map { (k, v) -> "-D$k=$v" }.toTypedArray(),
                        "-cp", System.getProperty("java.class.path"),
                        ProcessMediatorDaemonRuntimeClasspath.getMainClass().name,
                        *args)
}

private fun trampoline(launchOptions: DaemonLaunchOptions): Nothing {
  openHandshakeWriter(launchOptions.handshakeOption).use { handshakeWriter ->
    val daemonOptions = launchOptions.copy(trampoline = false,
                                           handshakeOption = DaemonLaunchOptions.HandshakeOption.Stdout)
    val daemonProcess = createDaemonProcessCommandLine(*daemonOptions.asCmdlineArgs().toTypedArray())
      .redirectOutput(ProcessBuilder.Redirect.PIPE)
      .redirectError(ProcessBuilder.Redirect.INHERIT)
      .start()
    System.err.println("[trampoline] Started daemon process PID ${daemonProcess.pid()}")

    daemonProcess.onExit().whenComplete { process, _ ->
      val exitCode = process.exitValue()
      System.err.println("[trampoline] Daemon process PID ${process.pid()} exited with code $exitCode before trampoline process")
      exitProcess(exitCode)
    }

    try {
      val handshake = daemonProcess.inputStream.use(Handshake::parseDelimitedFrom)
                      ?: throw IOException("Premature EOF while reading handshake")

      handshakeWriter.writeHandshake(handshake)
    }
    catch (e: Throwable) {
      if (e is IOException) System.err.println("[trampoline] Unable to relay handshake: ${e.message}")
      daemonProcess.run {
        waitFor(3, TimeUnit.SECONDS)
        destroy()
        kotlin.runCatching { waitFor(10, TimeUnit.SECONDS) }
        destroyForcibly()
      }
      throw e
    }
  }
  exitProcess(0)
}

private fun openHandshakeWriter(handshakeOption: DaemonLaunchOptions.HandshakeOption?): HandshakeWriter? =
  when (handshakeOption) {
    null -> null
    DaemonLaunchOptions.HandshakeOption.Stdout -> HandshakeStreamWriter(System.out)
    is DaemonLaunchOptions.HandshakeOption.File -> HandshakeFileWriter(handshakeOption.path)
    is DaemonLaunchOptions.HandshakeOption.Port -> HandshakeSocketWriter(handshakeOption.port)
  }

private fun HandshakeWriter?.writeHandshake(handshake: Handshake) {
  this?.write(handshake::writeDelimitedTo) ?: println(handshake)
}

// the order matters
@Suppress("unused")
enum class ExitCode {
  SUCCESS,
  GENERAL_ERROR,
  LEADER_EXITED,
}

fun main(args: Array<String>) {
  val launchOptions = DaemonLaunchOptions.parseFromArgsOrDie("ProcessMediatorDaemonMain", args)

  val leaderProcessHandle = launchOptions.leaderPid?.let { leaderPid ->
    ProcessHandle.of(leaderPid).orElse(null) ?: run {
      System.err.println("Leader process with PID $leaderPid not found, exiting immediately")
      exitProcess(ExitCode.LEADER_EXITED.ordinal)
    }
  }

  if (launchOptions.trampoline) {
    trampoline(launchOptions)  // never returns
  }
  if (UnixUtil.isUnix()) {
    UnixUtil.setup(launchOptions.daemonize)
    if (MachUtil.isMac()) {
      MachUtil.setup(launchOptions.machNamespaceUid)
    }
  }

  val daemon: ProcessMediatorServerDaemon

  openHandshakeWriter(launchOptions.handshakeOption).use { handshakeWriter ->
    val coroutineScope = CoroutineScope(EmptyCoroutineContext)
    val credentials = DaemonClientCredentials.generate()
    daemon = ProcessMediatorServerDaemon(coroutineScope, ServerBuilder.forPort(0), credentials)
    try {
      val token = credentials.token.let {
        when (val publicKey = launchOptions.tokenEncryptionOption?.publicKey) {
          null -> it
          else -> publicKey.rsaEncrypt(it)
        }
      }
      val handshake = Handshake.newBuilder()
        .setPort(daemon.port)
        .setToken(token)
        .setPid(ProcessHandle.current().pid())
        .build()

      handshakeWriter.writeHandshake(handshake)
    }
    catch (e: Throwable) {
      if (e is IOException) System.err.println("Unable to write handshake: ${e.message}")
      daemon.stop()
      throw e
    }
  }
  if (launchOptions.daemonize) {
    try {
      System.`in`.close()
    }
    catch (e: IOException) {
      System.err.println("Unable to close daemon stdin: " + e.message)
    }
    System.out.close()
    System.err.close()
  }

  Runtime.getRuntime().addShutdownHook(
    Thread {
      System.err.println("Shutting down gRPC server since JVM is shutting down")
      daemon.stop()
    }
  )
  leaderProcessHandle?.onExit()?.whenComplete { handle, _ ->
    System.err.println("Leader process with PID ${handle.pid()} exited, shutting down")
    exitProcess(ExitCode.LEADER_EXITED.ordinal)
  }
  daemon.blockUntilShutdown()
}
