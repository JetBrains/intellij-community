// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.analysis.problemsView.toolWindow

import com.intellij.analysis.problemsView.Problem
import com.intellij.analysis.problemsView.ProblemsListener
import com.intellij.analysis.problemsView.ProblemsProvider
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.impl.event.MarkupModelListener
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.EdtInvocationManager

internal class ProblemsViewHighlightingWatcher(
  private val provider: ProblemsProvider,
  private val listener: ProblemsListener,
  private val file: VirtualFile,
  document: Document,
  private val level: Int)
  : MarkupModelListener, Disposable {

  private var disposed: Boolean = false // guarded by EDT
  private val lock = Any()
  private val problems: MutableMap<RangeHighlighter, Problem> = HashMap() // guarded by lock

  init {
    val markupModel = DocumentMarkupModel.forDocument(document, provider.project, true) as MarkupModelEx
    markupModel.addMarkupModelListener(this, this)
    val highlighters = markupModel.allHighlighters
    highlighters.forEach { afterAdded(it as RangeHighlighterEx) }
    Disposer.register(provider, this)
  }

  override fun dispose() {
    synchronized(lock) {
      val list = problems.values.toList()
      problems.clear()
      list
    }.forEach { listener.problemDisappeared(it) }
    disposed = true
  }

  override fun afterAdded(highlighter: RangeHighlighterEx) {
    val problem = getProblem(highlighter)
    if (problem != null) {
      EdtInvocationManager.invokeLaterIfNeeded {
        if (!disposed) {
          listener.problemAppeared(problem)
        }
      }
    }
  }

  override fun beforeRemoved(highlighter: RangeHighlighterEx) {
    val problem = getProblem(highlighter)
      if (problem != null) {
        EdtInvocationManager.invokeLaterIfNeeded {
          if (!disposed) {
            listener.problemDisappeared(problem)
            synchronized(lock) {
              problems.remove(highlighter)
            }
          }
        }
      }
  }

  override fun attributesChanged(highlighter: RangeHighlighterEx, renderersChanged: Boolean, fontStyleOrColorChanged: Boolean) {
    val problem = getProblem(highlighter)
    if (problem != null) {
      EdtInvocationManager.invokeLaterIfNeeded {
        if (!disposed) {
          listener.problemUpdated(problem)
        }
      }
    }
  }

  fun findProblem(highlighter: RangeHighlighter): Problem? {
    synchronized(lock) {
      return problems[highlighter]
    }
  }

  private fun getProblem(highlighter: RangeHighlighter): Problem? = when {
    !isValid(highlighter) -> null
    else -> {
      synchronized(lock) {
        problems.computeIfAbsent(highlighter) { HighlightingProblem(provider, file, highlighter) }
      }
    }
  }

  private fun isValid(highlighter: RangeHighlighter): Boolean {
    val info = HighlightInfo.fromRangeHighlighter(highlighter) ?: return false
    return info.description != null && info.severity.myVal >= level
  }

}
