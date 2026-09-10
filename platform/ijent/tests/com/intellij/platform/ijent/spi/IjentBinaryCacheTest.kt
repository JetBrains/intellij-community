// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ijent.spi

import com.intellij.platform.eel.EelPlatform
import com.intellij.platform.eel.channels.EelSendChannel
import com.intellij.platform.eel.channels.EelSendApi
import com.intellij.platform.eel.channels.EelSendChannelException
import com.intellij.platform.eel.provider.utils.sendWholeText
import com.intellij.platform.ijent.IjentExecFileProvider
import com.intellij.platform.ijent.IjentScope
import com.intellij.platform.ijent.ParentOfIjentScopes
import com.intellij.testFramework.common.timeoutRunBlocking
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFilePermissions
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteExisting
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.seconds

@Timeout(30)
@EnabledOnOs(OS.LINUX, OS.MAC)
class IjentBinaryCacheTest {
  @TempDir
  lateinit var root: Path

  @Test
  fun `the same binary is reused without a second upload`(): Unit = timeoutRunBlocking {
    val binary = binary(1)
    val first = deploy(binary)
    val second = deploy(binary)

    (first.bytesSent > binary.readBytes().size) shouldBe true
    (second.bytesSent < binary.readBytes().size) shouldBe true
    first.commandExchanges shouldBe second.commandExchanges
    (first.path != second.path) shouldBe true
    second.content shouldBe binary.readBytes().toList()
    cached(binary).exists() shouldBe true
  }

  @Test
  @EnabledIfSystemProperty(named = "ijent.test.powershell.path", matches = ".+")
  fun `PowerShell uploads and reuses the binary through stdin`(): Unit = timeoutRunBlocking {
    val binary = binary(1)
    val first = deploy(binary, powerShell = true)
    val second = deploy(binary, powerShell = true)

    (first.bytesSent > binary.readBytes().size) shouldBe true
    (second.bytesSent < binary.readBytes().size) shouldBe true
    first.commandExchanges shouldBe second.commandExchanges
    (first.path != second.path) shouldBe true
    first.content shouldBe binary.readBytes().toList()
    second.content shouldBe first.content
    cached(binary, powerShell = true).exists() shouldBe true
  }

  @Test
  fun `different binaries of the same size have separate cache entries`(): Unit = timeoutRunBlocking {
    val firstBinary = binary(1)
    val secondBinary = binary(2)
    deploy(firstBinary)
    val second = deploy(secondBinary)

    (second.bytesSent > secondBinary.readBytes().size) shouldBe true
    second.content shouldBe secondBinary.readBytes().toList()
    cached(firstBinary).exists() shouldBe true
    cached(secondBinary).exists() shouldBe true
    (deploy(firstBinary).bytesSent < firstBinary.readBytes().size) shouldBe true
  }

  @Test
  fun `a damaged cache entry is replaced`(): Unit = timeoutRunBlocking {
    val binary = binary(1)
    deploy(binary)
    val cached = cached(binary)
    cached.deleteExisting()
    cached.writeBytes(binary.readBytes().reversedArray())

    val repaired = deploy(binary)

    (repaired.bytesSent > binary.readBytes().size) shouldBe true
    repaired.content shouldBe binary.readBytes().toList()
    cached.readBytes().toList() shouldBe binary.readBytes().toList()
    (deploy(binary).bytesSent < binary.readBytes().size) shouldBe true
  }

  @Test
  fun `old cache entries are reused and marked as recently used`(): Unit = checkOldEntries(powerShell = false)

  @Test
  @EnabledIfSystemProperty(named = "ijent.test.powershell.path", matches = ".+")
  fun `PowerShell marks old cache entries as recently used`(): Unit = checkOldEntries(powerShell = true)

  private fun checkOldEntries(powerShell: Boolean): Unit = timeoutRunBlocking(30.seconds) {
    val oldBinary = binary(1)
    val currentBinary = binary(2)
    deploy(oldBinary, powerShell = powerShell)
    deploy(currentBinary, powerShell = powerShell)
    val oldEntry = cached(oldBinary, powerShell)
    val currentEntry = cached(currentBinary, powerShell)
    val previousUse = FileTime.from(Instant.now().minus(365, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS))
    for (entry in listOf(oldEntry, currentEntry)) {
      Files.setLastModifiedTime(entry, previousUse)
    }

    val reused = deploy(currentBinary, powerShell = powerShell)

    (reused.bytesSent < currentBinary.readBytes().size) shouldBe true
    oldEntry.exists() shouldBe true
    Files.getLastModifiedTime(oldEntry) shouldBe previousUse
    (Files.getLastModifiedTime(currentEntry) > previousUse) shouldBe true
    (deploy(oldBinary, powerShell = powerShell).bytesSent < oldBinary.readBytes().size) shouldBe true
    (Files.getLastModifiedTime(oldEntry) > previousUse) shouldBe true
  }

