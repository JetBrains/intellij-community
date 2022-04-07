// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.dependencies.Jdk11Downloader
import org.junit.Test

class BundledRuntimeTest {
  @Test
  fun download() {
    withCompilationContext { context ->
      val bundledRuntime = BundledRuntime(context)
      val currentJbr = bundledRuntime.homeForCurrentOsAndArch
      var spottedCurrentJbrInDownloadVariants = false
      for (prefix in JetBrainsRuntimeDistribution.getALL()) {
        for (os in OsFamily.getALL()) {
          for (arch in JvmArchitecture.getALL()) {
            if (os == OsFamily.WINDOWS && arch == JvmArchitecture.aarch64) {
              // Not supported yet
              // https://youtrack.jetbrains.com/issue/JBR-2074
              continue
            }

            if (os == OsFamily.LINUX && arch == JvmArchitecture.aarch64 && prefix == JetBrainsRuntimeDistribution.JCEF) {
              // Not supported yet
              // https://youtrack.jetbrains.com/issue/JBR-3906
              continue
            }

            val home = try {
              bundledRuntime.extract(prefix.artifactPrefix, os, arch)
            }
            catch (t: Throwable) {
              throw IllegalStateException(
                "Unable to download JBR for os $os, arch $arch, type $prefix (classifier '${prefix.classifier}', artifact prefix '${prefix.artifactPrefix}': ${t.message}",
                t)
            }

            // do not cache, takes too much space. Do not delete current jbr for this os and arch
            if (currentJbr.startsWith(home)) {
              spottedCurrentJbrInDownloadVariants = true
            }
            else {
              FileUtil.delete(home)
            }
          }
        }
      }

      if (!spottedCurrentJbrInDownloadVariants) {
        error("Across all download variants current jbr at $currentJbr was not found")
      }
    }
  }

  @Test
  fun currentArchDownload() {
    withCompilationContext { context ->
      val currentJbrHome = BundledRuntime(context).homeForCurrentOsAndArch
      val javaExe = Jdk11Downloader.getJavaExecutable(currentJbrHome)
      val process = ProcessBuilder(javaExe.toString(), "-version")
        .inheritIO()
        .start()
      val rc = process.waitFor()
      if (rc != 0) {
        error("'$javaExe -version' non-zero exit code $rc")
      }
    }
  }

  private fun withCompilationContext(block: (CompilationContext) -> Unit) {
    val tempDir = FileUtil.createTempDirectory("compilation-context-", "")
    try {
      val communityHome = IdeaProjectLoaderUtil.guessCommunityHome(javaClass)
      val context = CompilationContextImpl.create(communityHome.toString(), communityHome.toString(), tempDir.toString())
      block(context)
    }
    finally {
      FileUtil.delete(tempDir)
    }
  }
}