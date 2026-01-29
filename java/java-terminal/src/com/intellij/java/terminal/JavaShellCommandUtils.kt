// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.terminal

import com.intellij.execution.vmOptions.JdkOptionsData
import com.intellij.execution.vmOptions.VMOptionKind
import com.intellij.execution.vmOptions.VMOptionVariant
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SystemInfo
import com.intellij.terminal.completion.spec.ShellCompletionSuggestion
import com.intellij.terminal.completion.spec.ShellRuntimeContext
import com.intellij.terminal.completion.spec.ShellRuntimeDataGenerator
import org.jetbrains.plugins.terminal.block.completion.spec.ShellDataGenerators
import org.jetbrains.plugins.terminal.block.completion.spec.ShellRuntimeDataGenerator
import org.jetbrains.plugins.terminal.block.completion.spec.dsl.ShellChildOptionsContext
import org.jetbrains.plugins.terminal.block.completion.spec.dsl.ShellCommandContext

object JavaShellCommandUtils {
  internal val DEFAULT_UI_OPTION_INSTANCE = UIOptionInfo()
  private const val SEPARATOR_NOT_FOUND_INDEX = -1

  internal fun ShellChildOptionsContext.addOptionsFromData(jdkOptionsData: JdkOptionsData, uiMap: Map<String, UIOptionInfo>) {
    jdkOptionsData.options
      .filter { (it.kind == VMOptionKind.Standard || it.kind == VMOptionKind.Product) && (it.variant != VMOptionVariant.XX)}
      .toList()
      .forEach {
        val optionName = it.optionName
        val presentableName = "${it.variant.prefix()}$optionName"
        option(presentableName) {
          val optionDescription = it.doc
          if (optionDescription != null) {
            description(optionDescription)
          }

          val info = uiMap[presentableName] ?: DEFAULT_UI_OPTION_INSTANCE
          repeatTimes(info.repeatTimes)
          info.separator?.let { separator(it) }
          val argumentName = info.argumentName
          if (argumentName != null) {
            argument {
              if (info.isArgumentOptional) optional()
              displayName(argumentName)

              if (info.suggestionsGenerator != null) suggestions(info.suggestionsGenerator)
            }
          }
        }
      }
  }

  internal fun ShellCommandContext.addClassPathOption() {
    option("-classpath", "-cp") {
      exclusiveOn(listOf("--class-path"))
      description(JavaTerminalBundle.message("java.command.terminal.classpath.option.description"))
      argument {
        suggestions(classpathSuggestionsGenerator())
        displayName(CLASSPATH_ARGUMENT_NAME)
      }
    }
  }

  internal val CLASSPATH_ARGUMENT_NAME: @NlsSafe String = "filepath[${getClassPathSeparator()}filepath]"

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

    return if (separatorIndex == SEPARATOR_NOT_FOUND_INDEX) {
      PathInfo(typedPrefix)
    } else {
      PathInfo(typedPrefix.substring(separatorIndex + 1), separatorIndex + 1)
    }
  }

  internal data class UIOptionInfo(
    val separator: String? = null,
    @NlsSafe val argumentName: String? = null,
    val repeatTimes: Int = 1,
    val isArgumentOptional: Boolean = false,
    val suggestionsGenerator: ShellRuntimeDataGenerator<List<ShellCompletionSuggestion>>? = null,
  )

  private data class PathInfo(val typedPrefix: String, val replacementIndexDelta: Int = 0)
}