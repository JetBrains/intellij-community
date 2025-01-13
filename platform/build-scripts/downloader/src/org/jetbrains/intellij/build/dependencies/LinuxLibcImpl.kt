// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.dependencies

import org.jetbrains.intellij.build.dependencies.JdkDownloader.OS
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

enum class LinuxLibcImpl {
  GLIBC,
  MUSL;

  companion object {
    @JvmField
    val ALL: List<LinuxLibcImpl> = listOf(*LinuxLibcImpl.entries.toTypedArray())

    val isLinuxMusl: Boolean by lazy {
      if (OS.current != OS.LINUX) {
        false
      } else {
        runCatching {
          val process = ProcessBuilder()
            .command("ldd", "--version")
            .redirectErrorStream(true)
            .start()

          val output = process.inputStream.bufferedReader().use { it.readText().lowercase() }

          val lddOutputContainsMusl = if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            false
          } else {
            // Check for musl in output
            output.contains("musl")
          }
          Logger.getLogger(LinuxLibcImpl::class.java.name).info("Linux 'ldd --version': $output")
          lddOutputContainsMusl
        }.getOrElse {
          Logger.getLogger(LinuxLibcImpl::class.java.name).info("Failed to detect musl libc: ${it.message}")
          false
        }
      }
    }
  }
}