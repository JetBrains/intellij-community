// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.actions;

import com.intellij.debugger.engine.JavaValue;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.ui.tree.FieldDescriptor;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.actions.XDebuggerTreeActionBase;
import com.intellij.xdebugger.impl.ui.tree.actions.XFetchValueActionBase;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.siyeh.ig.psiutils.CollectionUtils;
import com.sun.jdi.Type;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@ApiStatus.Experimental
public class ShowCollectionHistoryAction extends XFetchValueActionBase implements ActionRemoteBehaviorSpecification.Disabled {
  @Override
  public void update(@NotNull AnActionEvent e) {
    if (!Registry.is("debugger.collection.watchpoints.enabled")) {
      Presentation presentation = e.getPresentation();
      presentation.setEnabledAndVisible(false);
      return;
    }
    super.update(e);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  protected @NotNull ValueCollector createCollector(@NotNull AnActionEvent e) {
    XDebugSession session = e.getData(XDebugSession.DATA_KEY);
    XValueNodeImpl node = getNode(e);
    return new ValueCollector(XDebuggerTree.getTree(e.getDataContext())) {
      @Override
      public void handleInCollector(Project project, String value, XDebuggerTree tree) {
        if (session == null || node == null) {
          return;
        }
        DebuggerUtilsEx.addCollectionHistoryTab(session, node);
      }
    };
  }

  @Override
  protected void handle(Project project,
                        String value,
                        XDebuggerTree tree) { }

  private static XValueNodeImpl getNode(@NotNull AnActionEvent e) {
    List<XValueNodeImpl> selectedNodes = XDebuggerTreeActionBase.getSelectedNodes(e.getDataContext());
    if (selectedNodes.size() == 1) {
      XValueNodeImpl node = selectedNodes.get(0);
      XValue container = node.getValueContainer();
      if (container instanceof JavaValue && ((JavaValue)container).getDescriptor() instanceof FieldDescriptor) {
        Type type = ((JavaValue)container).getDescriptor().getType();
        Project project = e.getProject();
        if (type == null || project == null) {
          return null;
        }
        PsiType psiType = PsiType.getTypeByName(type.name(), project, GlobalSearchScope.allScope(project));
        return CollectionUtils.isCollectionClassOrInterface(psiType) ? node : null;
      }
    }
    return null;
  }
}