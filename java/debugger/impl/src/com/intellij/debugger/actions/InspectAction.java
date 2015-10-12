/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
 * Class InspectAction
 * @author Jeka
 */
package com.intellij.debugger.actions;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.engine.evaluation.expression.Modifier;
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.impl.DebuggerStateManager;
import com.intellij.debugger.ui.impl.InspectDialog;
import com.intellij.debugger.ui.impl.watch.*;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.sun.jdi.Field;

public class InspectAction extends DebuggerAction {
  public void actionPerformed(AnActionEvent e) {
    final Project project = CommonDataKeys.PROJECT.getData(e.getDataContext());
    final DebuggerTreeNodeImpl node = getSelectedNode(e.getDataContext());
    if(node == null) return;
    final NodeDescriptorImpl descriptor = node.getDescriptor();
    final DebuggerStateManager stateManager = getContextManager(e.getDataContext());
    if(!(descriptor instanceof ValueDescriptorImpl) || stateManager == null) return;
    final DebuggerContextImpl context = stateManager.getContext();

    if (!canInspect((ValueDescriptorImpl)descriptor, context)) {
      return;
    }
    context.getDebugProcess().getManagerThread().schedule(new DebuggerContextCommandImpl(context) {
      public void threadAction() {
        try {
          final TextWithImports evaluationText = DebuggerTreeNodeExpression.createEvaluationText(node, context);

          final NodeDescriptorImpl inspectDescriptor;
          if (descriptor instanceof WatchItemDescriptor) {
            inspectDescriptor = (NodeDescriptorImpl) ((WatchItemDescriptor) descriptor).getModifier().getInspectItem(project);
          }
          else {
            inspectDescriptor = descriptor;
          }

          DebuggerInvocationUtil.swingInvokeLater(project, new Runnable() {
            public void run() {
              final InspectDialog dialog = new InspectDialog(project, stateManager, ActionsBundle.actionText(DebuggerActions.INSPECT) + " '" + evaluationText + "'", inspectDescriptor);
              dialog.show();
            }
          });
        }
        catch (final EvaluateException e1) {
          DebuggerInvocationUtil.swingInvokeLater(project, new Runnable() {
            public void run() {
              Messages.showErrorDialog(project, e1.getMessage(), ActionsBundle.actionText(DebuggerActions.INSPECT));
            }
          });
        }
      }
    });
  }

  private static boolean canInspect(ValueDescriptorImpl descriptor, DebuggerContextImpl context) {
    DebuggerSession session = context.getDebuggerSession();
    if (session == null || !session.isPaused()) return false;

    boolean isField = descriptor instanceof FieldDescriptorImpl;

    if(descriptor instanceof WatchItemDescriptor) {
      Modifier modifier = ((WatchItemDescriptor)descriptor).getModifier();
      if(modifier == null || !modifier.canInspect()) return false;
      isField = modifier instanceof Field;
    }

    if (isField) { // check if possible
      if (!context.getDebugProcess().canWatchFieldModification()) {
        Messages.showMessageDialog(
          context.getProject(),
          DebuggerBundle.message("error.modification.watchpoints.not.supported"),
          ActionsBundle.actionText(DebuggerActions.INSPECT),
          Messages.getInformationIcon()
        );
        return false;
      }
    }
    return true;
  }

  public void update(AnActionEvent e) {
    DebuggerTreeNodeImpl selectedNode = getSelectedNode(e.getDataContext());
    boolean enabled = false;
    if(selectedNode != null) {
      NodeDescriptorImpl descriptor = selectedNode.getDescriptor();
      if(descriptor != null) {
        if(descriptor instanceof LocalVariableDescriptorImpl || descriptor instanceof FieldDescriptorImpl || descriptor instanceof ArrayElementDescriptorImpl) {
          enabled = true;
        }
        else if(descriptor instanceof WatchItemDescriptor){
          Modifier modifier = ((WatchItemDescriptor)descriptor).getModifier();
          enabled = modifier != null && modifier.canInspect();
        }
      }
    }
    e.getPresentation().setVisible(enabled);
  }

}
