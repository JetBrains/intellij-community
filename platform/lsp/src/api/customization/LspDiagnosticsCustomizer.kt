// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.api.customization

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.DiagnosticTag
import org.jetbrains.annotations.ApiStatus

sealed class LspDiagnosticsCustomizer

/**
 * Handles [Diagnostic](https://microsoft.github.io/language-server-protocol/specification#diagnostic) objects received from the LSP server.
 */
open class LspDiagnosticsSupport : LspDiagnosticsCustomizer() {

  /**
   * `true` means that the IDE should send the
   * [textDocument/diagnostic](https://microsoft.github.io/language-server-protocol/specification/#textDocument_pullDiagnostics)
   * request to the server for the given file.
   * Diagnostics received in response will be passed to [createAnnotation].
   *
   * This function is called only if the server has the corresponding capability.
   *
   * Note that the LSP specification describes two ways of getting diagnostic information for a file: pushed by the server
   * ([textDocument/publishDiagnostics](https://microsoft.github.io/language-server-protocol/specification/#textDocument_publishDiagnostics))
   * and pulled by the client
   * ([textDocument/diagnostic](https://microsoft.github.io/language-server-protocol/specification/#textDocument_pullDiagnostics)).
   * This function controls the latter one.
   */
  open fun shouldAskServerForDiagnostics(file: VirtualFile): Boolean = true

  @RequiresReadLock
  @RequiresBackgroundThread
  open fun createAnnotation(holder: AnnotationHolder, diagnostic: Diagnostic, textRange: TextRange, quickFixes: List<IntentionAction>) {
    val severity = getHighlightSeverity(diagnostic) ?: return
    holder.newAnnotation(severity, getMessage(diagnostic))
      .tooltip(getTooltip(diagnostic))
      .range(textRange)
      .let {
        // To get correct error highlighting in the editor, it's necessary to call `afterEndOfLine()` for zero-length diagnostics at the end of a line
        if (!textRange.isEmpty) return@let it
        val psiFile = holder.currentAnnotationSession.file
        val document = PsiDocumentManager.getInstance(psiFile.project).getDocument(psiFile) ?: return@let it
        val lineNumber = document.getLineNumber(textRange.startOffset)
        val lineEndOffset = document.getLineEndOffset(lineNumber)
        if (textRange.startOffset == lineEndOffset) it.afterEndOfLine() else it
      }
      .let {
        val highlightType = getSpecialHighlightType(diagnostic)
        if (highlightType != null) it.highlightType(highlightType) else it
      }
      .let {
        val textAttributes = getEnforcedTextAttributes(diagnostic)
        if (textAttributes != null) it.enforcedTextAttributes(textAttributes) else it
      }
      .let {
        customizeQuickFixes(diagnostic, quickFixes).fold(it) { builder, quickFix ->
          builder.withFix(quickFix)
        }
      }
      .create()
  }

  /**
   * Implementations may return `null` if this [diagnostic] should be ignored.
   */
  open fun getHighlightSeverity(diagnostic: Diagnostic): HighlightSeverity? =
    when (diagnostic.severity) {
      DiagnosticSeverity.Error -> HighlightSeverity.ERROR
      DiagnosticSeverity.Warning -> HighlightSeverity.WARNING
      else -> HighlightSeverity.WEAK_WARNING
    }

  @InspectionMessage
  open fun getMessage(diagnostic: Diagnostic): String = diagnostic.message

  @NlsContexts.Tooltip
  open fun getTooltip(diagnostic: Diagnostic): String = diagnostic.message

  open fun getSpecialHighlightType(diagnostic: Diagnostic): ProblemHighlightType? = when {
    diagnostic.tags?.contains(DiagnosticTag.Unnecessary) == true -> ProblemHighlightType.LIKE_UNUSED_SYMBOL
    diagnostic.tags?.contains(DiagnosticTag.Deprecated) == true -> ProblemHighlightType.LIKE_DEPRECATED
    else -> null
  }

  @ApiStatus.Experimental
  open fun getEnforcedTextAttributes(diagnostic: Diagnostic): TextAttributes? = null

  @ApiStatus.Internal
  @ApiStatus.Experimental
  open fun customizeQuickFixes(diagnostic: Diagnostic, quickFixes: List<IntentionAction>): List<IntentionAction> {
    return quickFixes
  }
}

object LspDiagnosticsDisabled : LspDiagnosticsCustomizer()
