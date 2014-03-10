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
package com.intellij.debugger.ui.impl;

import com.intellij.debugger.actions.DebuggerAction;
import com.intellij.debugger.actions.DebuggerActions;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.DebuggerStateManager;
import com.intellij.debugger.ui.impl.watch.DebuggerTree;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.SystemProperties;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.EvaluationMode;
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.impl.frame.XVariablesViewBase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

public class VariablesPanel extends DebuggerTreePanel implements DataProvider {
  @NonNls private static final String HELP_ID = "debugging.debugFrame";

  private static final String TREE = "tree";
  private static final String X_TREE = "xTree";
  private final JPanel myCards;
  private final MyXVariablesView myXTree;

  public VariablesPanel(Project project, DebuggerStateManager stateManager, Disposable parent) {
    super(project, stateManager);

    setBorder(null);

    final FrameVariablesTree frameTree = getFrameTree();

    myCards = new JPanel(new CardLayout());
    myCards.add(frameTree, TREE);

    myXTree = new MyXVariablesView(project);
    registerDisposable(myXTree);
    myCards.add(myXTree.getTree(), X_TREE);

    add(ScrollPaneFactory.createScrollPane(myCards), BorderLayout.CENTER);
    registerDisposable(DebuggerAction.installEditAction(frameTree, DebuggerActions.EDIT_NODE_SOURCE));

    overrideShortcut(frameTree, DebuggerActions.COPY_VALUE, CommonShortcuts.getCopy());
    overrideShortcut(frameTree, DebuggerActions.SET_VALUE, new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0)));

    new ValueNodeDnD(myTree, parent);
  }

  @Override
  protected DebuggerTree createTreeView() {
    return new FrameVariablesTree(getProject(), SystemProperties.getBooleanProperty("java.debugger.xTree", true) ? this : null);
  }

  @Override
  protected void changeEvent(DebuggerContextImpl newContext, int event) {
    if (event != DebuggerSession.EVENT_THREADS_REFRESH) {
      super.changeEvent(newContext, event);
    }
  }

  @Override
  protected ActionPopupMenu createPopupMenu() {
    ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction(DebuggerActions.FRAME_PANEL_POPUP);
    return ActionManager.getInstance().createActionPopupMenu(DebuggerActions.FRAME_PANEL_POPUP, group);
  }

  @Override
  public Object getData(String dataId) {
    if (PlatformDataKeys.HELP_ID.is(dataId)) {
      return HELP_ID;
    }
    return super.getData(dataId);
  }

  public FrameVariablesTree getFrameTree() {
    return (FrameVariablesTree)getTree();
  }

  public void stackChanged(@Nullable final XStackFrame xStackFrame) {
    AppUIUtil.invokeOnEdt(new Runnable() {
      @Override
      public void run() {
        myXTree.stackChanged(xStackFrame);
        ((CardLayout)(myCards.getLayout())).show(myCards, xStackFrame == null ? TREE : X_TREE);
      }
    });
  }

  private static final class MyXVariablesView extends XVariablesViewBase {
    private XStackFrame myCurrentXStackFrame;

    public MyXVariablesView(@NotNull Project project) {
      super(project, new XDebuggerEditorsProvider() {
        @NotNull
        @Override
        public FileType getFileType() {
          throw new UnsupportedOperationException();
        }

        @NotNull
        @Override
        public Document createDocument(@NotNull Project project, @NotNull String text, @Nullable XSourcePosition sourcePosition, @NotNull EvaluationMode mode) {
          throw new UnsupportedOperationException();
        }
      }, null);
    }

    public void stackChanged(@Nullable XStackFrame stackFrame) {
      if (myCurrentXStackFrame != null) {
        saveCurrentTreeState(stackFrame);
      }

      myCurrentXStackFrame = stackFrame;
      if (stackFrame == null) {
        getTree().setSourcePosition(null);
      }
      else {
        buildTreeAndRestoreState(stackFrame);
      }
    }
  }
}
