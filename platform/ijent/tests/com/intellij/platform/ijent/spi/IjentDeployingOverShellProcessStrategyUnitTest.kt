// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.spi

import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelPlatform
import com.intellij.platform.eel.SafeDeferred
import com.intellij.platform.eel.channels.EelReceiveChannel
import com.intellij.platform.eel.channels.EelSendChannel
import com.intellij.platform.eel.channels.EelSendChannelException
import com.intellij.platform.eel.channels.PeekableEelReceiveChannel
import com.intellij.platform.eel.channels.peekable
import com.intellij.platform.eel.provider.utils.EelPipe
import com.intellij.platform.eel.provider.utils.lines
import com.intellij.platform.eel.provider.utils.sendWholeText
import com.intellij.platform.ijent.IjentApi
import com.intellij.platform.ijent.IjentEventBus
import com.intellij.platform.ijent.IjentExecFileProvider
import com.intellij.platform.ijent.IjentMissingBinary
import com.intellij.platform.ijent.IjentScope
import com.intellij.platform.ijent.IjentSession
import com.intellij.platform.ijent.IjentUnavailableException
import com.intellij.platform.ijent.ParentOfIjentScopes
import com.intellij.testFramework.LoggedErrorProcessorEnabler
import com.intellij.testFramework.common.timeoutRunBlocking
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.be
import io.kotest.matchers.collections.beIn
import io.kotest.matchers.shouldBe
import io.kotest.matchers.should
import io.kotest.matchers.string.include
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds

@Timeout(30)
class IjentDeployingOverShellProcessStrategyUnitTest {
  @Test
  @ExtendWith(LoggedErrorProcessorEnabler.DoNoRethrowErrors::class)
  fun `shell write failure is reported and the owned process is closed`(): Unit = timeoutRunBlocking(10.seconds) {
    val expectedFailure = IOException("test shell write failure")
    val parentScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val strategy = TestShellStrategy(parentScope, shellWriteFailure = expectedFailure)

    try {
      val error = shouldThrow<IjentUnavailableException.CommunicationFailure> {
        strategy.createIjentSession(failingProvider("Connection must not be attempted when shell initialization fails"))
      }
      error.cause.shouldBeInstanceOf<EelSendChannelException>().cause shouldBe expectedFailure

      strategy.shellProcess.destroyed.await()
      strategy.shellProcess.isAlive shouldBe false
    }
    finally {
      parentScope.cancel()
    }
  }

  @Test
  fun `shell command failure is not masked as cancellation during cleanup`(): Unit = timeoutRunBlocking(10.seconds) {
    val expectedFailure = IOException("test path mapping failure")
    val strategy = TestShellStrategy(this, pathMapper = { throw expectedFailure })

    val error = shouldThrow<IjentUnavailableException.CommunicationFailure> {
      strategy.createIjentSession(failingProvider("Connection must not be attempted when path mapping fails"))
    }
    error.cause shouldBe expectedFailure

    strategy.shellProcess.destroyed.await()
    strategy.shellProcess.isAlive shouldBe false
  }

  @Test
  fun `provider connection failure closes the shell process still owned by the deployer`(): Unit = timeoutRunBlocking(10.seconds) {
    val expectedFailure = IOException("protocol handshake failed")
    val parentScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val strategy = TestShellStrategy(
      parentScope,
      usePowerShell = true,
      pathMapper = { "C:\\remote\\ijent.exe" },
    )
    val provider = object : IjentSessionProvider {
      override suspend fun connect(deploymentResult: IjentConnectionContext): IjentSession = throw expectedFailure
    }

    try {
      shouldThrow<IOException> {
        strategy.createIjentSession(provider)
      } shouldBe expectedFailure

      strategy.shellProcess.destroyed.await()
      strategy.shellProcess.isAlive shouldBe false
    }
    finally {
      parentScope.cancel()
    }
  }

