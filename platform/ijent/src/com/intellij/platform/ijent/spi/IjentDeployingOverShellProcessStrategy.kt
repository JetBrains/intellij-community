// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.spi

import com.intellij.execution.CommandLineUtil.posixQuote
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.*
import com.intellij.platform.ijent.IjentPlatform
import com.intellij.platform.ijent.getIjentGrpcArgv
import com.intellij.util.io.computeDetached
import com.intellij.util.io.copyToAsync
import kotlinx.coroutines.*
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.io.InputStream
import java.nio.file.Path
import kotlin.io.path.fileSize
import kotlin.io.path.inputStream
import kotlin.time.Duration.Companion.seconds

abstract class IjentDeployingOverShellProcessStrategy(scope: CoroutineScope) : IjentDeployingStrategy.Posix {
  /**
   * If there's some bind mount, returns the path for the remote machine/container that corresponds to [path].
   * Otherwise, returns null.
   */
  protected abstract suspend fun mapPath(path: Path): String?

  protected abstract suspend fun createShellProcess(): ShellProcessWrapper

  private val myContext: Deferred<DeployingContextAndShell> = run {
    var createdShellProcess: ShellProcessWrapper? = null
    val context = scope.async(start = CoroutineStart.LAZY) {
      val shellProcess = createShellProcess()
      createdShellProcess = shellProcess
      createDeployingContext(shellProcess.apply {
        // The timeout is taken at random.
        withTimeout(10.seconds) {
          write("set -ex")
          ensureActive()
          filterOutBanners()
        }
      })
    }
    context.invokeOnCompletion { error ->
      if (error != null && error !is CancellationException) {
        createdShellProcess?.destroyForcibly()
      }
    }
    context
  }

  final override suspend fun getTargetPlatform(): IjentPlatform.Posix {
    return myContext.await().execCommand {
      getTargetPlatform()
    }
  }

  final override suspend fun createProcess(binaryPath: String): Process {
    return myContext.await().execCommand {
      execIjent(binaryPath)
    }
  }

  final override suspend fun copyFile(file: Path): String {
    return myContext.await().execCommand {
      uploadIjentBinary(file, ::mapPath)
    }
  }

  final override fun close() {
    if (myContext.isActive) {
      myContext.cancel(CancellationException("Closed explicitly"))
    }
  }

  class ShellProcessWrapper(private var wrapped: Process?) {
    suspend fun write(data: String) {
      val wrapped = wrapped!!

      @Suppress("NAME_SHADOWING")
      val data = if (data.endsWith("\n")) data else "$data\n"
      LOG.debug { "Executing a script inside the shell: $data" }
      withContext(Dispatchers.IO) {
        wrapped.outputStream.write(data.toByteArray())
        ensureActive()
        wrapped.outputStream.flush()
        ensureActive()
      }
    }

    /** The same stdin and stdout will be used for transferring binary data. Some buffering wrapper may occasionally consume too much data. */
    suspend fun readLineWithoutBuffering(): String =
      withContext(Dispatchers.IO) {
        val buffer = StringBuilder()
        val stream = wrapped!!.inputStream
        while (true) {
          ensureActive()
          val c = stream.read()
          if (c < 0 || c == '\n'.code) {
            break
          }
          buffer.append(c.toChar())
        }
        LOG.trace { "Read line from stdout: $buffer" }
        buffer.toString()
      }

    suspend fun copyDataFrom(stream: InputStream) {
      val wrapped = wrapped!!
      withContext(Dispatchers.IO) {
        stream.copyToAsync(wrapped.outputStream)
        ensureActive()
        wrapped.outputStream.flush()
      }
    }

    fun destroyForcibly() {
      wrapped!!.destroyForcibly()
    }

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun readWholeErrorStream(): ByteArray =
      computeDetached { wrapped!!.errorStream.readAllBytes() }

