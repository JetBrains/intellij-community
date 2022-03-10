// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.actions;

import com.intellij.CommonBundle;
import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.JavaValue;
import com.intellij.debugger.memory.ui.CollectionHistoryView;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.xdebugger.XDebugProcess;
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

import javax.swing.*;
import java.util.List;

@ApiStatus.Experimental
public class ShowCollectionHistoryAction extends XFetchValueActionBase {

  @Override
  public void update(@NotNull AnActionEvent e) {
    if (!Registry.is("debugger.collection.watchpoints.enabled")) {
      Presentation presentation = e.getPresentation();
      presentation.setEnabledAndVisible(false);
      return;
    }
    super.update(e);
    if (getNode(e) != null) {
      e.getPresentation().setText(ActionsBundle.messagePointer("action.Debugger.ShowCollectionHistory.text"));
    }
  }

  @NotNull
  @Override
  protected ValueCollector createCollector(@NotNull AnActionEvent e) {
    XDebugSession session = e.getData(XDebugSession.DATA_KEY);
    XValueNodeImpl node = getNode(e);
    return new ValueCollector(XDebuggerTree.getTree(e.getDataContext())) {
      @Override
      public void handleInCollector(Project project, String value, XDebuggerTree tree) {
        XDebugProcess process = session == null ? null : session.getDebugProcess();
        CollectionHistoryDialog dialog = new CollectionHistoryDialog(null, null, project, process, node);
        dialog.setTitle(JavaDebuggerBundle.message("show.collection.history.dialog.title"));
        dialog.show();
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
      if (container instanceof JavaValue) {
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

  public static final class CollectionHistoryDialog extends DialogWrapper {
    private final XValueNodeImpl myNode;
    private final XDebugProcess myDebugProcess;
    private final String myClsName;
    private final String myFieldName;

    public CollectionHistoryDialog(String clsName, String fieldName, Project project, XDebugProcess debugProcess, XValueNodeImpl node) {
      super(project, false);
      myNode = node;
      setModal(false);
      setCancelButtonText(CommonBundle.message("button.without.mnemonic.close"));
      setCrossClosesWindow(true);
      myDebugProcess = debugProcess;
      myClsName = clsName;
      myFieldName = fieldName;
      init();
    }

    @Override
    protected Action @NotNull [] createActions() {
      return myNode != null ? new Action[]{getOKAction(), getCancelAction()} : new Action[]{getCancelAction()};
    }

    @Override
    protected String getDimensionServiceKey() {
      return "#com.intellij.debugger.actions.ViewTextAction";
    }

    @Override
    protected JComponent createCenterPanel() {
      return new CollectionHistoryView(myClsName, myFieldName, myDebugProcess, myNode).getComponent();
    }
  }
}