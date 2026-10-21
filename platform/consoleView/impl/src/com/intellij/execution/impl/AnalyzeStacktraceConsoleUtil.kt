// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl

import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.extensions.ProjectExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.ThreadingAssertions
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object AnalyzeStacktraceConsoleUtil {
  @JvmStatic
  fun printStacktrace(consoleView: ConsoleView, unscrambledTrace: String, consoleViewContentType: ConsoleViewContentType) {
    ThreadingAssertions.assertEventDispatchThread()
    consoleView.clear()
    consoleView.print(unscrambledTrace + "\n", consoleViewContentType)
    consoleView.scrollTo(0)
  }

  @JvmStatic
  val ANALYZE_STACKTRACE_FILTER_EP_NAME: ProjectExtensionPointName<Filter> = ProjectExtensionPointName("com.intellij.analyzeStacktraceFilter")

  @JvmStatic
  fun createConsole(project: Project): ConsoleView {
    val builder = TextConsoleBuilderFactory.getInstance().createBuilder(project)
    builder.filters(ANALYZE_STACKTRACE_FILTER_EP_NAME.getExtensions(project))
    val consoleView = builder.getConsole()
    return consoleView
  }

}