    fun extractProcess(): Process {
      val result = wrapped!!
      wrapped = null
      return result
    }
  }
}

private suspend fun <T : Any> DeployingContextAndShell.execCommand(block: suspend DeployingContextAndShell.() -> T): T {
  return try {
    block()
  }
  catch (err: Throwable) {
    runCatching { process.destroyForcibly() }.exceptionOrNull()?.let(err::addSuppressed)

    val attachment = Attachment("stderr", String(process.readWholeErrorStream()))
    attachment.isIncluded = attachment.isIncluded or ApplicationManager.getApplication().isInternal

    val errorWithAttachments = RuntimeExceptionWithAttachments(err.message ?: "", err, attachment)

    // TODO Suppress RuntimeExceptionWithAttachments instead of wrapping when KT-66006 is resolved.
    //err.addSuppressed(RuntimeExceptionWithAttachments(
    //  "The error happened during handling $process",
    //  Attachment("stderr", stderr.toString()).apply { isIncluded = isIncluded or ApplicationManager.getApplication().isInternal },
    //))
    //throw err

    throw when (err) {
      is TimeoutCancellationException, is IOException -> IjentStartupError.CommunicationError(errorWithAttachments)
      else -> errorWithAttachments
    }
  }
}

private suspend fun IjentDeployingOverShellProcessStrategy.ShellProcessWrapper.filterOutBanners() {
  // The boundary is for skipping various banners, greeting messages, PS1, etc.
  val boundary = (0..31).joinToString("") { "abcdefghijklmnopqrstuvwxyz0123456789".random().toString() }
  write("echo $boundary")
  do {
    val line = readLineWithoutBuffering()
  }
  while (line != boundary)
}

private class DeployingContextAndShell(
  val process: IjentDeployingOverShellProcessStrategy.ShellProcessWrapper,
  val context: DeployingContext,
)

@VisibleForTesting
internal data class DeployingContext(
  val chmod: String,
  val cp: String,
  val cut: String,
  val env: String,
  val getent: String,
  val head: String,
  val mktemp: String,
  val uname: String,
  val whoami: String,
)

/**
 * There are distributions like rancher-desktop-data where /bin/busybox exists, but there are no symlinks to uname, head, etc.
 *
 * This tricky function checks if the necessary core utils exist and tries to substitute them with busybox otherwise.
 */
private suspend fun createDeployingContext(
  shellProcess: IjentDeployingOverShellProcessStrategy.ShellProcessWrapper,
): DeployingContextAndShell {
  val deployingContext = createDeployingContext { commands ->
    val outputOfWhich = mutableListOf<String>()

    val done = "done"
    val whichCmd = commands.joinToString(" ").let { joined ->
      "set +e; which $joined || /bin/busybox which $joined || /usr/bin/busybox which $joined; echo $done; set -e"
    }

    shellProcess.write(whichCmd)

    while (true) {
      val line = shellProcess.readLineWithoutBuffering()
      if (line == done) break
      outputOfWhich += line
    }

    outputOfWhich
  }
  return DeployingContextAndShell(shellProcess, deployingContext)
}

@VisibleForTesting
internal suspend fun createDeployingContext(runWhichCmd: suspend (commands: Collection<String>) -> Collection<String>): DeployingContext {
  var busybox: String? = null

  // This strange at first glance code helps reduce copy-paste errors.
  val commands: Set<String> = setOf(
    "busybox",
    "chmod",
    "cp",
    "cut",
    "env",
    "getent",
    "head",
    "mktemp",
    "uname",
    "whoami",
  )
  val outputOfWhich = mutableListOf<String>()

  fun getCommandPath(name: String): String {
    assert(name in commands)
    val directCandidate = outputOfWhich.firstOrNull { it.endsWith("/$name") }
    if (directCandidate != null) {
      return directCandidate
    }

    if (name != "busybox" && busybox != null) {
      return "$busybox $name"
    }

    throw IjentStartupError.IncompatibleTarget(setOf("busybox", name).joinToString(prefix = "The remote machine has none of: "))
  }

  outputOfWhich += runWhichCmd(commands)

  busybox = outputOfWhich.firstOrNull { it.endsWith("/busybox") }

  return DeployingContext(
    chmod = getCommandPath("chmod"),
    cp = getCommandPath("cp"),
    cut = getCommandPath("cut"),
    env = getCommandPath("env"),
    getent = getCommandPath("getent"),
    head = getCommandPath("head"),
    mktemp = getCommandPath("mktemp"),
    uname = getCommandPath("uname"),
    whoami = getCommandPath("whoami"),
  )
}

