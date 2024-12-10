// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.filters

import com.intellij.execution.ConsoleFolding
import com.intellij.execution.ExecutionBundle
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.NoStackTraceFoldingPanel
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.getParentOfType

private const val STACK_TRACE_ELEMENT_PREFIX = "\tat "
private const val ASYNC_STACK_TRACE_PREFIX = "\tat --- Async.Stack.Trace --- "

private val ENCLOSED_MORE_REGEX = Regex("""\t\.\.\. \d+ more""")

class StackTraceFolding : ConsoleFolding() {

  private val enabled: Boolean
    get() =
      StackTraceFoldingSettings.getInstance().foldJavaStackTrace

  private val countToFold: Int
    get() =
      StackTraceFoldingSettings.getInstance().foldJavaStackTraceGreaterThan

  private var frameCount = 0

  override fun shouldFoldLine(project: Project, line: String): Boolean {
    if (line.startsWith(STACK_TRACE_ELEMENT_PREFIX) || ENCLOSED_MORE_REGEX.matches(line)) {
      // In case of "... 10 more" we still count it as
      frameCount++
      return frameCount > countToFold
    }

    frameCount = 0
    return false
  }

  override fun getPlaceholderText(project: Project, lines: List<String>): String? {
    val count = lines.size
    val hasAsync = lines.any { it.startsWith(ASYNC_STACK_TRACE_PREFIX) }
    val msg =
      if (hasAsync) ExecutionBundle.message("java.stack.trace.folded.frames.including.async", count)
      else ExecutionBundle.message("java.stack.trace.folded.frames", count)
    return "\t$msg"
  }

  override fun shouldBeAttachedToThePreviousLine(): Boolean =
    false

  override fun isEnabledForConsole(consoleView: ConsoleView): Boolean =
    super.isEnabledForConsole(consoleView) &&
      enabled &&
      consoleView.component.getParentOfType<NoStackTraceFoldingPanel>() == null
}