  @Test
  fun `cleanup failure is suppressed without masking the shell command failure`(): Unit = timeoutRunBlocking(10.seconds) {
    val expectedFailure = IOException("test path mapping failure")
    val cleanupFailure = IOException("test destroy failure")
    val strategy = TestShellStrategy(
      this,
      pathMapper = { throw expectedFailure },
      destroyFailure = cleanupFailure,
    )

    val error = shouldThrow<IjentUnavailableException.CommunicationFailure> {
      strategy.createIjentSession(failingProvider("Connection must not be attempted when path mapping fails"))
    }
    error.cause shouldBe expectedFailure
    expectedFailure.suppressed.single() shouldBe cleanupFailure
    strategy.shellProcess.destroyed.await()
  }

  @Test
  fun `cancelling deployment during shell command closes the owned shell process`(): Unit = timeoutRunBlocking(10.seconds) {
    val parentScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val strategy = TestShellStrategy(
      parentScope,
      blockPlatformProbe = true,
      binaryProvider = { _, _ -> error("Binary provider must not be reached while the platform probe is blocked") },
    )

    try {
      supervisorScope {
        val deployment = async {
          strategy.createIjentSession(failingProvider("Connection must not be attempted while the platform probe is blocked"))
        }
        strategy.shellCreated.await()
        strategy.shellProcess.platformProbeStarted.await()
        parentScope.cancel(CancellationException("Test cancellation during shell command"))

        shouldThrow<CancellationException> { deployment.await() }

        strategy.shellProcess.destroyed.await()
        strategy.shellProcess.isAlive shouldBe false
      }
    }
    finally {
      parentScope.cancel()
    }
  }

  @Test
  fun `binary provider failure after platform probe closes the owned shell process`(): Unit = timeoutRunBlocking(10.seconds) {
    val expectedFailure = IjentMissingBinary(EelPlatform.Linux(EelPlatform.Arch.X86_64), "test failure")
    val strategy = TestShellStrategy(this, binaryProvider = { platform, shellProcess ->
      shellProcess.platformProbed.isCompleted shouldBe true
      platform.shouldBeInstanceOf<EelPlatform.Linux>()
      (platform as EelPlatform.Linux).arch shouldBe EelPlatform.Arch.X86_64
      throw expectedFailure
    })

    shouldThrow<IjentMissingBinary> {
      strategy.createIjentSession(failingProvider("Connection must not be attempted when the binary provider fails"))
    } shouldBe expectedFailure

    strategy.shellProcess.platformProbed.isCompleted shouldBe true
    strategy.shellProcess.destroyed.await()
    strategy.shellProcess.isAlive shouldBe false
  }

  @Test
  fun `closing the deployer after process handoff does not close the session process`(): Unit = timeoutRunBlocking(10.seconds) {
    val remotePath = "C:\\remote\\ijent.exe"
    val strategy = TestShellStrategy(this, usePowerShell = true, pathMapper = { remotePath })
    val session = strategy.createIjentSession(strategy.successfulProvider(remotePath))

    strategy.closeStrategy()
    strategy.shellProcess.isAlive shouldBe true
    session.sessionCoroutineScope.s.isActive shouldBe true
    strategy.shellProcess.destroyed.isCompleted shouldBe false

    session.close()
    strategy.shellProcess.destroyed.await()
  }

  @Test
  fun `cancellation while publishing connection completion keeps process cleanup with deployer`(): Unit = timeoutRunBlocking(10.seconds) {
    val parentScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val remotePath = "C:\\remote\\ijent.exe"
    val strategy = TestShellStrategy(parentScope, usePowerShell = true, pathMapper = { remotePath })
    val providerConnected = CompletableDeferred<Unit>()
    val eventsCollector = launch(start = CoroutineStart.UNDISPATCHED) {
      IjentDeployingStrategy.deployEvents.collect { event ->
        if (event == IjentDeployingStrategy.DeployEvent.CONNECT_STARTED) {
          awaitCancellation()
        }
      }
    }

    try {
      val successfulProvider = strategy.successfulProvider(remotePath)
      val deployment = async {
        strategy.createIjentSession(object : IjentSessionProvider {
          override suspend fun connect(deploymentResult: IjentConnectionContext): IjentSession {
            val session = successfulProvider.connect(deploymentResult)
            providerConnected.complete(Unit)
            return session
          }
        })
      }
      providerConnected.await()
      yield()
      deployment.isCompleted shouldBe false
      deployment.cancelAndJoin()

      strategy.shellProcess.destroyed.await()
      strategy.shellProcess.isAlive shouldBe false
    }
    finally {
      eventsCollector.cancelAndJoin()
      parentScope.cancel()
    }
  }

