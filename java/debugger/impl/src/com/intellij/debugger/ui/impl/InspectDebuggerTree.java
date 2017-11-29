/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.actions.DebuggerActions;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.ui.impl.watch.DebuggerTree;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPopupMenu;
import com.intellij.openapi.project.Project;
import com.intellij.ui.PopupHandler;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class InspectDebuggerTree extends DebuggerTree{
  private NodeDescriptorImpl myInspectDescriptor;

  public InspectDebuggerTree(Project project) {
    super(project);

    final PopupHandler popupHandler = new PopupHandler() {
      public void invokePopup(Component comp, int x, int y) {
        ActionPopupMenu popupMenu = createPopupMenu();
        if (popupMenu != null) {
          myTipManager.registerPopup(popupMenu.getComponent()).show(comp, x, y);
        }
      }
    };
    addMouseListener(popupHandler);

    new ValueNodeDnD(this, project);
  }

  public static ActionPopupMenu createPopupMenu() {
    ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction(DebuggerActions.INSPECT_PANEL_POPUP);
    return ActionManager.getInstance().createActionPopupMenu(DebuggerActions.INSPECT_PANEL_POPUP, group);
  }

  protected void build(DebuggerContextImpl context) {
    updateNode(context);
  }

  public void setInspectDescriptor(NodeDescriptorImpl inspectDescriptor) {
    myInspectDescriptor = inspectDescriptor;
  }

  public NodeDescriptorImpl getInspectDescriptor() {
    return myInspectDescriptor;
  }



  private void updateNode(final DebuggerContextImpl context) {
    context.getDebugProcess().getManagerThread().schedule(new DebuggerContextCommandImpl(context) {
      public void threadAction(@NotNull SuspendContextImpl suspendContext) {
        DebuggerTreeNodeImpl node = getNodeFactory().createNode(myInspectDescriptor, context.createEvaluationContext());

        DebuggerInvocationUtil.swingInvokeLater(getProject(), () -> {
          DebuggerTreeNodeImpl root = (DebuggerTreeNodeImpl) getModel().getRoot();
          root.removeAllChildren();

          root.add(node);
          treeChanged();
          root.getTree().expandRow(0);
        });
      }
    });
  }
}
