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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
  fun `old cache entries are preserved and reused`(): Unit = timeoutRunBlocking {
    val oldBinary = binary(1)
    val currentBinary = binary(2)
    deploy(oldBinary)
    deploy(currentBinary)
    val oldEntry = cached(oldBinary)
    val currentEntry = cached(currentBinary)
    val unrelated = cacheDirectory().resolve("keep-me").also { it.writeText("unrelated") }
    val abandonedUpload = cacheDirectory().resolve(".upload-abandoned").also { it.writeText("incomplete") }
    val previousUse = FileTime.from(Instant.now().minus(365, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS))
    for (entry in listOf(oldEntry, currentEntry, unrelated, abandonedUpload)) {
      Files.setLastModifiedTime(entry, previousUse)
    }

    val reused = deploy(currentBinary)

    (reused.bytesSent < currentBinary.readBytes().size) shouldBe true
    oldEntry.exists() shouldBe true
    Files.getLastModifiedTime(currentEntry) shouldBe previousUse
    unrelated.exists() shouldBe true
    abandonedUpload.exists() shouldBe true
    (deploy(oldBinary).bytesSent < oldBinary.readBytes().size) shouldBe true
    Files.getLastModifiedTime(oldEntry) shouldBe previousUse
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

    deploy(binary, failCachePublication = true).content shouldBe binary.readBytes().toList()
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
    failCachePublication: Boolean = false,
    powerShell: Boolean = false,
  ): Deployment {
    val home = home()
    val temporaryDirectory = root.resolve("tmp").createDirectories()
    val commandDirectory = if (failCachePublication) {
      root.resolve("commands").createDirectories().also { directory ->
        val mv = directory.resolve("mv")
        mv.writeText("#!/bin/sh\necho 'Test cache publication failure' >&2\nexit 1\n")
        Files.setPosixFilePermissions(mv, PosixFilePermissions.fromString("rwx------"))
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
      return Deployment(path, path.readBytes().toList(), strategy.bytesSent, strategy.commandExchanges)
    }
    finally {
      strategy.closeStrategy()
    }
  }
}
