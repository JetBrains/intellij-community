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

/*
 * Class WatchPanel
 * @author Jeka
 */
package com.intellij.debugger.ui.impl;

import com.intellij.debugger.actions.DebuggerAction;
import com.intellij.debugger.actions.DebuggerActions;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.DebuggerStateManager;
import com.intellij.debugger.ui.impl.watch.DebuggerTree;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.WatchItemDescriptor;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ScrollPaneFactory;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.util.Enumeration;

public abstract class WatchPanel extends DebuggerTreePanel {
  @NonNls private static final String HELP_ID = "debugging.debugWatches";

  public WatchPanel(Project project, DebuggerStateManager stateManager) {
    super(project, stateManager);
    add(createTreePanel(getWatchTree()), BorderLayout.CENTER);
    registerDisposable(DebuggerAction.installEditAction(getWatchTree(), DebuggerActions.EDIT_NODE_SOURCE));
    overrideShortcut(getWatchTree(), DebuggerActions.COPY_VALUE, CommonShortcuts.getCopy());
  }

  protected JComponent createTreePanel(final WatchDebuggerTree tree) {
    return ScrollPaneFactory.createScrollPane(tree);
  }

  @Override
  protected DebuggerTree createTreeView() {
    return new WatchDebuggerTree(getProject());
  }

  @Override
  protected void changeEvent(DebuggerContextImpl newContext, int event) {
    if (event == DebuggerSession.EVENT_THREADS_REFRESH) {
      return;
    }
    if(event == DebuggerSession.EVENT_ATTACHED) {
      DebuggerTreeNodeImpl root = (DebuggerTreeNodeImpl) getWatchTree().getModel().getRoot();
      if(root != null) {
        for(Enumeration e = root.rawChildren(); e.hasMoreElements();) {
          DebuggerTreeNodeImpl child = (DebuggerTreeNodeImpl) e.nextElement();
          ((WatchItemDescriptor) child.getDescriptor()).setNew();
        }
      }
    }

    rebuildIfVisible(event);
  }

  @Override
  protected ActionPopupMenu createPopupMenu() {
    return null;
  }

  @Override
  public Object getData(String dataId) {
    if (PlatformDataKeys.HELP_ID.is(dataId)) {
      return HELP_ID;
    }
    return super.getData(dataId);
  }

  public WatchDebuggerTree getWatchTree() {
    return (WatchDebuggerTree) getTree();
  }
}