  @Test
  fun `Windows target uses PowerShell and quotes the binary path`(): Unit = timeoutRunBlocking(10.seconds) {
    val remotePath = "C:\\temp\\Scarlet O'hara\\ijent.exe"
    val binaryPlatform = CompletableDeferred<EelPlatform>()
    val strategy = TestShellStrategy(
      this,
      usePowerShell = true,
      binaryProvider = { platform, _ ->
        binaryPlatform.complete(platform)
        Path.of("test-ijent.exe")
      },
      pathMapper = { remotePath },
    )
    val session = strategy.createIjentSession(strategy.successfulProvider(remotePath))

    val selectedPlatform = binaryPlatform.await()
    selectedPlatform.shouldBeInstanceOf<EelPlatform.Windows>()
    (selectedPlatform as EelPlatform.Windows).arch shouldBe EelPlatform.Arch.X86_64
    (session.platform === selectedPlatform) shouldBe true
    strategy.shellProcess.receivedCommands.any { it.startsWith("Write-Output '") } shouldBe true
    strategy.shellProcess.receivedCommands.none {
      it.startsWith("echo ") || "set -e" in it || "uname" in it || "type busybox" in it
    } shouldBe true
    strategy.shellProcess.receivedCommands.single {
      "& 'C:\\temp\\Scarlet O''hara\\ijent.exe' 'grpc-server' '--self-delete-on-exit'" in it
    }

    session.close()
    strategy.shellProcess.destroyed.await()
  }

  @Nested
  inner class `test createDeployingContext` {
    @Test
    fun `all commands with busybox`(): Unit = timeoutRunBlocking(10.seconds) {
      val context = createDeployingContext { commands ->
        commands
      }
      context should be(DeployingContext(
        chmod = "chmod",
        cp = "cp",
        cut = "cut",
        env = "env",
        head = "head",
        mktemp = "mktemp",
        rm = "rm",
        sed = "sed",
        tail = "tail",
        uname = "uname",
        whoami = "whoami",
        getent = "getent",
        id = "id",
      ))
    }

    @Test
    fun `all commands without busybox`(): Unit = timeoutRunBlocking(10.seconds) {
      val context = createDeployingContext { commands ->
        "busybox" should beIn(commands)
        (commands - "busybox")
      }
      context should be(DeployingContext(
        chmod = "chmod",
        cp = "cp",
        cut = "cut",
        env = "env",
        head = "head",
        mktemp = "mktemp",
        rm = "rm",
        sed = "sed",
        tail = "tail",
        uname = "uname",
        whoami = "whoami",
        getent = "getent",
        id = "id",
      ))
    }

    @Test
    fun `all commands without chmod`(): Unit = timeoutRunBlocking(10.seconds) {
      val context = createDeployingContext { commands ->
        "chmod" should beIn(commands)
        (commands - "chmod")
      }
      context should be(DeployingContext(
        chmod = "busybox chmod",
        cp = "cp",
        cut = "cut",
        env = "env",
        head = "head",
        mktemp = "mktemp",
        rm = "rm",
        sed = "sed",
        tail = "tail",
        uname = "uname",
        whoami = "whoami",
        getent = "getent",
        id = "id",
      ))
    }

    /** macOS has neither `getent` nor `busybox`, but the deployment must work there anyway. */
    @Test
    fun `all commands without getent and without busybox`(): Unit = timeoutRunBlocking(10.seconds) {
      val context = createDeployingContext { commands ->
        "busybox" should beIn(commands)
        "getent" should beIn(commands)
        commands - "busybox" - "getent"
      }
      context should be(DeployingContext(
        chmod = "chmod",
        cp = "cp",
        cut = "cut",
        env = "env",
        head = "head",
        mktemp = "mktemp",
        rm = "rm",
        sed = "sed",
        tail = "tail",
        uname = "uname",
        whoami = "whoami",
        getent = null,
        id = "id",
      ))
    }

    @Test
    fun `only busybox`(): Unit = timeoutRunBlocking(10.seconds) {
      val context = createDeployingContext { commands ->
        "busybox" should beIn(commands)
        listOf("busybox")
      }
      context should be(DeployingContext(
        chmod = "busybox chmod",
        cp = "busybox cp",
        cut = "busybox cut",
        env = "busybox env",
        head = "busybox head",
        mktemp = "busybox mktemp",
        rm = "busybox rm",
        sed = "busybox sed",
        tail = "busybox tail",
        uname = "busybox uname",
        whoami = "busybox whoami",
        getent = "busybox getent",
        id = "busybox id",
      ))
    }

    @Test
    fun `no chmod and no busybox`(): Unit = timeoutRunBlocking(10.seconds) {
      val errorAssertion = shouldThrow<IjentUnavailableException.CommunicationFailure> {
        createDeployingContext { commands ->
          "busybox" should beIn(commands)
          "chmod" should beIn(commands)
          commands - "busybox" - "chmod"
        }
      }
      errorAssertion.message should include("busybox")
    }
  }
}

