// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dependencies

import org.jetbrains.intellij.build.dependencies.BuildDependenciesManualRunOnly.communityRootFromWorkingDirectory
import org.jetbrains.intellij.build.dependencies.JdkDownloader.getJavaExecutable
import org.jetbrains.intellij.build.dependencies.JdkDownloader.getJdkHome
import org.junit.Assert
import org.junit.Test
import java.nio.file.Files

class JdkDownloaderTest {
  @Test
  fun allJdkVariantsCouldBeDownloaded() {
    val communityRoot = communityRootFromWorkingDirectory
    for (os in JdkDownloader.OS.values()) {
      for (arch in listOf(JdkDownloader.Arch.X86_64, JdkDownloader.Arch.ARM64)) {
        if (os === JdkDownloader.OS.WINDOWS && arch === JdkDownloader.Arch.ARM64) {
          // Not supported yet
          continue
        }

        val jdkHome = getJdkHome(communityRoot, os, arch) { }
        val javaExecutable = getJavaExecutable(jdkHome)
        Assert.assertTrue(Files.exists(javaExecutable))
      }
    }
  }
}
