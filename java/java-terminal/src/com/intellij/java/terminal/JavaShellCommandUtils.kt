// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.terminal

import com.intellij.openapi.util.SystemInfo
import com.intellij.terminal.completion.spec.ShellCompletionSuggestion
import com.intellij.terminal.completion.spec.ShellRuntimeContext
import com.intellij.terminal.completion.spec.ShellRuntimeDataGenerator
import org.jetbrains.plugins.terminal.block.completion.spec.ShellDataGenerators
import org.jetbrains.plugins.terminal.block.completion.spec.ShellRuntimeDataGenerator

object JavaShellCommandUtils {
  private const val SEPARATOR_NOT_FOUND_INDEX = -1

  fun getClassPathSeparator(): String = when {
    SystemInfo.isWindows -> ";"
    else -> ":"
  }

  fun classpathSuggestionsGenerator(): ShellRuntimeDataGenerator<List<ShellCompletionSuggestion>> {
    val key = "classpath generator"
    return ShellRuntimeDataGenerator(
      debugName = key,
      getCacheKey = {
        val pathInfo = getPathInfo(it)
        "$key:${pathInfo.typedPrefix}:${pathInfo.replacementIndexDelta}"
      }
    ) { context ->
      val pathInfo = getPathInfo(context)
      ShellDataGenerators.getFileSuggestions(context, pathInfo.typedPrefix, false, pathInfo.replacementIndexDelta)
    }
  }

  private fun getPathInfo(context: ShellRuntimeContext): PathInfo {
    val separator = getClassPathSeparator()
    val typedPrefix = context.typedPrefix
    val separatorIndex = typedPrefix.lastIndexOf(separator)
    val isStartWithQuote = ShellDataGenerators.isStartWithQuote(typedPrefix)
    val quoteOffset = if (isStartWithQuote) 1 else 0

    return if (separatorIndex == SEPARATOR_NOT_FOUND_INDEX) {
      PathInfo(typedPrefix.substring(quoteOffset))
    } else {
      val adjustedSeparatorIndex = if (isStartWithQuote) separatorIndex else separatorIndex + 1
      PathInfo(typedPrefix.substring(separatorIndex + 1), adjustedSeparatorIndex)
    }
  }

  private data class PathInfo(val typedPrefix: String, val replacementIndexDelta: Int = 0)
}