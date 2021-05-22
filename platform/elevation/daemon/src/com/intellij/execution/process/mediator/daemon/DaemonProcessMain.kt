// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("EXPERIMENTAL_API_USAGE")

package com.intellij.execution.process.mediator.daemon

import com.intellij.execution.process.mediator.rpc.Handshake
import com.intellij.execution.process.mediator.util.MachUtil
import com.intellij.execution.process.mediator.util.UnixUtil
import com.intellij.execution.process.mediator.util.rsaEncrypt
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder
import kotlinx.coroutines.CoroutineScope
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.InetAddress
import java.net.Socket
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.concurrent.TimeUnit
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.system.exitProcess


private fun createDaemonProcessCommandLine(vararg args: String): ProcessBuilder {
  return ProcessBuilder(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java",
                        *DaemonProcessRuntimeClasspath.getProperties().map { (k, v) -> "-D$k=$v" }.toTypedArray(),
                        "-cp", System.getProperty("java.class.path"),
                        DaemonProcessRuntimeClasspath.getMainClass().name,
                        *args)
}

private fun trampoline(launchOptions: DaemonLaunchOptions): Nothing {
  openHandshakeOutputStream(launchOptions.handshakeOption).use { handshakeWriter ->
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

      writeHandshake(handshakeWriter, handshake)
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

private fun openHandshakeOutputStream(handshakeOption: DaemonLaunchOptions.HandshakeOption?): OutputStream? =
  when (handshakeOption) {
    null -> null
    DaemonLaunchOptions.HandshakeOption.Stdout -> System.out
    is DaemonLaunchOptions.HandshakeOption.File -> Files.newOutputStream(handshakeOption.path, StandardOpenOption.WRITE)
    is DaemonLaunchOptions.HandshakeOption.Port -> Socket(InetAddress.getLoopbackAddress(), handshakeOption.port).getOutputStream()
  }

private fun writeHandshake(outputStream: OutputStream?, handshake: Handshake) {
  outputStream?.let { handshake.writeDelimitedTo(it) } ?: println(handshake)
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

  openHandshakeOutputStream(launchOptions.handshakeOption).use { handshakeWriter ->
    val coroutineScope = CoroutineScope(EmptyCoroutineContext)
    val credentials = DaemonClientCredentials.generate()
    val serverBuilder = NettyServerBuilder.forPort(0)
      .permitKeepAliveTime(10, TimeUnit.SECONDS)
      .keepAliveTime(90, TimeUnit.SECONDS)
      .keepAliveTimeout(90, TimeUnit.SECONDS)
    daemon = ProcessMediatorServerDaemon(coroutineScope, serverBuilder, credentials)
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

      writeHandshake(handshakeWriter, handshake)
    }
    catch (e: Throwable) {
      if (e is IOException) System.err.println("Unable to write handshake: ${e.message}")
      daemon.requestShutdown()
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
      daemon.requestShutdown()
    }
  )
  leaderProcessHandle?.onExit()?.whenComplete { handle, _ ->
    System.err.println("Leader process with PID ${handle.pid()} exited, shutting down")
    exitProcess(ExitCode.LEADER_EXITED.ordinal)
  }
  daemon.blockUntilShutdown()
}