  @Test
  fun `an upload evicts the least recently used binary`(): Unit = checkEviction(powerShell = false)

  @Test
  @EnabledIfSystemProperty(named = "ijent.test.powershell.path", matches = ".+")
  fun `a PowerShell upload evicts the least recently used binary`(): Unit = checkEviction(powerShell = true)

  @Test
  fun `cleanup ignores forced ls colors`(): Unit = checkEviction(
    powerShell = false,
    environment = mapOf("CLICOLOR" to "1", "CLICOLOR_FORCE" to "1", "TERM" to "xterm-256color"),
  )

  private fun checkEviction(powerShell: Boolean, environment: Map<String, String> = emptyMap()): Unit = timeoutRunBlocking(30.seconds) {
    val binaries = (1..6).map { binary(it) }
    deploy(binaries[1], powerShell = powerShell) { sessionCopy ->
      binaries.take(5).forEachIndexed { index, binary ->
        if (index != 1) deploy(binary, powerShell = powerShell)
        Files.setLastModifiedTime(cached(binary, powerShell), FileTime.from(Instant.now().minus(10L - index, ChronoUnit.DAYS)))
      }
      val reused = deploy(binaries.first(), powerShell = powerShell)
      (reused.bytesSent < binaries.first().readBytes().size) shouldBe true

      val uploaded = deploy(binaries.last(), powerShell = powerShell, environment = environment)

      uploaded.commandExchanges shouldBe reused.commandExchanges
      binaries.forEachIndexed { index, binary -> cached(binary, powerShell).exists() shouldBe (index != 1) }
      Files.list(cacheDirectory(powerShell)).use { it.count() } shouldBe 5L
      sessionCopy.readBytes().toList() shouldBe binaries[1].readBytes().toList()
    }
  }

  @Test
  fun `cleanup preserves unrelated files directories and links`(): Unit = checkCleanupScope(powerShell = false)

  @Test
  @EnabledIfSystemProperty(named = "ijent.test.powershell.path", matches = ".+")
  fun `PowerShell cleanup preserves unrelated files directories and links`(): Unit = checkCleanupScope(powerShell = true)

  private fun checkCleanupScope(powerShell: Boolean): Unit = timeoutRunBlocking(30.seconds) {
    val first = binary(1)
    deploy(first, powerShell = powerShell)
    val directory = cacheDirectory(powerShell)
    val suffix = if (powerShell) ".exe" else ""
    val unrelated = listOf("keep-me", ".upload-abandoned", "ijent-${"g".repeat(64)}$suffix", "ijent-${"a".repeat(65)}$suffix")
      .map { name -> directory.resolve(name).also { it.writeText("unrelated") } }
    val nested = directory.resolve("ijent-${"0".repeat(64)}$suffix").createDirectories().resolve("keep-me")
    nested.writeText("nested")
    val target = root.resolve("link-target").also { it.writeText("target") }
    val link = directory.resolve("ijent-${"1".repeat(64)}$suffix")
    Files.createSymbolicLink(link, target)
    val previousUse = FileTime.from(Instant.now().minus(365, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS))
    for (entry in unrelated + listOf(nested.parent, target, cached(first, powerShell))) {
      Files.setLastModifiedTime(entry, previousUse)
    }

    for (seed in 2..6) {
      deploy(binary(seed), powerShell = powerShell)
    }

    cached(first, powerShell).exists() shouldBe false
    unrelated.forEach {
      it.readBytes().toList() shouldBe "unrelated".toByteArray().toList()
      Files.getLastModifiedTime(it) shouldBe previousUse
    }
    nested.readBytes().toList() shouldBe "nested".toByteArray().toList()
    Files.isSymbolicLink(link) shouldBe true
    target.readBytes().toList() shouldBe "target".toByteArray().toList()
    Files.getLastModifiedTime(target) shouldBe previousUse
  }