private suspend fun DeployingContextAndShell.getTargetPlatform(): IjentPlatform.Posix = run {
  // There are two arguments in `uname` that can show the process architecture: `-m` and `-p`. According to `man uname`, `-p` is more
  // verbose, and that information may be sufficient for choosing the right binary.
  // https://man.freebsd.org/cgi/man.cgi?query=uname&sektion=1
  process.write("${context.uname} -pm")

  val arch = process.readLineWithoutBuffering().split(" ").filterTo(linkedSetOf(), String::isNotEmpty)

  val targetPlatform = when {
    arch.isEmpty() -> throw IjentStartupError.IncompatibleTarget("Empty output of `uname`")
    "x86_64" in arch -> IjentPlatform.X8664Linux
    "aarch64" in arch -> IjentPlatform.Aarch64Linux
    else -> throw IjentStartupError.IncompatibleTarget("No binary for architecture $arch")
  }
  return targetPlatform
}

private suspend fun DeployingContextAndShell.uploadIjentBinary(
  ijentBinaryOnLocalDisk: Path,
  pathMapper: suspend (Path) -> String?,
): String {
  // TODO Don't upload a new binary every time if the binary is already on the server. However, hashes must be checked.
  val ijentBinarySize = ijentBinaryOnLocalDisk.fileSize()

  val ijentBinaryPreparedOnTarget = pathMapper(ijentBinaryOnLocalDisk)

  val script = context.run {
    val ijentPathUploadScript =
      pathMapper(ijentBinaryOnLocalDisk)
        ?.let { "$cp ${posixQuote(it)} \$BINARY" }
      ?: run {
        "LC_ALL=C $head -c $ijentBinarySize > \$BINARY"
      }

    "BINARY=\"$($mktemp -d)/ijent\" ; $ijentPathUploadScript ; $chmod 500 \"\$BINARY\" ; echo \"\$BINARY\" "
  }

  process.write(script)

  if (ijentBinaryPreparedOnTarget == null) {
    LOG.debug { "Writing $ijentBinarySize bytes of IJent binary into the stream" }
    ijentBinaryOnLocalDisk.inputStream().use { stream ->
      process.copyDataFrom(stream)
    }
    LOG.debug { "Sent the IJent binary $ijentBinaryOnLocalDisk" }
  }

  return process.readLineWithoutBuffering()
}

private suspend fun DeployingContextAndShell.execIjent(remotePathToBinary: String): Process {
  val joinedCmd = getIjentGrpcArgv(remotePathToBinary, selfDeleteOnExit = true, usrBinEnv = context.env).joinToString(" ")
  val commandLineArgs = context.run {
    """
    | cd ${posixQuote(remotePathToBinary.substringBeforeLast('/'))};
    | export SHELL="${'$'}($getent passwd "${'$'}($whoami)" | $cut -d: -f7)";
    | if [ -z "${'$'}SHELL" ]; then export SHELL='/bin/sh' ; fi;
    | exec "${'$'}SHELL" -c ${posixQuote(joinedCmd)}
    """.trimMargin()
  }
  process.write(commandLineArgs)
  return process.extractProcess()
}

private val LOG = logger<IjentDeployingOverShellProcessStrategy>()