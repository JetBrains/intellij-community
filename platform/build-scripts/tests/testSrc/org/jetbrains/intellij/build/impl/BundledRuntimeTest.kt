// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.NioFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.dependencies.JdkDownloader
import org.junit.Test
import java.nio.file.Files

class BundledRuntimeTest {
  @Test
  fun download(): Unit = runBlocking(Dispatchers.IO) {
    withCompilationContext { context ->
      val bundledRuntime = BundledRuntimeImpl(context.options, context.paths, context.dependenciesProperties, context.messages::error, context.messages::info)
      val currentJbr = bundledRuntime.getHomeForCurrentOsAndArch()
      var spottedCurrentJbrInDownloadVariants = false
      for (prefix in JetBrainsRuntimeDistribution.ALL) {
        for (os in OsFamily.ALL) {
          for (arch in JvmArchitecture.ALL) {
            if (os == OsFamily.WINDOWS && arch == JvmArchitecture.aarch64) {
              // Not supported yet
              // https://youtrack.jetbrains.com/issue/JBR-2074
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
      val currentJbrHome = runBlocking(Dispatchers.IO) {
        BundledRuntimeImpl(
          options = context.options,
          paths = context.paths,
          dependenciesProperties = context.dependenciesProperties,
          error = context.messages::error,
          info = context.messages::info
        )
          .getHomeForCurrentOsAndArch()
      }
      val javaExe = JdkDownloader.getJavaExecutable(currentJbrHome)
      val process = ProcessBuilder(javaExe.toString(), "-version")
        .inheritIO()
        .start()
      val rc = process.waitFor()
      if (rc != 0) {
        error("'$javaExe -version' non-zero exit code $rc")
      }
    }
  }

  private inline fun withCompilationContext(block: (CompilationContext) -> Unit) {
    val tempDir = Files.createTempDirectory("compilation-context-")
    try {
      val communityHome = IdeaProjectLoaderUtil.guessCommunityHome(javaClass)
      val context = createCompilationContextBlocking(
        communityHome = communityHome,
        projectHome = communityHome.communityRoot,
        defaultOutputRoot = tempDir,
      )
      block(context)
    }
    finally {
      NioFiles.deleteRecursively(tempDir)
    }
  }
}