  @Test
  fun `LRU failures do not prevent reuse or publication`(): Unit = timeoutRunBlocking {
    val first = binary(1)
    deploy(first)
    val reused = deploy(first, failingCacheCommand = "touch")
    (reused.bytesSent < first.readBytes().size) shouldBe true
    for (seed in 2..5) {
      deploy(binary(seed))
    }
    val last = binary(6)

    deploy(last, failingCacheCommand = "ls").content shouldBe last.readBytes().toList()

    Files.list(cacheDirectory()).use { it.count() } shouldBe 6L
    (deploy(last).bytesSent < last.readBytes().size) shouldBe true
  }

  @Test
  fun `an unavailable cache does not prevent deployment`(): Unit = timeoutRunBlocking {
    home().resolve(".cache").writeText("not a directory")
    val binary = binary(1)

    deploy(binary).content shouldBe binary.readBytes().toList()
  }

  @Test
  fun `a cache publication failure still completes the upload`(): Unit = timeoutRunBlocking {
    val binary = binary(1)

    deploy(binary, failingCacheCommand = "mv").content shouldBe binary.readBytes().toList()
    cached(binary).exists() shouldBe false
    Files.list(cacheDirectory()).use { it.count() } shouldBe 0L
  }

  @Test
  fun `the cache directory cannot be a symbolic link`(): Unit = timeoutRunBlocking {
    val target = root.resolve("another directory").createDirectories()
    cacheDirectory().parent.createDirectories()
    Files.createSymbolicLink(cacheDirectory(), target)
    val binary = binary(1)

    deploy(binary).content shouldBe binary.readBytes().toList()
    Files.list(target).use { it.count() } shouldBe 0L
  }

  @Test
  fun `concurrent deployments publish complete entries`(): Unit = timeoutRunBlocking {
    val binary = binary(1)
    val results = List(4) { async { deploy(binary) } }.awaitAll()

    results.map { it.path }.distinct().size shouldBe results.size
    results.forEach { it.content shouldBe binary.readBytes().toList() }
    cached(binary).readBytes().toList() shouldBe binary.readBytes().toList()
    (deploy(binary).bytesSent < binary.readBytes().size) shouldBe true
  }

  @Test
  fun `concurrent eviction preserves session copies`(): Unit = checkConcurrentEviction(powerShell = false)

  @Test
  @EnabledIfSystemProperty(named = "ijent.test.powershell.path", matches = ".+")
  fun `concurrent PowerShell eviction preserves session copies`(): Unit = checkConcurrentEviction(powerShell = true)

  private fun checkConcurrentEviction(powerShell: Boolean): Unit = timeoutRunBlocking(30.seconds) {
    val binaries = (1..8).map { binary(it) }
    val ready = Channel<Unit>(binaries.size)
    val pruned = CompletableDeferred<Unit>()
    val deployments = binaries.map { binary ->
      async {
        deploy(binary, powerShell = powerShell) { sessionCopy ->
          ready.send(Unit)
          pruned.await()
          sessionCopy.readBytes().toList() shouldBe binary.readBytes().toList()
        }
      }
    }
    repeat(binaries.size) { ready.receive() }
    val last = binary(9)
    deploy(last, powerShell = powerShell)
    Files.list(cacheDirectory(powerShell)).use { (it.count() <= 5) shouldBe true }
    (deploy(last, powerShell = powerShell).bytesSent < last.readBytes().size) shouldBe true
    pruned.complete(Unit)
    deployments.awaitAll()
  }

  private fun binary(seed: Int): Path = root.resolve("local-$seed").also {
    it.writeBytes(ByteArray(256 * 1024) { offset -> (offset + seed).toByte() })
  }

  private fun home(): Path = root.resolve("home with 'quotes'").createDirectories()

  private fun cacheDirectory(powerShell: Boolean = false): Path =
    home().resolve(if (powerShell) ".local/share/JetBrains/ijent" else ".cache/JetBrains/ijent")

  private suspend fun cached(binary: Path, powerShell: Boolean = false): Path =
    cacheDirectory(powerShell).resolve("ijent-${IjentBinaryCache.forBinary(binary).hash}${if (powerShell) ".exe" else ""}")

