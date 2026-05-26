// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DialogTitleCapitalization", "HardCodedStringLiteral")

package com.intellij.platform.ijent.community.ui.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.platform.core.nio.fs.MultiRoutingFileSystem
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.EelPlatform
import com.intellij.platform.eel.nioFs.impl.MultiRoutingFileSystemBackend
import com.intellij.platform.eel.provider.utils.toEelArch
import com.intellij.platform.ijent.IjentExecFileProvider
import com.intellij.platform.ijent.ParentOfIjentScopes
import com.intellij.platform.ijent.coroutineDispatcher
import com.intellij.platform.ijent.getIjentGrpcArgv
import com.intellij.platform.ijent.spi.IjentConnectionStrategy
import com.intellij.platform.ijent.spi.IjentControlledEnvironmentDeployingStrategy
import com.intellij.platform.ijent.spi.IjentDeployingOverShellProcessStrategy
import com.intellij.platform.ijent.spi.IjentDeployingStrategy
import com.intellij.platform.ijent.spi.IjentSessionProcessMediator
import com.intellij.util.system.CpuArch
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.NonNls
import java.io.File
import java.nio.file.FileStore
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.absolutePathString
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.outputStream

private fun dummyEelDescriptor(targetPlatform: EelPlatform): EelDescriptor = object : EelDescriptor {
  override val name: @NonNls String = "mock"
  override val osFamily: EelOsFamily get() = targetPlatform.osFamily
}

internal class IjentLocalVerificationAction : AbstractIjentVerificationAction() {
  private val currentPlatform = when (CpuArch.CURRENT) {
    CpuArch.ARM64 -> when {
      SystemInfoRt.isWindows -> EelPlatform.Windows(CpuArch.ARM64.toEelArch())
      SystemInfoRt.isLinux -> EelPlatform.Linux(CpuArch.ARM64.toEelArch())
      SystemInfoRt.isMac -> EelPlatform.Darwin(CpuArch.ARM64.toEelArch())
      else -> null
    }

    CpuArch.X86_64 -> when {
      SystemInfoRt.isWindows -> EelPlatform.Windows(CpuArch.X86_64.toEelArch())
      SystemInfoRt.isLinux -> EelPlatform.Linux(CpuArch.X86_64.toEelArch())
      SystemInfoRt.isMac -> EelPlatform.Darwin(CpuArch.X86_64.toEelArch())
      else -> null
    }

    null, CpuArch.X86, CpuArch.ARM32, CpuArch.OTHER, CpuArch.UNKNOWN -> null
  }

  private val title = "Test IJent + Local Machine: $currentPlatform"

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.run {
      text = title
      isEnabled = isEnabled && currentPlatform != null
    }
  }

  override suspend fun deployingStrategy(ijentProcessScope: ParentOfIjentScopes): Triple<String, IjentDeployingStrategy, EelDescriptor> {
    val targetPlatform = currentPlatform ?: error("Local IJent is not supported on this OS")

    return Triple(title, object : IjentControlledEnvironmentDeployingStrategy() {
      override suspend fun getTargetPlatform(): EelPlatform = targetPlatform
      override suspend fun getConnectionStrategy(): IjentConnectionStrategy = IjentConnectionStrategy.Default

      override val ijentExecFileProvider: IjentExecFileProvider = service()

      override suspend fun createProcess(binaryPath: String): IjentSessionProcessMediator =
        IjentSessionProcessMediator.create(
          ijentProcessScope,
          ProcessBuilder(*getIjentGrpcArgv(binaryPath).toTypedArray()).start(),
          title,
          ::isExpectedProcessExit
        )

      override suspend fun copyFile(file: Path): String =
        file.toString()

      override fun close(): Unit = Unit
    }, dummyEelDescriptor(targetPlatform))
  }
}

internal class IjentDockerVerificationAction : AbstractIjentVerificationAction() {
  private val dockerImage = "ubuntu:22.04"

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.text = "Test IJent + Docker $dockerImage"
  }

  @OptIn(DelicateCoroutinesApi::class)  // It's an internal action for testing, no need for the perfectly clean code.
  override suspend fun deployingStrategy(ijentProcessScope: ParentOfIjentScopes): Triple<String, IjentDeployingStrategy, EelDescriptor> {
    // TODO Use com.intellij.clouds.docker.gateway.ijent.DockerIjentDeployingStrategy
    val targetPlatform = when (val arch = CpuArch.CURRENT) {
      CpuArch.ARM64 -> EelPlatform.Linux(EelPlatform.Arch.ARM_64)
      CpuArch.X86_64 -> EelPlatform.Linux(EelPlatform.Arch.X86_64)

      null, CpuArch.X86, CpuArch.ARM32, CpuArch.OTHER, CpuArch.UNKNOWN -> error("Unsupported CPU arch: $arch")
    }

    val title = "Docker $dockerImage"
    return Triple(title, object : IjentDeployingOverShellProcessStrategy.JavaProcessBasedStrategy(ijentProcessScope, ijentProcessScope.s.coroutineDispatcher()) {
      override val ijentLabel: String = title

      override suspend fun mapPath(path: Path): String? = null

      override val ijentExecFileProvider: IjentExecFileProvider = service()

      override suspend fun createShellProcess(): Process {
        val containerName = "ijent-test-${LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)}"
        return withContext(Dispatchers.IO) {
          ProcessBuilder()
            .command(
              "docker",
              "run",
              "--interactive",
              "--rm",
              "--volume",
              "${ijentExecFileProvider.getIjentBinary(targetPlatform).toBindMount()}:/ijent:ro",
              "--name",
              containerName,
              "registry.jetbrains.team/p/ij/docker-hub/$dockerImage",
              "bash",
            )
            .redirectInput(ProcessBuilder.Redirect.PIPE)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start()
        }
      }
    }, dummyEelDescriptor(targetPlatform))
  }

  /**
   * https://docs.docker.com/desktop/troubleshoot/topics/#path-conversion-on-windows
   */
  private fun Path.toBindMount(): String =
    if (SystemInfo.isWindows)
      absolutePathString()
        .replace("\\", "/")
        .let { "/${it.substring(0, 1).lowercase()}${it.substring(2)}" }
    else
      absolutePathString()
}

