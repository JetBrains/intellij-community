// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.terminal

import com.intellij.terminal.completion.spec.ShellRuntimeContext
import com.intellij.util.lang.JavaVersion

internal class JavaShellCommandContext private constructor(private val propertyMap: Map<String, String> = mapOf()) {

  fun getJrePath(): String? = propertyMap["java.home"]

  fun getJavaVersion(): JavaVersion? {
    val versionString = propertyMap["java.version"] ?: return null
    return JavaVersion.tryParse(versionString)
  }

  companion object {
    private const val PROPERTY_SEPARATOR = " = "

    suspend fun create(context: ShellRuntimeContext): JavaShellCommandContext? {
      val result = context.runShellCommand("java -XshowSettings:properties -version")
      if (result.exitCode != 0) return null
      val propertyMap = result.output.split('\n').dropLastWhile { it.isBlank() }.asSequence().map { it.trim() }
        .filter { it.contains(PROPERTY_SEPARATOR) }.mapNotNull {
          val split = it.split(PROPERTY_SEPARATOR)
          if (split.size != 2) return@mapNotNull null
          Pair(split[0], split[1])
        }.toMap()
      return JavaShellCommandContext(propertyMap)
    }
  }
}