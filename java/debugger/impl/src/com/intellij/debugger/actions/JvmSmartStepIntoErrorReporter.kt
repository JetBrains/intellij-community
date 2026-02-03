// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.actions

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

@ApiStatus.Internal
object JvmSmartStepIntoErrorReporter {
  @JvmStatic
  @TestOnly
  var enabled = true

  @JvmStatic
  fun report(
    expression: PsiElement,
    session: DebuggerSession?,
    position: SourcePosition,
    message: String,
  ) {
    if (!enabled) return
    if (ApplicationManager.getApplication().isInternal && session != null) {
      reportInternal(expression, session, position, message)
    }
  }

  private fun reportInternal(
    expression: PsiElement,
    session: DebuggerSession,
    position: SourcePosition,
    message: String,
  ) {
    val stackTrace = session.contextManager.context.threadProxy?.frames()?.joinToString(separator = "\n") {
      val location = it.location()
      val method = DebuggerUtilsEx.getMethod(location)
      val lineNumber = DebuggerUtilsEx.getLineNumber(location, false)
      val sourceName = DebuggerUtilsEx.getSourceName(location, null)
      "$method at $sourceName:${lineNumber}"
    }
    val path = position.file.virtualFile.canonicalPath

    val context = """
        File path = $path
        Project name = ${session.project.name}
    """.trimIndent()

    val attachments = buildList {
      add(Attachment("context.txt", context).apply { isIncluded = true })
      if (stackTrace != null) {
        add(Attachment("stacktrace.txt", stackTrace).apply { isIncluded = true })
      }
      add(Attachment("expression.txt", runReadAction { expression.text }).apply { isIncluded = true })
    }

    thisLogger().error("Failed to locate targets in bytecode. Details in attachments.",
                       RuntimeExceptionWithAttachments(message, *attachments.toTypedArray()))
  }

  @JvmStatic
  fun joinTargetInfo(targets: List<SmartStepTarget>): String {
    return targets.joinToString { it.presentation }
  }
}
