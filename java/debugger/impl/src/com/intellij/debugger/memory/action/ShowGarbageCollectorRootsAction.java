// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.action;

import com.intellij.debugger.actions.JavaReferringObjectsValue;
import com.intellij.debugger.engine.JavaValue;
import com.intellij.debugger.engine.ReferringObjectsProvider;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.memory.agent.MemoryAgent;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.impl.XDebuggerManagerImpl;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.XInspectDialog;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.NotNull;

public class ShowGarbageCollectorRootsAction extends NativeAgentActionBase {
  @Override
  protected void perform(@NotNull MemoryAgent memoryAgent,
                         @NotNull ObjectReference reference,
                         @NotNull XValueNodeImpl node) throws EvaluateException {
    ReferringObjectsProvider roots = memoryAgent.canFindGcRoots() ? memoryAgent.findGcRoots(reference) : null;
    if (roots == null) {
      XDebuggerManagerImpl.NOTIFICATION_GROUP.createNotification("This feature is unavailable", NotificationType.INFORMATION);
      return;
    }
    ApplicationManager.getApplication().invokeLater(
      () -> {
        XDebuggerTree tree = node.getTree();
        JavaValue javaValue = (JavaValue)node.getValueContainer();
        XDebugSession session = javaValue.getEvaluationContext().getDebugProcess().getSession().getXDebugSession();
        JavaReferringObjectsValue value = new JavaReferringObjectsValue(javaValue, roots, false);
        XInspectDialog dialog =
          new XInspectDialog(tree.getProject(), tree.getEditorsProvider(), tree.getSourcePosition(), StringUtil.notNullize(node.getName()),
                             value, tree.getValueMarkers(),
                             session, false);
        dialog.setTitle("Paths to GC Roots");
        dialog.show();
      }
    );
  }

  @Override
  protected boolean isEnabled(@NotNull MemoryAgent agent) {
    return agent.canFindGcRoots();
  }
}
