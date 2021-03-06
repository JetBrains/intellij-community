// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.isAncestor
import java.nio.file.Files
import java.nio.file.Path

private val LOG = logger<JdkInstallerWSL>()

object JdkInstallerWSL {
  fun unpackJdkOnWsl(wslDistribution: WSLDistributionForJdkInstaller,
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

  private fun unpackJdkArchiveOnWsl(wslDistribution: WSLDistributionForJdkInstaller,
                                    packageType: JdkPackageType,
                                    downloadFile: Path,
                                    targetDir: Path) {
    val downloadFileWslPath = wslDistribution.getWslPath(downloadFile)
    val targetWslPath = wslDistribution.getWslPath(targetDir)
    FileUtil.createDirectory(targetDir.toFile())

    val command = when (packageType) {
      JdkPackageType.ZIP -> listOf("unzip", downloadFileWslPath)
      JdkPackageType.TAR_GZ -> listOf("tar", "xzf", downloadFileWslPath)
    }
    val processOutput = wslDistribution.executeOnWsl(command, targetWslPath, 300_000)
    if (processOutput.exitCode != 0) {
      val message = "Failed to unpack $downloadFile to $targetDir"
      LOG.warn(message + ": " + processOutput.stderrLines.takeLast(10).joinToString("") { "\n  $it" })
      throw RuntimeException(message)
    }
  }

  private fun moveUnpackedJdkPrefixOnWsl(
    wslDistribution: WSLDistributionForJdkInstaller,
    unpackDir: Path,
    targetDir: Path,
    packageRootPrefixRaw: String,
  ) {
    val packageRootPrefix = packageRootPrefixRaw.removePrefix("./").trim('/')
    val packageRootDir = if (packageRootPrefix.isBlank()) unpackDir else unpackDir.resolve(packageRootPrefixRaw).normalize()

    if (!unpackDir.isAncestor(packageRootDir)) {
      error("Failed to move JDK contents from $unpackDir to $packageRootDir. Invalid metadata is detected")
    }

    if (!Files.isDirectory(packageRootDir)) {
      error("Invalid package. Directory is expected under '$packageRootPrefixRaw' path on the JDK package")
    }

    val wslTarget = wslDistribution.getWslPath(targetDir)
    val wslUnpack = wslDistribution.getWslPath(unpackDir)
    val wslSource = wslDistribution.getWslPath(packageRootDir)

    FileUtil.delete(targetDir)
    val command = listOf("mv", wslSource, wslTarget)
    val processOutput = wslDistribution.executeOnWsl(command, wslUnpack, 300_000)

    if (processOutput.exitCode != 0) {
      val message = "Failed to strip package root prefix ${packageRootPrefix}"
      LOG.warn(message + ": " + processOutput.stderrLines.takeLast(10).joinToString("") { "\n  $it" })
      throw RuntimeException(message)
    }
  }
}
