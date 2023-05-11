// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.analysis.problemsView.toolWindow

import com.intellij.analysis.problemsView.Problem
import com.intellij.analysis.problemsView.ProblemsListener
import com.intellij.analysis.problemsView.ProblemsProvider
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.DocumentMarkupModel.forDocument
import com.intellij.openapi.editor.impl.event.MarkupModelListener
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.EdtInvocationManager
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

internal class HighlightingWatcher(
  private val provider: ProblemsProvider,
  private val listener: ProblemsListener,
  private val file: VirtualFile,
  private val level: Int)
  : MarkupModelListener, Disposable {

  private var disposed: Boolean = false
  private val problems:MutableMap<RangeHighlighter, Problem> = ConcurrentHashMap()
  private var reference: WeakReference<MarkupModelEx>? = null

  init {
    ApplicationManager.getApplication().runReadAction { update() }
  }

  override fun dispose() {
    synchronized(problems) {
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

  fun update() {
    val model = reference?.get() ?: getMarkupModel() ?: return
    val highlighters = model.allHighlighters
    highlighters.forEach { afterAdded(it as RangeHighlighterEx) }
  }

  fun findProblem(highlighter: RangeHighlighter): Problem? = problems[highlighter]

  private fun getHighlightingProblem(highlighter: RangeHighlighter): HighlightingProblem
    = HighlightingProblem(provider, file, highlighter)

  private fun getProblem(highlighter: RangeHighlighter): Problem? = when {
    !isValid(highlighter) -> null
    else -> problems.computeIfAbsent(highlighter) { getHighlightingProblem(highlighter) }
  }

  private fun isValid(highlighter: RangeHighlighter): Boolean {
    val info = highlighter.errorStripeTooltip as? HighlightInfo ?: return false
    return info.description != null && info.severity.myVal >= level
  }

  private fun getMarkupModel(): MarkupModelEx? {
    val document = ProblemsView.getDocument(provider.project, file) ?: return null
    val model = forDocument(document, provider.project, true) as? MarkupModelEx ?: return null
    model.addMarkupModelListener(this, this)
    reference = WeakReference(model)
    return model
  }
}
