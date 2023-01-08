// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.memory.agent.extractor

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Path

object AgentExtractor {
  private lateinit var extractedFile: File
  private var lastModified = -1L

  @Synchronized
  fun extract(agentType: AgentLibraryType, directory: Path): Path {
    if (!::extractedFile.isInitialized || !extractedFile.exists() || extractedFile.lastModified() != lastModified) {
        val agentFileName = "${agentType.prefix}memory_agent${agentType.suffix}"
        val file = FileUtilRt.createTempFile(directory.toFile(), "${agentType.prefix}memory_agent", agentType.suffix, true)
        val inputStream = AgentExtractor::class.java.classLoader.getResourceAsStream("bin/$agentFileName") ?: throw FileNotFoundException(agentFileName)
        inputStream.use { input ->
          Files.newOutputStream(file.toPath()).use { output ->
            FileUtil.copy(input, output)
          }
        }
        extractedFile = file
        lastModified = file.lastModified()
    }

    return extractedFile.toPath()
  }

  enum class AgentLibraryType(val prefix: String, val suffix: String) {
    WINDOWS32("", "32.dll"),
    WINDOWS64("", ".dll"),
    WINDOWS_ARM64("", "64a.dll"),
    LINUX_X64("lib", ".so"),
    LINUX_AARCH64("lib", "_aarch64.so"),
    MACOS("lib", ".dylib")
  }
}
