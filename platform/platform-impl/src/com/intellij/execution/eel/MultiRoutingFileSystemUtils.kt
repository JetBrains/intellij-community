// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.eel

import com.intellij.diagnostic.VMOptions
import com.intellij.execution.wsl.WslIjentAvailabilityService
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.core.nio.fs.MultiRoutingFileSystem
import com.intellij.platform.core.nio.fs.MultiRoutingFileSystemProvider
import org.jetbrains.annotations.ApiStatus
import java.io.BufferedReader
import java.nio.file.FileSystems
import kotlin.io.path.bufferedReader

@ApiStatus.Internal
object MultiRoutingFileSystemUtils {
  private val logger = logger<MultiRoutingFileSystem>()

  val isMultiRoutingFsEnabled: Boolean by lazy {
    val defaultProvider = FileSystems.getDefault().provider()
    when {
      defaultProvider.javaClass.name == MultiRoutingFileSystemProvider::class.java.name -> true
      else -> {
        val vmOptions = runCatching {
          VMOptions.getUserOptionsFile()?.bufferedReader()?.use { it.readText() }
          ?: "<null>"
        }.getOrElse { err -> err.stackTraceToString() }

        val systemProperties = runCatching {
          System.getProperties().entries.joinToString("\n") { (k, v) -> "$k=$v" }
        }.getOrElse<String, String> { err -> err.stackTraceToString() }

        val message = "The default filesystem ${FileSystems.getDefault()} is not ${MultiRoutingFileSystemProvider::class.java}"

        logger.warn("$message\nVM Options:\n$vmOptions\nSystem properties:\n$systemProperties")
        false
      }
    }
  }
}