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
import com.intellij.debugger.impl.DebuggerContextUtil;
import com.intellij.debugger.impl.DebuggerStateManager;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.ui.impl.watch.DebuggerTree;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.StackFrameDescriptorImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NonNls;

import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

public class ThreadsPanel extends DebuggerTreePanel{
  @NonNls private static final String HELP_ID = "debugging.debugThreads";

  public ThreadsPanel(Project project, DebuggerStateManager stateManager) {
    super(project, stateManager);

    final Disposable disposable = DebuggerAction.installEditAction(getThreadsTree(), DebuggerActions.EDIT_FRAME_SOURCE);
    registerDisposable(disposable);

    getThreadsTree().addKeyListener(new KeyAdapter() {
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER && getThreadsTree().getSelectionCount() == 1) {
          DebuggerTreeNodeImpl node = (DebuggerTreeNodeImpl)getThreadsTree().getLastSelectedPathComponent();
          if (node != null) {
            NodeDescriptorImpl descriptor = node.getDescriptor();
            if (descriptor instanceof StackFrameDescriptorImpl) {
              selectFrame(node);
            }
          }
        }
      }
    });
    add(new JBScrollPane(getThreadsTree()), BorderLayout.CENTER);
  }

  protected DebuggerTree createTreeView() {
    return new ThreadsDebuggerTree(getProject());
  }

  protected ActionPopupMenu createPopupMenu() {
    DefaultActionGroup group = (DefaultActionGroup)ActionManager.getInstance().getAction(DebuggerActions.THREADS_PANEL_POPUP);
    return ActionManager.getInstance().createActionPopupMenu(DebuggerActions.THREADS_PANEL_POPUP, group);
  }

  public Object getData(String dataId) {
    if (PlatformDataKeys.HELP_ID.is(dataId)) {
      return HELP_ID;
    }
    return super.getData(dataId);
  }

  private void selectFrame(DebuggerTreeNodeImpl node) {
    StackFrameProxyImpl frame = ((StackFrameDescriptorImpl)node.getDescriptor()).getFrameProxy();
    DebuggerContextUtil.setStackFrame(getContextManager(), frame);
  }

  public ThreadsDebuggerTree getThreadsTree() {
    return (ThreadsDebuggerTree) getTree();
  }
}
