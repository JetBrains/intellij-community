// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.actions

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.psi.PsiElement
import com.intellij.util.concurrency.annotations.RequiresReadLockAbsence
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
    if (!enabled || !ApplicationManager.getApplication().isInternal || session == null) return
    val source = runReadActionBlocking { SourceInfo(expression.text, position.file.virtualFile.canonicalPath, message) }
    report(source, session)
  }

  internal class SourceInfo(val text: String, val path: String?, val message: String)

  @RequiresReadLockAbsence
  internal fun report(source: SourceInfo, session: DebuggerSession?) {
    if (!enabled || !ApplicationManager.getApplication().isInternal || session == null) return
    val stackTrace = session.contextManager.context.threadProxy?.frames()?.joinToString(separator = "\n") {
      val location = it.location()
      val method = DebuggerUtilsEx.getMethod(location)
      val lineNumber = DebuggerUtilsEx.getLineNumber(location, false)
      val sourceName = DebuggerUtilsEx.getSourceName(location, null)
      "$method at $sourceName:${lineNumber}"
    }

    val context = """
        File path = ${source.path}
        Project name = ${session.project.name}
    """.trimIndent()

    val attachments = buildList {
      add(Attachment("context.txt", context).apply { isIncluded = true })
      if (stackTrace != null) {
        add(Attachment("stacktrace.txt", stackTrace).apply { isIncluded = true })
      }
      add(Attachment("expression.txt", source.text).apply { isIncluded = true })
    }

    thisLogger().error("Failed to locate targets in bytecode. Details in attachments.",
                       RuntimeExceptionWithAttachments(source.message, *attachments.toTypedArray()))
  }

  @JvmStatic
  fun joinTargetInfo(targets: List<SmartStepTarget>): String {
    return targets.joinToString { it.presentation }
  }
}
