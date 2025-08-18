// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.actions

import com.intellij.debugger.engine.JavaValue
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl
import com.intellij.debugger.impl.DebuggerUtilsAsync
import com.intellij.debugger.ui.tree.render.NodeRenderer
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.DumbAware
import com.intellij.util.containers.ContainerUtil
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase
import one.util.streamex.StreamEx
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CompletableFuture

class ViewAsGroup : ActionGroup(Presentation.NULL_STRING, true), DumbAware {
  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  private class RendererAction(private val nodeRenderer: NodeRenderer) : ToggleAction(nodeRenderer.getName()) {
    init {
      getTemplatePresentation().setKeepPopupOnPerform(KeepPopupOnPerform.IfRequested)
    }

    override fun isSelected(e: AnActionEvent): Boolean {
      val values = getSelectedValues(e)
      if (values.isEmpty()) {
        return false
      }
      for (value in values) {
        if (value.descriptor.lastRenderer !== nodeRenderer) {
          return false
        }
      }
      return true
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.EDT
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      if (!state) return

      val debuggerContext = DebuggerAction.getDebuggerContext(e.dataContext)
      val values = getSelectedValues(e)
      val selectedNodes = XDebuggerTreeActionBase.getSelectedNodes(e.dataContext)

      LOG.assertTrue(!values.isEmpty())

      val managerThread = debuggerContext.getManagerThread() ?: return

      managerThread.schedule(object : DebuggerContextCommandImpl(debuggerContext) {
        override fun threadAction(suspendContext: SuspendContextImpl) {
          for (node in selectedNodes) {
            val container = node.valueContainer
            if (container is JavaValue) {
              container.setRenderer(nodeRenderer, node)
            }
          }
        }
      })
    }
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    if (e == null) {
      return EMPTY_ARRAY
    }
    val debuggerContext = DebuggerAction.getDebuggerContext(e.dataContext)
    if (debuggerContext.debugProcess == null) {
      return EMPTY_ARRAY
    }

    val values = getSelectedValues(e)
    if (!values.isEmpty()) {
      val future = CompletableFuture<List<NodeRenderer>>()
      val scheduled = debuggerContext.getManagerThread()!!.schedule(object : DebuggerContextCommandImpl(debuggerContext) {
        override fun threadAction(suspendContext: SuspendContextImpl) {
          getApplicableRenderers(values)
            .whenComplete { renderers, throwable ->
              DebuggerUtilsAsync.completeFuture(renderers, throwable, future)
            }
        }

        override fun commandCancelled() {
          future.cancel(false)
        }
      })
      if (scheduled) {
        val rs = ProgressIndicatorUtils.awaitWithCheckCanceled(future)
        if (ContainerUtil.isEmpty(rs)) {
          return EMPTY_ARRAY
        }
        val children = mutableListOf<AnAction>()
        val actionManager = ActionManager.getInstance()
        val viewAsActions = (actionManager.getAction("Debugger.Representation") as DefaultActionGroup)
          .getChildren(actionManager)
        for (viewAsAction in viewAsActions) {
          if (viewAsAction is AutoRendererAction) {
            if (rs.size > 1) {
              children.add(viewAsAction)
            }
          }
          else {
            children.add(viewAsAction)
          }
        }

        if (!children.isEmpty()) {
          children.add(Separator.getInstance())
        }
        children.addAll(rs.map { nodeRenderer -> RendererAction(nodeRenderer) })
        return children.toTypedArray()
      }
    }
    return EMPTY_ARRAY
  }

  override fun update(event: AnActionEvent) {
    val debuggerContext = DebuggerAction.getDebuggerContext(event.dataContext)
    if (getSelectedValues(event).isEmpty() || debuggerContext.debugProcess == null) {
      event.presentation.setEnabledAndVisible(false)
      return
    }
    event.presentation.setEnabledAndVisible(true)
  }

  companion object {
    private val LOG = Logger.getInstance(ViewAsGroup::class.java)

    private fun getApplicableRenderers(values: List<JavaValue>): CompletableFuture<List<NodeRenderer>> {
      val futures = mutableListOf<CompletableFuture<List<NodeRenderer>>>()
      for (value in values) {
        val completedFuture = getApplicableNodeRenderers(value)
        futures.add(completedFuture)
      }

      return CompletableFuture.allOf(*futures.toTypedArray()).thenApply {
        var res: MutableList<NodeRenderer>? = null
        for (future in futures) {
          val list = future.join()
          if (res == null) {
            res = list.toMutableList()
          }
          else {
            res.retainAll(list)
          }
        }
        res ?: emptyList()
      }
    }

    @ApiStatus.Internal
    fun getApplicableNodeRenderers(value: JavaValue): CompletableFuture<List<NodeRenderer>> {
      if (value is JavaReferringObjectsValue) { // disable for any referrers at all
        return CompletableFuture.completedFuture(emptyList())
      }
      val valueDescriptor = value.descriptor
      if (!valueDescriptor.isValueValid) {
        return CompletableFuture.completedFuture(emptyList())
      }
      val process = value.evaluationContext.debugProcess
      return process.getApplicableRenderers(valueDescriptor.getType())
    }

    @JvmStatic
    fun getSelectedValues(event: AnActionEvent): List<JavaValue> {
      val selectedNodes = XDebuggerTree.getSelectedNodes(event.dataContext)
      return StreamEx.of(selectedNodes)
        .map { obj -> obj.valueContainer }
        .select(JavaValue::class.java)
        .toList()
    }
  }
}