private fun failingProvider(message: String): IjentSessionProvider = object : IjentSessionProvider {
  override suspend fun connect(deploymentResult: IjentConnectionContext): IjentSession = error(message)
}

private class TestShellStrategy(
  parentScope: CoroutineScope,
  private val blockPlatformProbe: Boolean = false,
  private val usePowerShell: Boolean = false,
  private val binaryProvider: suspend (EelPlatform, TestShellProcessFacade) -> Path = { _, _ -> Path.of("test-ijent") },
  private val pathMapper: suspend (Path) -> String? = { null },
  private val destroyFailure: Exception? = null,
  private val shellWriteFailure: IOException? = null,
) : IjentDeployingOverShellProcessStrategy(ParentOfIjentScopes(parentScope), Dispatchers.Default) {
  override val ijentLabel: String = "test shell"
  val shellCreated = CompletableDeferred<Unit>()
  lateinit var shellProcess: TestShellProcessFacade
    private set

  override val ijentExecFileProvider: IjentExecFileProvider = object : IjentExecFileProvider {
    override suspend fun getIjentBinary(targetPlatform: EelPlatform): Path = binaryProvider(targetPlatform, shellProcess)
  }

  override suspend fun getShellDialect(): ShellDialect =
    if (usePowerShell) ShellDialect.POWERSHELL else ShellDialect.POSIX

  override suspend fun mapPath(path: Path): String? = pathMapper(path)

  override suspend fun createShellProcessFacade(ijentProcessScope: IjentScope): IjentSessionProcessMediator.ProcessFacade =
    TestShellProcessFacade(ijentProcessScope, blockPlatformProbe, destroyFailure, shellWriteFailure).also {
      shellProcess = it
      shellCreated.complete(Unit)
    }

  fun successfulProvider(remoteBinaryPath: String): IjentSessionProvider = object : IjentSessionProvider {
    override suspend fun connect(deploymentResult: IjentConnectionContext): IjentSession = object : IjentSession {
      override val isRunning: Boolean get() = shellProcess.isAlive
      override val platform: EelPlatform = deploymentResult.targetPlatform
      override val remotePathToBinary: String = remoteBinaryPath

      @OptIn(DelicateCoroutinesApi::class)
      override val sessionCoroutineScope: IjentScope = deploymentResult.mediator.ijentProcessScope

      override suspend fun updateLogLevel() = Unit
      override suspend fun setParentProcessToWatch(pid: Long) = Unit
      override fun getIjentInstance(descriptor: EelDescriptor): IjentApi = error("Unused in this test")
      override val eventBus: IjentEventBus get() = error("Unused in this test")

      override fun close() {
        sessionCoroutineScope.s.cancel()
      }
    }
  }

  fun closeStrategy() {
    close()
  }
}

