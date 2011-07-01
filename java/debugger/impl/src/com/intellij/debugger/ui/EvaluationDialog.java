/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.debugger.ui;

import com.intellij.CommonBundle;
import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.actions.DebuggerActions;
import com.intellij.debugger.engine.evaluation.CodeFragmentFactory;
import com.intellij.debugger.engine.evaluation.DefaultCodeFragmentFactory;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerContextListener;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.PositionUtil;
import com.intellij.debugger.ui.impl.WatchDebuggerTree;
import com.intellij.debugger.ui.impl.WatchPanel;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.EvaluationDescriptor;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiElement;
import com.intellij.xdebugger.XDebuggerBundle;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreeModel;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

public abstract class EvaluationDialog extends DialogWrapper {
  private final MyEvaluationPanel myEvaluationPanel;
  private final Project myProject;
  private final DebuggerContextListener myContextListener;
  private final DebuggerEditorImpl myEditor;
  private final List<Runnable> myDisposeRunnables = new ArrayList<Runnable>();

  public EvaluationDialog(Project project, TextWithImports text) {
    super(project, true);
    myProject = project;
    setModal(false);
    setCancelButtonText(CommonBundle.message("button.close"));
    setOKButtonText(DebuggerBundle.message("button.evaluate"));

    myEvaluationPanel = new MyEvaluationPanel(myProject);

    myEditor = createEditor(DefaultCodeFragmentFactory.getInstance());

    setDebuggerContext(getDebuggerContext());
    initDialogData(text);

    myContextListener = new DebuggerContextListener() {
      public void changeEvent(DebuggerContextImpl newContext, int event) {
        boolean close = true;
        for (DebuggerSession session : DebuggerManagerEx.getInstanceEx(myProject).getSessions()) {
          if (!session.isStopped()) {
            close = false;
            break;
          }
        }

        if(close) {
          close(CANCEL_EXIT_CODE);
        }
        else {
          setDebuggerContext(newContext);
        }
      }
    };
    DebuggerManagerEx.getInstanceEx(myProject).getContextManager().addListener(myContextListener);

    setHorizontalStretch(1f);
    setVerticalStretch(1f);
  }

  protected void doOKAction() {
    if (isOKActionEnabled()) {
      doEvaluate();
    }
  }

  protected void doEvaluate() {
    if (myEditor == null || myEvaluationPanel == null) {
      return;
    }

    myEvaluationPanel.clear();
    TextWithImports codeToEvaluate = getCodeToEvaluate();
    if (codeToEvaluate == null) {
      return;
    }
    try {
      setOKActionEnabled(false);
      NodeDescriptorImpl descriptor = myEvaluationPanel.getWatchTree().addWatch(codeToEvaluate).getDescriptor();
      if (descriptor instanceof EvaluationDescriptor) {
        ((EvaluationDescriptor)descriptor).setCodeFragmentFactory(myEditor.getCurrentFactory());
      }
      myEvaluationPanel.getWatchTree().rebuild(getDebuggerContext());
      descriptor.myIsExpanded = true;
    }
    finally {
      setOKActionEnabled(true);
    }
    getEditor().addRecent(getCodeToEvaluate());

    myEvaluationPanel.getContextManager().getContext().getDebuggerSession().refresh(true);
  }

  @Nullable
  protected TextWithImports getCodeToEvaluate() {
    TextWithImports text = getEditor().getText();
    String s = text.getText();
    if (s != null) {
      s = s.trim();
    }
    if ("".equals(s)) {
      return null;
    }
    return text;
  }

  public JComponent getPreferredFocusedComponent() {
    return myEditor.getPreferredFocusedComponent();
  }

  protected String getDimensionServiceKey() {
    return "#com.intellij.debugger.ui.EvaluationDialog2";
  }

  protected void addDisposeRunnable (Runnable runnable) {
    myDisposeRunnables.add(runnable);
  }

  public void dispose() {
    for (Runnable runnable : myDisposeRunnables) {
      runnable.run();
    }
    myDisposeRunnables.clear();
    myEditor.dispose();
    DebuggerManagerEx.getInstanceEx(myProject).getContextManager().removeListener(myContextListener);
    myEvaluationPanel.dispose();
    super.dispose();
  }

  protected class MyEvaluationPanel extends WatchPanel {
    public MyEvaluationPanel(final Project project) {
      super(project, (DebuggerManagerEx.getInstanceEx(project)).getContextManager());
      final WatchDebuggerTree watchTree = getWatchTree();
      final AnAction setValueAction  = ActionManager.getInstance().getAction(DebuggerActions.SET_VALUE);
      setValueAction.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0)), watchTree);
      registerDisposable(new Disposable() {
        public void dispose() {
          setValueAction.unregisterCustomShortcutSet(watchTree);
        }
      });
      setUpdateEnabled(true);
      getTree().getEmptyText().setText(XDebuggerBundle.message("debugger.no.results"));
    }

    protected ActionPopupMenu createPopupMenu() {
      ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction(DebuggerActions.EVALUATION_DIALOG_POPUP);
      return ActionManager.getInstance().createActionPopupMenu(DebuggerActions.EVALUATION_DIALOG_POPUP, group);
    }

    protected void changeEvent(DebuggerContextImpl newContext, int event) {
      if (event == DebuggerSession.EVENT_REFRESH || event == DebuggerSession.EVENT_REFRESH_VIEWS_ONLY) {
        // in order not to spoil the evaluation result do not re-evaluate the tree
        final TreeModel treeModel = getTree().getModel();
        updateTree(treeModel, (DebuggerTreeNodeImpl)treeModel.getRoot());
      }
    }

    private void updateTree(final TreeModel model, final DebuggerTreeNodeImpl node) {
      if (node == null) {
        return;
      }
      if (node.getDescriptor().myIsExpanded) {
        final int count = model.getChildCount(node);
        for (int idx = 0; idx < count; idx++) {
          final DebuggerTreeNodeImpl child = (DebuggerTreeNodeImpl)model.getChild(node, idx);
          updateTree(model, child);
        }
      }
      node.labelChanged();
    }
  }

  protected void setDebuggerContext(DebuggerContextImpl context) {
    final PsiElement contextElement = PositionUtil.getContextElement(context);
    myEditor.setContext(contextElement);
  }

  protected PsiElement getContext() {
    return myEditor.getContext();
  }

  protected void initDialogData(TextWithImports text) {
    getEditor().setText(text);
    myEvaluationPanel.clear();
  }

  public DebuggerContextImpl getDebuggerContext() {
    return DebuggerManagerEx.getInstanceEx(myProject).getContext();
  }

  public DebuggerEditorImpl getEditor() {
    return myEditor;
  }

  protected abstract DebuggerEditorImpl createEditor(final CodeFragmentFactory factory);

  protected MyEvaluationPanel getEvaluationPanel() {
    return myEvaluationPanel;
  }

  public Project getProject() {
    return myProject;
  }

}
