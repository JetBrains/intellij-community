// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.collections.visualizer

import com.intellij.debugger.JavaDebuggerBundle
import com.intellij.debugger.engine.DebugProcess
import com.intellij.debugger.engine.DebugProcessListener
import com.intellij.debugger.engine.FullValueEvaluatorProvider
import com.intellij.debugger.engine.SuspendContext
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.FrameWrapper
import com.intellij.platform.util.coroutines.childScope
import com.intellij.xdebugger.frame.XFullValueEvaluator
import com.intellij.xdebugger.impl.ui.CustomComponentEvaluator
import com.sun.jdi.request.EventRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import java.awt.event.MouseEvent
import javax.swing.JComponent

class CollectionVisualizerEvaluator(private val visualizer: CollectionVisualizer) : FullValueEvaluatorProvider {
  override fun getFullValueEvaluator(evaluationContext: EvaluationContextImpl, valueDescriptor: ValueDescriptorImpl): XFullValueEvaluator? {
    val thread = evaluationContext.suspendContext.thread
    val threadName = thread?.name() ?: "<unknown thread>"
    val presenterName = valueDescriptor.type?.name() ?: "<unknown name>"
    val mainScopeName = "Collection presentation for $presenterName (thread $threadName)"
    val scope = evaluationContext.managerThread.coroutineScope.childScope(mainScopeName)

    return object : CustomComponentEvaluator("CoVi") {
      override fun startEvaluation(callback: XFullValueEvaluationCallback) {
        callback.evaluated("")
      }

      override fun createComponent(fullValue: String?): JComponent? {
        return visualizer.createComponent(evaluationContext.project, valueDescriptor, evaluationContext, scope)
      }

      override fun showValuePopup(event: MouseEvent, project: Project, editor: Editor?, component: JComponent, cancelCallback: Runnable?) {
        val frame = FrameWrapper(
          project,
          dimensionKey = "debugger-collection-visualizer",
          isDialog = false,
          title = JavaDebuggerBundle.message("debugger.collection.visualizer.title"),
          component,
          // don't cancel the whole scope when the window is closed -- otherwise it wouldn't be possible to reopen
          scope.childScope("$mainScopeName (limited to a window)"),
        )
        frame.apply {
          disposeOnResume(evaluationContext, scope)
          closeOnEsc()
          show()
        }
      }
    }
  }

  private fun FrameWrapper.disposeOnResume(
    evaluationContext: EvaluationContextImpl,
    scope: CoroutineScope,
  ) {
    evaluationContext.debugProcess.addDebugProcessListener(object : DebugProcessListener {
      override fun resumed(suspendContext: SuspendContext?) {
        if (affectedByResume(suspendContext)) {
          close()
          scope.cancel()
        }
      }

      override fun processDetached(process: DebugProcess, closedByUser: Boolean) {
        close()
        scope.cancel()
      }

      // maximum conservatism -- better close more popups than make them leak
      private fun affectedByResume(suspendContext: SuspendContext?): Boolean {
        if (suspendContext == null) return true
        if (evaluationContext.suspendContext.suspendPolicy == EventRequest.SUSPEND_ALL) {
          return true
        }
        val visualizerRelatedThread = evaluationContext.suspendContext.thread ?: return true
        val resumeOnThread = suspendContext.thread ?: return true
        return visualizerRelatedThread === resumeOnThread
      }
    })
  }

  companion object {
    @JvmStatic
    fun createFor(collectionClass: String) = CollectionVisualizer.findApplicable(collectionClass)?.let(::CollectionVisualizerEvaluator)
  }
}
