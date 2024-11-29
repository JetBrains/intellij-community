// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.memory.agent.ui;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.JavaDebugProcess;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.memory.agent.MemoryAgent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.actions.XDebuggerActions;
import com.intellij.xdebugger.impl.frame.XValueMarkers;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.debugger.JavaDebuggerBundle;

import javax.swing.tree.TreeNode;
import java.util.HashSet;
import java.util.Set;

public class PathsToClosestGcRootsDialog extends MemoryAgentDialog {
  public PathsToClosestGcRootsDialog(@NotNull Project project,
                                     XDebuggerEditorsProvider editorsProvider,
                                     XSourcePosition sourcePosition,
                                     @NotNull String name,
                                     @NotNull XValue value,
                                     XValueMarkers<?, ?> markers,
                                     @Nullable XDebugSession session,
                                     boolean rebuildOnSessionEvents) {
    super(
      project, name, value, session,
      new XDebuggerTree(project, editorsProvider, sourcePosition, XDebuggerActions.INSPECT_TREE_POPUP_GROUP, markers),
      rebuildOnSessionEvents
    );

    setTitle(JavaDebuggerBundle.message("paths.to.closest.gc.roots.for", name));
    myTree.expandNodesOnLoad(treeNode -> isInTopSubTree(treeNode));

    if (session != null) {
      addProgressIndicator(session);
    }
  }

  private void addProgressIndicator(@NotNull XDebugSession session) {
    XDebugProcess process = session.getDebugProcess();
    if (!(process instanceof JavaDebugProcess javaDebugProcess)) return;

    DebugProcessImpl debugProcess = javaDebugProcess.getDebuggerSession().getProcess();
    SuspendContextImpl suspendContext = debugProcess.getSuspendManager().getPausedContext();
    PathsToClosestGcRootsDialog dialog = this;
    suspendContext.getManagerThread().schedule(new SuspendContextCommandImpl(suspendContext) {
      @Override
      public void contextAction(@NotNull SuspendContextImpl suspendContext) {
        if (dialog.isDisposed()) {
          return;
        }

        EvaluationContextImpl evaluationContext = new EvaluationContextImpl(suspendContext, suspendContext.getFrameProxy());
        MemoryAgent memoryAgent = MemoryAgent.get(evaluationContext);
        Disposer.register(dialog.getDisposable(), () -> memoryAgent.cancelAction());
        memoryAgent.setProgressIndicator(dialog.createProgressIndicator());
      }
    });
  }

  @Override
  @NonNls
  protected String getDimensionServiceKey() {
    return "#javadebugger.PathsToClosestGcRootsDialog";
  }

  private static boolean isInTopSubTree(@NotNull TreeNode node) {
    Set<TreeNode> visited = new HashSet<>();
    while (node.getParent() != null) {
      if (!visited.add(node) || visited.size() > 10) { // stop expanding if recursion detected or we're too deep
        return false;
      }
      if (node != node.getParent().getChildAt(0)) {
        return false;
      }
      node = node.getParent();
    }

    return true;
  }
}
