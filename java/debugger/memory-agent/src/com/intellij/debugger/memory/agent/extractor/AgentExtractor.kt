// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
    LINUX("lib", ".so"),
    MACOS("lib", ".dylib")
  }
}
