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
import com.intellij.openapi.vfs.VirtualFile
import java.lang.ref.WeakReference

internal class HighlightingWatcher(
  private val provider: ProblemsProvider,
  private val listener: ProblemsListener,
  private val file: VirtualFile,
  private val level: Int)
  : MarkupModelListener, Disposable {

  private val problems = mutableMapOf<RangeHighlighterEx, Problem>()
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
  }

  override fun afterAdded(highlighter: RangeHighlighterEx) {
    getProblem(highlighter)?.let { listener.problemAppeared(it) }
  }

  override fun beforeRemoved(highlighter: RangeHighlighterEx) {
    getProblem(highlighter)?.let { listener.problemDisappeared(it) }
  }

  override fun attributesChanged(highlighter: RangeHighlighterEx, renderersChanged: Boolean, fontStyleOrColorChanged: Boolean) {
    findProblem(highlighter)?.let { listener.problemUpdated(it) }
  }

  fun update() {
    val model = reference?.get() ?: getMarkupModel() ?: return
    val highlighters = arrayListOf<RangeHighlighterEx>()
    model.processRangeHighlightersOverlappingWith(0, model.document.textLength) { highlighter: RangeHighlighterEx ->
      highlighters.add(highlighter)
      true
    }
    highlighters.forEach { afterAdded(it) }
  }

  fun getProblems(): Collection<Problem> = synchronized(problems) { problems.values.toList() }

  fun findProblem(highlighter: RangeHighlighterEx): Problem? = synchronized(problems) { problems[highlighter] }

  private fun getHighlightingProblem(highlighter: RangeHighlighterEx): HighlightingProblem
    = HighlightingProblem(provider, file, highlighter)

  private fun getProblem(highlighter: RangeHighlighterEx): Problem? = when {
    !isValid(highlighter) -> null
    else -> synchronized(problems) {
      problems.computeIfAbsent(highlighter) { getHighlightingProblem(highlighter) }
    }
  }

  private fun isValid(highlighter: RangeHighlighterEx): Boolean {
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
