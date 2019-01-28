// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.agent.extractor

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.FileNotFoundException

class AgentExtractor {
  private val platform: PlatformType = let {
    if (SystemInfo.isLinux) return@let PlatformType.LINUX
    if (SystemInfo.isMac) return@let PlatformType.MACOS
    return@let if (SystemInfo.is32Bit) PlatformType.WINDOWS32 else PlatformType.WINDOWS64
  }

  fun extract(): File {
    val file = FileUtil.createTempFile("${platform.prefix}memory_agent", platform.suffix, true)
    val agentFileName = "${platform.prefix}memory_agent${platform.suffix}"
    val inputStream = AgentExtractor::class.java.classLoader.getResourceAsStream("bin/$agentFileName")
    if (inputStream == null) throw FileNotFoundException(agentFileName)
    FileUtils.copyToFile(inputStream, file)
    return file
  }

  private enum class PlatformType(val prefix: String, val suffix: String) {
    WINDOWS32("", "32.dll"),
    WINDOWS64("", ".dll"),
    LINUX("lib", ".so"),
    MACOS("lib", ".dylib")
  }
}