internal class MultiRoutingFileSystemVerificationAction : DumbAwareAction() {
  @OptIn(DelicateCoroutinesApi::class)  // It's an internal action for debugging, and correct scopes don't matter.
  override fun actionPerformed(e: AnActionEvent) {
    GlobalScope.launch(Dispatchers.EDT) {
      val message = buildString {
        append("MultiRoutingFileSystem is the default filesystem (IJPL-160621):\n")
        val actualCls = FileSystems.getDefault().javaClass
        val expectedCls = MultiRoutingFileSystem::class.java
        append(
          if (actualCls.name == expectedCls.name) "YES"
          else "NO, it's ${actualCls.name}"
        )

        append("\n\nMultiRoutingFileSystem is loaded by a single classloader (IJPL-158098):\n")
        append(
          if (actualCls == MultiRoutingFileSystem::class.java) "YES"
          else "NO, `FileSystems.getDefault()` is loaded by ${actualCls.classLoader}, the plugin loaded it with ${expectedCls.classLoader}"
        )

        append("\n\nJBR is patched to forward java.io over nio (JBR-7700):\n")
        run {
          val jbrIsNioPatched = runCatching { Class.forName("com.jetbrains.internal.IoOverNio") }.isSuccess
          val vmOption = "jbr.java.io.use.nio"
          val enabledByVmOptions = System.getProperty(vmOption, "true").equals("true", ignoreCase = true)

          val version = Runtime.version().toString()
          val fileToCheck = "test-file"
          val localRoot =
            if (SystemInfoRt.isWindows) """\\mrfs-test\foobar\"""
            else "/mrfs-test/foobar"
          val (nioExists, ioExists) = withZipFile(localRoot, fileToCheck) { emptyZip ->
            val zipFileSystem = FileSystems.newFileSystem(emptyZip)
            withRegisteredBackend(zipFileSystem, localRoot) {
              val ioFile = File(File(localRoot), fileToCheck)
              val nioPath = Path.of(localRoot, fileToCheck)
              nioPath.exists() to ioFile.exists()
            }
          }
          append(
            when {
              !jbrIsNioPatched -> "NO, $version is not patched"
              !enabledByVmOptions -> "NO, $version is patched by disabled by $vmOption"
              nioExists && ioExists -> "YES, $version is patched, io probe succeeds"
              nioExists -> "?, nio probe succeeds, io fails"
              else -> "?, nio probe fails"
            }
          )
        }
      }
      Messages.showInfoMessage(message, e.presentation.text)
    }
  }
}

private fun <T> withRegisteredBackend(backendFileSystem: FileSystem, localRoot: String, f: () -> T): T {
  val disposable = Disposer.newDisposable()
  try {
    MultiRoutingFileSystemBackend.EP_NAME.point.registerExtension(
      object : MultiRoutingFileSystemBackend {
        override fun compute(localFS: FileSystem, sanitizedPath: String): FileSystem? =
          if (localRoot.replace('\\', '/').trim('/') in sanitizedPath) backendFileSystem
          else null

        override fun getCustomRoots(): Collection<String> = listOf()

        override fun getCustomFileStores(localFS: FileSystem): Collection<FileStore> = listOf()
      },
      disposable,
    )
    return f()
  }
  finally {
    Disposer.dispose(disposable)
  }
}

private fun <T> withZipFile(mountPrefix: String, fileToCheck: String, f: (Path) -> T): T {
  val emptyZip = createTempFile("mrfs-test", ".zip")
  ZipOutputStream(emptyZip.outputStream()).use { zipOutputStream ->
    zipOutputStream.putNextEntry(ZipEntry("${MultiRoutingFileSystem.sanitizeRoot(mountPrefix)}/$fileToCheck"))
    zipOutputStream.write("Text".toByteArray())
  }
  try {
    return f(emptyZip)
  }
  finally {
    emptyZip.deleteIfExists()
  }
}