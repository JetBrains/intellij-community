// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.agent.extractor

import com.intellij.openapi.util.io.FileUtil
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.FileNotFoundException

class AgentExtractor {
  fun extract(agentType: AgentLibraryType): File {
    val file = FileUtil.createTempFile("${agentType.prefix}memory_agent", agentType.suffix, true)
    val agentFileName = "${agentType.prefix}memory_agent${agentType.suffix}"
    val inputStream = AgentExtractor::class.java.classLoader.getResourceAsStream("bin/$agentFileName")
    if (inputStream == null) throw FileNotFoundException(agentFileName)
    FileUtils.copyToFile(inputStream, file)
    return file
  }

  public enum class AgentLibraryType(val prefix: String, val suffix: String) {
    WINDOWS32("", "32.dll"),
    WINDOWS64("", ".dll"),
    LINUX("lib", ".so"),
    MACOS("lib", ".dylib")
  }
}
