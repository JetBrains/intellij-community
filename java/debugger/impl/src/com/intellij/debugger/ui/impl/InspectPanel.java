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
 * Class InspectPanel
 * @author Jeka
 */
package com.intellij.debugger.ui.impl;

import com.intellij.debugger.actions.DebuggerAction;
import com.intellij.debugger.actions.DebuggerActions;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.DebuggerStateManager;
import com.intellij.debugger.ui.impl.watch.DebuggerTree;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ScrollPaneFactory;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class InspectPanel extends DebuggerTreePanel {
  public InspectPanel(Project project, DebuggerStateManager stateManager, @NotNull NodeDescriptorImpl inspectDescriptor) {
    super(project, stateManager);

    getInspectTree().setInspectDescriptor(inspectDescriptor);

    add(ScrollPaneFactory.createScrollPane(getInspectTree()), BorderLayout.CENTER);
    registerDisposable(DebuggerAction.installEditAction(getInspectTree(), DebuggerActions.EDIT_NODE_SOURCE));

    overrideShortcut(getInspectTree(), DebuggerActions.COPY_VALUE, CommonShortcuts.getCopy());
    setUpdateEnabled(true);
  }

  protected void changeEvent(DebuggerContextImpl newContext, int event) {
    if (event != DebuggerSession.EVENT_THREADS_REFRESH) {
      super.changeEvent(newContext, event);
    }
  }

  protected DebuggerTree createTreeView() {
    return new InspectDebuggerTree(getProject());
  }

  protected ActionPopupMenu createPopupMenu() {
    return InspectDebuggerTree.createPopupMenu();
  }

  public InspectDebuggerTree getInspectTree() {
    return (InspectDebuggerTree)getTree();
  }
}