  private data class Deployment(val path: Path, val content: List<Byte>, val bytesSent: Long, val commandExchanges: Int)

  private suspend fun CoroutineScope.deploy(
    binary: Path,
    failingCacheCommand: String? = null,
    powerShell: Boolean = false,
    environment: Map<String, String> = emptyMap(),
    afterUpload: suspend CoroutineScope.(Path) -> Unit = {},
  ): Deployment {
    val home = home()
    val temporaryDirectory = root.resolve("tmp").createDirectories()
    val commandDirectory = if (failingCacheCommand != null) {
      root.resolve("commands-$failingCacheCommand").createDirectories().also { directory ->
        val command = directory.resolve(failingCacheCommand)
        command.writeText("#!/bin/sh\necho 'Test cache command failure' >&2\nexit 1\n")
        Files.setPosixFilePermissions(command, PosixFilePermissions.fromString("rwx------"))
      }
    }
    else null
    val strategy = object : IjentDeployingOverShellProcessStrategy(ParentOfIjentScopes(this), Dispatchers.IO) {
      override val ijentLabel: String = "cache test"
      var bytesSent = 0L
      var processesCreated = 0
      var commandExchanges = 0

      override val ijentExecFileProvider: IjentExecFileProvider = object : IjentExecFileProvider {
        override suspend fun getIjentBinary(targetPlatform: EelPlatform): Path = binary
      }

      override suspend fun getShellDialect(): ShellDialect = if (powerShell) ShellDialect.POWERSHELL else ShellDialect.POSIX

      override suspend fun mapPath(path: Path): String? = null

      override suspend fun isExpectedProcessExit(exitCode: Int): Boolean = exitCode == 0 || exitCode == 143

      override suspend fun createShellProcessFacade(ijentProcessScope: IjentScope): IjentSessionProcessMediator.ProcessFacade {
        processesCreated++
        val process = withContext(Dispatchers.IO) {
          val command =
            if (powerShell) listOf(System.getProperty("ijent.test.powershell.path"), "-NoLogo", "-NoProfile", "-NonInteractive", "-Command", "-")
            else listOf("/bin/sh")
          ProcessBuilder(command).apply {
            environment().putAll(environment)
            environment()["HOME"] = home.toString()
            environment()["TMPDIR"] = temporaryDirectory.toString()
            if (powerShell) {
              environment()["PROCESSOR_ARCHITECTURE"] = "AMD64"
              environment()["TERM"] = "dumb"
            }
            if (commandDirectory != null) {
              environment()["PATH"] = "$commandDirectory:${environment()["PATH"].orEmpty()}"
            }
          }.start()
        }
        val facade = IjentSessionProcessMediator.JavaProcessFacade(ijentProcessScope, process)
        return object : IjentSessionProcessMediator.ProcessFacade by facade {
          override val stdin: EelSendChannel = object : EelSendChannel by facade.stdin {
            @OptIn(EelSendApi::class)
            @Throws(EelSendChannelException::class)
            override suspend fun send(src: ByteBuffer) {
              val before = src.remaining()
              val data = StandardCharsets.UTF_8.decode(src.asReadOnlyBuffer()).toString()
              if (src.position() == 0) {
                val command = data.substringBefore(';')
                if ((command.startsWith("echo ") && command.endsWith("_START")) ||
                    (command.startsWith("Write-Output '") && command.endsWith("_START'"))) {
                  commandExchanges++
                }
              }
              val cacheRootExpression = "[Environment]::GetFolderPath('LocalApplicationData')"
              if (powerShell && cacheRootExpression in data) {
                val cacheRoot = home.resolve(".local/share").toString().replace("'", "''")
                facade.stdin.sendWholeText(data.replace(cacheRootExpression, "'$cacheRoot'"))
                src.position(src.limit())
              }
              else {
                facade.stdin.send(src)
              }
              bytesSent += before - src.remaining()
            }
          }
        }
      }

      suspend fun upload(): Path {
        getTargetPlatform()
        return Path.of(copyFile(binary))
      }

      fun closeStrategy() = close()
    }

    try {
      val path = strategy.upload()
      strategy.processesCreated shouldBe 1
      coroutineScope { afterUpload(path) }
      return Deployment(path, path.readBytes().toList(), strategy.bytesSent, strategy.commandExchanges)
    }
    finally {
      strategy.closeStrategy()
    }
  }
}
