// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.spi

import com.intellij.execution.CommandLineUtil.posixQuote
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.platform.eel.EelPlatform
import com.intellij.platform.ijent.IjentUnavailableException
import com.intellij.platform.ijent.getIjentGrpcArgv
import com.intellij.util.io.copyToAsync
import kotlinx.coroutines.*
import org.jetbrains.annotations.VisibleForTesting
import java.io.InputStream
import java.nio.file.Path
import kotlin.io.path.fileSize
import kotlin.io.path.inputStream
import kotlin.time.Duration.Companion.seconds

abstract class IjentDeployingOverShellProcessStrategy(scope: CoroutineScope) : IjentDeployingStrategy.Posix {
  protected abstract val ijentLabel: String

  /**
   * If there's some bind mount, returns the path for the remote machine/container that corresponds to [path].
   * Otherwise, returns null.
   */
  protected abstract suspend fun mapPath(path: Path): String?

  protected abstract suspend fun createShellProcess(): Process

  private val myContext: Deferred<DeployingContextAndShell> = run {
    var createdShellProcess: ShellProcessWrapper? = null
    val context = scope.async(start = CoroutineStart.LAZY) {
      val shellProcess = ShellProcessWrapper(IjentSessionMediator.create(scope, createShellProcess(), ijentLabel))
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
    context
  }

  final override suspend fun getTargetPlatform(): EelPlatform.Posix {
    return myContext.await().execCommand {
      getTargetPlatform()
    }
  }

  final override suspend fun createProcess(binaryPath: String): IjentSessionMediator {
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

  override suspend fun getConnectionStrategy(): IjentConnectionStrategy = IjentConnectionStrategy.Default

  internal class ShellProcessWrapper(private var mediator: IjentSessionMediator?) {
    @OptIn(DelicateCoroutinesApi::class)
    suspend fun write(data: String) {
      val process = mediator!!.process

      @Suppress("NAME_SHADOWING")
      val data = if (data.endsWith("\n")) data else "$data\n"
      LOG.debug {
        val debugData = data.replace(Regex("\n\n+")) { "<\\n ${it.value.length} times>\n" }
        "Executing a script inside the shell: $debugData"
      }
      withContext(Dispatchers.IO) {
        process.outputStream.write(data.toByteArray())
        ensureActive()
        process.outputStream.flush()
        ensureActive()
      }
    }

    /** The same stdin and stdout will be used for transferring binary data. Some buffering wrapper may occasionally consume too much data. */
    suspend fun readLineWithoutBuffering(): String =
      withContext(Dispatchers.IO) {
        val buffer = StringBuilder()
        val stream = mediator!!.process.inputStream
        while (true) {
          ensureActive()
          val c = stream.read()
          if (c < 0 || c == '\n'.code) {
            break
          }
          buffer.append(c.toChar())
        }
        if (buffer.isNotEmpty()) {
          LOG.trace { "Read line from stdout: $buffer" }
        }
        buffer.toString()
      }

    suspend fun copyDataFrom(stream: InputStream) {
      val process = mediator!!.process
      withContext(Dispatchers.IO) {
        stream.copyToAsync(process.outputStream)
        ensureActive()
        process.outputStream.flush()
      }
    }

    @OptIn(InternalCoroutinesApi::class)
    suspend fun destroyForciblyAndGetError(): Throwable {
      mediator!!.process.destroyForcibly()
      try {
        val job = mediator!!.ijentProcessScope.coroutineContext.job
        job.join()
        throw job.getCancellationException()
      }
      catch (err: Throwable) {
        return IjentUnavailableException.unwrapFromCancellationExceptions(err)
      }
    }

    fun extractProcess(): IjentSessionMediator {
      val result = mediator!!
      mediator = null
      return result
    }
  }
}

private suspend fun <T : Any> DeployingContextAndShell.execCommand(block: suspend DeployingContextAndShell.() -> T): T {
  return try {
    block()
  }
  catch (initialErrorFromStack: Throwable) {
    val errorFromScope = process.destroyForciblyAndGetError()
    val errorFromStack = IjentUnavailableException.unwrapFromCancellationExceptions(initialErrorFromStack)

    // It happens sometimes that some valuable error is wrapped into CancellationException.
    // However, if there's no valuable error,
    // then it's a fair CancellationException that should be thrown futher, to cancel the context.
    val (mainError, suppressedError) =
      when (errorFromStack) {
        is IjentUnavailableException, is CancellationException -> errorFromStack to errorFromScope
        else -> errorFromScope to errorFromStack
      }

    if (mainError != suppressedError) {
      mainError.addSuppressed(suppressedError)
    }

    throw when (mainError) {
      is IjentUnavailableException, is CancellationException -> mainError
      else -> IjentStartupError.CommunicationError(mainError)
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
  val rm: String,
  val sed: String,
  val tail: String,
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
    val whichCmd = buildString {
      append("set +e; ")
      for (command in commands) {
        append("type $command 1>&2 && echo $command; ")
      }
      append("echo $done; set -e")
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
internal suspend fun createDeployingContext(filterAvailableBinariesCmd: suspend (commands: Collection<String>) -> Collection<String>): DeployingContext {
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
    "rm",
    "sed",
    "tail",
    "uname",
    "whoami",
  )
  val outputOfWhich = mutableListOf<String>()

  fun getCommandPath(name: String): String {
    assert(name in commands)
    return when {
      name in outputOfWhich -> name
      "busybox" in outputOfWhich -> "busybox $name"
      else -> throw IjentStartupError.IncompatibleTarget(setOf("busybox", name).joinToString(prefix = "The remote machine has none of: "))
    }
  }

  outputOfWhich += filterAvailableBinariesCmd(commands)

  return DeployingContext(
    chmod = getCommandPath("chmod"),
    cp = getCommandPath("cp"),
    cut = getCommandPath("cut"),
    env = getCommandPath("env"),
    getent = getCommandPath("getent"),
    head = getCommandPath("head"),
    mktemp = getCommandPath("mktemp"),
    rm = getCommandPath("rm"),
    sed = getCommandPath("sed"),
    tail = getCommandPath("tail"),
    uname = getCommandPath("uname"),
    whoami = getCommandPath("whoami"),
  )
}

private suspend fun DeployingContextAndShell.getTargetPlatform(): EelPlatform.Posix = run {
  // There are two arguments in `uname` that can show the process architecture: `-m` and `-p`. According to `man uname`, `-p` is more
  // verbose, and that information may be sufficient for choosing the right binary.
  // https://man.freebsd.org/cgi/man.cgi?query=uname&sektion=1
  process.write("${context.uname} -pm")

  val arch = process.readLineWithoutBuffering().split(" ").filterTo(linkedSetOf(), String::isNotEmpty)

  val targetPlatform = when {
    arch.isEmpty() -> throw IjentStartupError.IncompatibleTarget("Empty output of `uname`")
    "x86_64" in arch -> EelPlatform.X8664Linux
    "aarch64" in arch -> EelPlatform.Aarch64Linux
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

  process.write(context.run {
    "BINARY=\"\$($mktemp -d)/ijent\";\n"
  })

  if (ijentBinaryPreparedOnTarget != null) {
    process.write(context.run {
      "$cp ${posixQuote(ijentBinaryPreparedOnTarget)} \$BINARY;\n"
    })
  }
  else {
    process.write(context.run {
      "$head -c ${ijentBinarySize + BUGGY_DASH_BUFFER_FILLER.length} > \$BINARY.tmp;\n"
    })

    // Old versions of busybox with bundled problematic versions of dash don't handle arguments
    // like `-v` or `--version`.
    // While it's not easy to figure out if the workaround filler is actually required,
    // it's used with every shell. This also makes code a bit simpler.
    LOG.debug { "Writing workaround command for Dash (1 of 2)" }
    process.write(BUGGY_DASH_BUFFER_FILLER)
    LOG.debug { "Writing $ijentBinarySize bytes of IJent binary into the stream" }
    ijentBinaryOnLocalDisk.inputStream().use { stream ->
      process.copyDataFrom(stream)
    }
    LOG.debug { "Sent the IJent binary $ijentBinaryOnLocalDisk" }
    LOG.debug { "Writing workaround command for Dash (2 of 2)" }
    process.write(BUGGY_DASH_BUFFER_FILLER)

    // Now the file `$BINARY.tmp` contains the following content:
    // <\n * (random number in 0..filler_size)> + useful data + <\n + filler_size>
    // The script below extracts the useful data and puts it into `$BINARY`.
    // It wasn't checked if `LC_ALL` really needed for sed/head/tail, this variable
    // was overridden just in case.
    process.write(context.run {
      """
      | BYTES_TO_SKIP=${'$'}(LC_ALL=C $sed -e '/^${'$'}/d; =; q' ${'$'}BINARY.tmp | LC_ALL=C $head -n1);
      | LC_ALL=C $tail -c+${'$'}BYTES_TO_SKIP ${'$'}BINARY.tmp | LC_ALL=C $head -c ${ijentBinarySize} > ${'$'}BINARY;
      | $rm -f ${'$'}BINARY.tmp;
      """.trimMargin()
    })
  }

  process.write(context.run {
    "$chmod 500 \"\$BINARY\"; echo \"\$BINARY\";\n"
  })

  return process.readLineWithoutBuffering()
}

private suspend fun DeployingContextAndShell.execIjent(remotePathToBinary: String): IjentSessionMediator {
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

/**
 * [Dash-based shells up to 0.5.12 inclusively have a problem](https://lore.kernel.org/dash/CAMQsgbSZnEac=ETYnR6a_ysnAysaHThwY03pnoDxC=p5FqtAag@mail.gmail.com/).
 *
 * [According to IEEE Std 1003.1-2024](https://pubs.opengroup.org/onlinepubs/9799919799/utilities/sh.html#tag_20_110_06),
 * `sh` must read user input byte by byte and execute commands
 * as soon as a valid expression can be constructed right after reading a byte.
 * In contrast, Dash used to read ahead user input into a buffer with the size of `BUFSIZ`.
 * It broke our workflow of writing binary data right after executing the command for reading binary data.
 *
 * [The fix was committed at the beginning of 2023](https://git.kernel.org/pub/scm/utils/dash/dash.git/commit/?id=5f094d08c5bcee876191404a4f3dd2d075571215),
 * so we expect a lot of problematic shell versions in the wild.
 *
 * [GlibC defines BUFSIZ as 8192](https://sourceware.org/git/?p=glibc.git;a=blob;f=libio/stdio.h;h=da9d4eebcf013f1bf4fa11accf14e391c6029aff;hb=HEAD#l100),
 * [musl defines it as an even smaller constant](http://git.musl-libc.org/cgit/musl/tree/include/stdio.h).
 * Although there can be some systems with greater `BUFSIZ`,
 * we see the situation of compiling a shell with a problematic version and increased global buffer as improbable.
 */
private val BUGGY_DASH_BUFFER_FILLER: String get() = "\n".repeat(8192)
private val LOG = logger<IjentDeployingOverShellProcessStrategy>()