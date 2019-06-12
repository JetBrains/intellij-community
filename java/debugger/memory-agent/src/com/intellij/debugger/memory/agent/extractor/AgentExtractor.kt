// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.agent.extractor

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Path

class AgentExtractor {
  fun extract(agentType: AgentLibraryType, directory: File): Path {
    Files.createTempFile(directory.toPath(), "${agentType.prefix}memory_agent", agentType.suffix)
    val file = FileUtilRt.createTempFile(directory, "${agentType.prefix}memory_agent", agentType.suffix, true).toPath()
    val agentFileName = "${agentType.prefix}memory_agent${agentType.suffix}"
    val inputStream = AgentExtractor::class.java.classLoader.getResourceAsStream("bin/$agentFileName") ?: throw FileNotFoundException(agentFileName)
    inputStream.use { input ->
      Files.newOutputStream(file).use { output ->
        FileUtil.copy(input, output)
      }
    }
    return file
  }

  enum class AgentLibraryType(val prefix: String, val suffix: String) {
    WINDOWS32("", "32.dll"),
    WINDOWS64("", ".dll"),
    LINUX("lib", ".so"),
    MACOS("lib", ".dylib")
  }
}