private class TestShellProcessFacade(
  ijentProcessScope: IjentScope,
  private val blockPlatformProbe: Boolean = false,
  private val destroyFailure: Exception? = null,
  shellWriteFailure: IOException? = null,
) : IjentSessionProcessMediator.ProcessFacade {
  private val stdinPipe = EelPipe("test shell stdin", prefersDirectBuffers = false)
  private val stdoutPipe = EelPipe("test shell stdout", prefersDirectBuffers = false)
  private val stderrPipe = EelPipe("test shell stderr", prefersDirectBuffers = false)
  private val exitCodeImpl = CompletableDeferred<Int>()
  private val alive = AtomicBoolean(true)

  val platformProbed = CompletableDeferred<Unit>()
  val platformProbeStarted = CompletableDeferred<Unit>()
  val destroyed = CompletableDeferred<Unit>()
  val receivedCommands = CopyOnWriteArrayList<String>()

  override val stdin: EelSendChannel = shellWriteFailure?.let { failure ->
    object : EelSendChannel by stdinPipe.sink {
      override suspend fun send(src: ByteBuffer) {
        throw EelSendChannelException(this, failure)
      }
    }
  } ?: stdinPipe.sink
  override val stdout: PeekableEelReceiveChannel = stdoutPipe.source.peekable()
  override val stderr: EelReceiveChannel = stderrPipe.source
  override val exitCode: SafeDeferred<Int> = SafeDeferred(exitCodeImpl)
  override val destroyIsGraceful: Boolean = true
  override val isAlive: Boolean get() = alive.get()

  init {
    ijentProcessScope.s.launch {
      stdinPipe.source.lines(StandardCharsets.UTF_8).collect { command ->
        respondTo(command)
      }
    }
  }

  override suspend fun destroyForcibly() {
    destroyFailure?.let { throw it }
    finish()
  }

  override suspend fun destroy() {
    finish()
  }

  private suspend fun respondTo(command: String) {
    receivedCommands += command
    val powerShellCommand = "Write-Output" in command
    val lineEnding = if (powerShellCommand) "\r\n" else "\n"
    val commandBoundary = (
      Regex("echo ([a-z0-9]{32})_START").find(command)
      ?: Regex("Write-Output '([a-z0-9]{32})_START'").find(command)
    )?.groupValues?.get(1)
    if (commandBoundary != null) {
      stdoutPipe.sink.sendWholeText("${commandBoundary}_START$lineEnding")
      when {
        "type busybox" in command -> AVAILABLE_SHELL_COMMANDS.forEach {
          stdoutPipe.sink.sendWholeText("$it$lineEnding")
        }
        "uname -spm" in command -> {
          platformProbeStarted.complete(Unit)
          if (blockPlatformProbe) return
          platformProbed.complete(Unit)
          stdoutPipe.sink.sendWholeText("Linux x86_64 x86_64$lineEnding")
        }
        "PROCESSOR_ARCHITECTURE" in command -> {
          platformProbed.complete(Unit)
          stdoutPipe.sink.sendWholeText("AMD64$lineEnding")
        }
      }
      stdoutPipe.sink.sendWholeText("${commandBoundary}_END$lineEnding")
      return
    }

    val processBoundary = (
      Regex("^echo ([a-z0-9]{32})(?:;|$)").find(command)
      ?: Regex("^Write-Output '([a-z0-9]{32})';").find(command)
    )?.groupValues?.get(1)
    if (processBoundary != null) stdoutPipe.sink.sendWholeText("$processBoundary$lineEnding")
  }

  private suspend fun finish() {
    if (!alive.compareAndSet(true, false)) return

    stdoutPipe.sink.close(null)
    stderrPipe.sink.close(null)
    exitCodeImpl.complete(0)
    destroyed.complete(Unit)
  }

  companion object {
    private val AVAILABLE_SHELL_COMMANDS = listOf(
      "busybox",
      "chmod",
      "cp",
      "cut",
      "env",
      "head",
      "mktemp",
      "rm",
      "sed",
      "tail",
      "uname",
      "whoami",
      "getent",
      "id",
    )
  }
}
