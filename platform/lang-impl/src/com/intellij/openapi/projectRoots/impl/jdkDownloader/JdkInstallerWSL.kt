// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Files
import java.nio.file.Path

private val LOG = logger<JdkInstallerWSL>()

@Internal
object JdkInstallerWSL {
  fun unpackJdkOnWsl(wslDistribution: OsAbstractionForJdkInstaller.Wsl,
                     packageType: JdkPackageType,
                     downloadFile: Path,
                     targetDir: Path,
                     packageRootPrefixRaw: String) {

    val unpackDir = targetDir.resolveSibling(".${targetDir.fileName}-downloading-${System.currentTimeMillis()}")
    try {
      unpackJdkArchiveOnWsl(wslDistribution, packageType, downloadFile, unpackDir)
      moveUnpackedJdkPrefixOnWsl(wslDistribution, unpackDir, targetDir, packageRootPrefixRaw)
    }
    catch (e: Throwable) {
      FileUtil.delete(targetDir)
      throw e
    }
    finally {
      FileUtil.delete(unpackDir)
    }
  }

  private fun unpackJdkArchiveOnWsl(osAbstraction: OsAbstractionForJdkInstaller,
                                    packageType: JdkPackageType,
                                    downloadFile: Path,
                                    targetDir: Path) {
    val downloadFileWslPath = osAbstraction.getPath(downloadFile)
    val targetWslPath = osAbstraction.getPath(targetDir)
    FileUtil.createDirectory(targetDir.toFile())

    val command = when (packageType) {
      JdkPackageType.ZIP -> listOf("unzip", downloadFileWslPath)
      JdkPackageType.TAR_GZ -> listOf("tar", "xzf", downloadFileWslPath)
    }
    val processOutput = osAbstraction.execute(command, targetWslPath, 300_000)
    if (processOutput.exitCode != 0) {
      val message = "Failed to unpack $downloadFile to $targetDir"
      LOG.warn(message + ": " + processOutput.stderrLines.takeLast(10).joinToString("") { "\n  $it" })
      throw RuntimeException(message)
    }
  }

  private fun moveUnpackedJdkPrefixOnWsl(
    osAbstraction: OsAbstractionForJdkInstaller,
    unpackDir: Path,
    targetDir: Path,
    packageRootPrefixRaw: String,
  ) {
    val packageRootPrefix = packageRootPrefixRaw.removePrefix("./").trim('/')
    val packageRootDir = if (packageRootPrefix.isBlank()) unpackDir else unpackDir.resolve(packageRootPrefixRaw).normalize()

    if (!packageRootDir.startsWith(unpackDir)) {
      error("Failed to move JDK contents from $unpackDir to $packageRootDir. Invalid metadata is detected")
    }

    if (!Files.isDirectory(packageRootDir)) {
      error("Invalid package. Directory is expected under '$packageRootPrefixRaw' path on the JDK package")
    }

    val wslTarget = osAbstraction.getPath(targetDir)
    val wslUnpack = osAbstraction.getPath(unpackDir)
    val wslSource = osAbstraction.getPath(packageRootDir)

    FileUtil.delete(targetDir)
    val command = listOf("mv", wslSource, wslTarget)
    val processOutput = osAbstraction.execute(command, wslUnpack, 300_000)

    if (processOutput.exitCode != 0) {
      val message = "Failed to strip package root prefix ${packageRootPrefix}"
      LOG.warn(message + ": " + processOutput.stderrLines.takeLast(10).joinToString("") { "\n  $it" })
      throw RuntimeException(message)
    }
  }
}
