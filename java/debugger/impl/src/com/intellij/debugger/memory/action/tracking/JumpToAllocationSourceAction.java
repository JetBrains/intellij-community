/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.debugger.memory.action.tracking;

import com.intellij.debugger.DebuggerManager;
import com.intellij.debugger.memory.action.DebuggerTreeAction;
import com.intellij.debugger.memory.ui.StackFramePopup;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.debugger.memory.component.CreationPositionTracker;
import com.intellij.debugger.memory.utils.StackFrameDescriptor;
import com.intellij.debugger.memory.ui.InstancesTree;

import java.util.List;

public class JumpToAllocationSourceAction extends DebuggerTreeAction {
  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setVisible(getStack(e) != null);
  }

  @Override
  protected void perform(XValueNodeImpl node, @NotNull String nodeName, AnActionEvent e) {
    Project project = e.getProject();
    List<StackFrameDescriptor> stack = getStack(e);
    if(project != null && stack != null) {
      XDebugSession session = XDebuggerManager.getInstance(project).getCurrentSession();
      if(session != null) {
        GlobalSearchScope searchScope = DebuggerManager.getInstance(project)
            .getDebugProcess(session.getDebugProcess().getProcessHandler()).getSearchScope();
        new StackFramePopup(project, stack, searchScope).show();
      }
    }
  }

  @Nullable
  private List<StackFrameDescriptor> getStack(AnActionEvent e) {
    Project project = e.getProject();
    XValueNodeImpl selectedNode = getSelectedNode(e.getDataContext());
    ObjectReference ref = selectedNode != null ? getObjectReference(selectedNode) : null;
    if(project == null || ref == null) {
      return null;
    }

    XDebugSession session = e.getData(InstancesTree.DEBUG_SESSION_DATA_KEY);
    CreationPositionTracker tracker = CreationPositionTracker.getInstance(project);
    return session == null || tracker == null ? null : tracker.getStack(session, ref);
  }
}
