// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeHighlighting.BackgroundEditorHighlighter
import com.intellij.codeHighlighting.Pass
import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.intellij.psi.PsiCompiledFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.PsiDocumentManagerBase
import com.intellij.psi.impl.PsiFileEx
import com.intellij.util.ArrayUtilRt
import com.intellij.util.containers.toArray
import java.util.concurrent.CancellationException

private val IGNORE_FOR_COMPILED = intArrayOf(
  Pass.UPDATE_FOLDING,
  Pass.POPUP_HINTS,
  Pass.LOCAL_INSPECTIONS,
  Pass.WHOLE_FILE_LOCAL_INSPECTIONS,
  Pass.EXTERNAL_TOOLS)

class TextEditorBackgroundHighlighter(private val project: Project, private val editor: Editor) : BackgroundEditorHighlighter {
  private val document = editor.getDocument()

  private fun renewFile(): PsiFile? {
    val file = PsiDocumentManager.getInstance(project).getPsiFile(document) ?: return null
    file.putUserData(PsiFileEx.BATCH_REFERENCE_PROCESSING, true)
    return file
  }

  fun getPasses(passesToIgnore: IntArray): List<TextEditorHighlightingPass> {
    if (project.isDisposed()) {
      return emptyList()
    }

    val documentManager = PsiDocumentManager.getInstance(project) as PsiDocumentManagerBase
    if (!documentManager.isCommitted(document)) {
      logger<TextEditorBackgroundHighlighter>().error("$document; ${documentManager.someDocumentDebugInfo(document)}")
    }

    var file = renewFile() ?: return emptyList()

    var effectivePassesToIgnore = passesToIgnore
    if (file is PsiCompiledFile) {
      file = file.getDecompiledPsiFile()
      effectivePassesToIgnore = IGNORE_FOR_COMPILED
    }
    else if (!DaemonCodeAnalyzer.getInstance(project).isHighlightingAvailable(file)) {
      return emptyList()
    }

    HighlightingPassTracer.HIGHLIGHTING_PASS_TRACER.spanBuilder("passes instantiation").use { span ->
      val startupActivity = StartUpMeasurer.startActivity("highlighting passes instantiation")
      var cancelled = false
      try {
        val passRegistrar = TextEditorHighlightingPassRegistrarEx.getInstanceEx(project)
        return passRegistrar.instantiatePasses(file, editor, effectivePassesToIgnore)
      }
      catch (e: ProcessCanceledException) {
        cancelled = true
        throw e
      }
      catch (e: CancellationException) {
        cancelled = true
        throw e
      }
      finally {
        startupActivity.end()
        span.setAttribute(HighlightingPassTracer.FILE_ATTR_SPAN_KEY, file.getName())
        span.setAttribute(HighlightingPassTracer.FILE_ATTR_SPAN_KEY, cancelled.toString())
      }
    }
  }

  override fun createPassesForEditor(): Array<TextEditorHighlightingPass> {
    val passes = getPasses(ArrayUtilRt.EMPTY_INT_ARRAY)
    return if (passes.isEmpty()) TextEditorHighlightingPass.EMPTY_ARRAY else passes.toArray(TextEditorHighlightingPass.EMPTY_ARRAY)
  }
}