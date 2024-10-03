// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.terminal

import com.intellij.openapi.util.SystemInfo
import com.intellij.terminal.completion.spec.ShellCompletionSuggestion
import com.intellij.terminal.completion.spec.ShellRuntimeContext
import com.intellij.terminal.completion.spec.ShellRuntimeDataGenerator
import org.jetbrains.plugins.terminal.block.completion.spec.ShellDataGenerators
import org.jetbrains.plugins.terminal.block.completion.spec.ShellRuntimeDataGenerator

internal object JavaShellCommandUtils {
  internal fun getClassPathSeparator() = when {
    SystemInfo.isWindows -> ";"
    else -> ":"
  }

  internal fun classpathSuggestionsGenerator(): ShellRuntimeDataGenerator<List<ShellCompletionSuggestion>> {
    val key = "classpath generator"
    return ShellRuntimeDataGenerator(
      debugName = key,
      getCacheKey = {
        val pathInfo = getPathInfo(it)
        "$key:${pathInfo.lastSeparatorIndex}:${pathInfo.typedPrefix}"
      }
    ) { context ->
      val pathInfo = getPathInfo(context)
      ShellDataGenerators.getFileSuggestions(context, pathInfo.typedPrefix, false, if (pathInfo.lastSeparatorIndex == -1) 0 else pathInfo.lastSeparatorIndex + 1)
    }
  }

  private fun getPathInfo(context: ShellRuntimeContext): PathInfo {
    val separator = getClassPathSeparator()
    val currentPrefix = context.typedPrefix
    val index = currentPrefix.lastIndexOf(separator)
    val newTypedPrefix = if (index == -1) currentPrefix else currentPrefix.substring(index + 1)
    return PathInfo(newTypedPrefix, index)
  }

  private data class PathInfo(val typedPrefix: String, val lastSeparatorIndex: Int)
}