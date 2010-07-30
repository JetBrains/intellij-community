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
import com.intellij.debugger.impl.DebuggerStateManager;
import com.intellij.debugger.ui.impl.watch.DebuggerTree;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ScrollPaneFactory;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

public class VariablesPanel extends DebuggerTreePanel implements DataProvider{

  @NonNls private static final String HELP_ID = "debugging.debugFrame";

  public VariablesPanel(Project project, DebuggerStateManager stateManager, Disposable parent) {
    super(project, stateManager);
    setBorder(null);


    final FrameDebuggerTree frameTree = getFrameTree();

    add(ScrollPaneFactory.createScrollPane(frameTree), BorderLayout.CENTER);
    registerDisposable(DebuggerAction.installEditAction(frameTree, DebuggerActions.EDIT_NODE_SOURCE));

    overrideShortcut(frameTree, DebuggerActions.COPY_VALUE, CommonShortcuts.getCopy());
    overrideShortcut(frameTree, DebuggerActions.SET_VALUE, new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0)));

    new ValueNodeDnD(myTree, parent);
  }

  protected DebuggerTree createTreeView() {
    return new FrameDebuggerTree(getProject());
  }


  protected ActionPopupMenu createPopupMenu() {
    ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction(DebuggerActions.FRAME_PANEL_POPUP);
    return ActionManager.getInstance().createActionPopupMenu(DebuggerActions.FRAME_PANEL_POPUP, group);
  }

  public Object getData(String dataId) {
    if (PlatformDataKeys.HELP_ID.is(dataId)) {
      return HELP_ID;
    }
    return super.getData(dataId);
  }


  public FrameDebuggerTree getFrameTree() {
    return (FrameDebuggerTree) getTree();
  }

}
