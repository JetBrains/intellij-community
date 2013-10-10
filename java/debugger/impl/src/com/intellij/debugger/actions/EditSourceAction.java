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
package com.intellij.debugger.actions;

import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.evaluation.expression.Modifier;
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.ui.impl.watch.*;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;

public class EditSourceAction extends DebuggerAction{
  public void actionPerformed(AnActionEvent e) {
    final Project project = CommonDataKeys.PROJECT.getData(e.getDataContext());

    if(project == null) return;

    final DebuggerContextImpl debuggerContext = getDebuggerContext(e.getDataContext());
    final DebuggerTreeNodeImpl selectedNode = getSelectedNode(e.getDataContext());
    if(debuggerContext != null && selectedNode != null) {
      debuggerContext.getDebugProcess().getManagerThread().schedule(new DebuggerContextCommandImpl(debuggerContext) {
          public void threadAction() {
            final SourcePosition sourcePosition = getSourcePosition(selectedNode, debuggerContext);
            if (sourcePosition != null) {
              sourcePosition.navigate(true);
            }
          }
        });
    }
  }

  private SourcePosition getSourcePosition(DebuggerTreeNodeImpl selectedNode, DebuggerContextImpl debuggerContext) {
    DebuggerTreeNodeImpl node = selectedNode;
    final DebuggerContextImpl context = debuggerContext;

    if(node == null) return null;
    if(context == null) return null;

    final Project project = context.getProject();

    final DebuggerSession debuggerSession = context.getDebuggerSession();

    if(debuggerSession == null) return null;

    NodeDescriptorImpl nodeDescriptor = node.getDescriptor();
    if(nodeDescriptor instanceof WatchItemDescriptor) {
      Modifier modifier = ((WatchItemDescriptor)nodeDescriptor).getModifier();

      if(modifier == null) return null;

      nodeDescriptor = (NodeDescriptorImpl)modifier.getInspectItem(project);
    }

    final NodeDescriptorImpl nodeDescriptor1 = nodeDescriptor;
    return ApplicationManager.getApplication().runReadAction(new Computable<SourcePosition>() {
      public SourcePosition compute() {
        if (nodeDescriptor1 instanceof FieldDescriptorImpl && debuggerSession != null) {
          return ((FieldDescriptorImpl)nodeDescriptor1).getSourcePosition(project, context);
        }
        if (nodeDescriptor1 instanceof LocalVariableDescriptorImpl && debuggerSession != null) {
          return ((LocalVariableDescriptorImpl)nodeDescriptor1).getSourcePosition(project, context);
        }
        return null;
      }
    });
  }

  public void update(AnActionEvent e) {
    final Project project = CommonDataKeys.PROJECT.getData(e.getDataContext());

    final DebuggerContextImpl debuggerContext = getDebuggerContext(e.getDataContext());
    final DebuggerTreeNodeImpl node = getSelectedNode(e.getDataContext());

    final Presentation presentation = e.getPresentation();

    presentation.setEnabled(true);

    //if user used shortcut actionPerformed is called immediately after update
    //we not disable presentation here to allow actionPerform work
    DebuggerInvocationUtil.invokeLater(project, new Runnable() {
      public void run() {
        presentation.setEnabled(false);
      }
    });

    if(debuggerContext != null && debuggerContext.getDebugProcess() != null) {
      debuggerContext.getDebugProcess().getManagerThread().schedule(new DebuggerContextCommandImpl(debuggerContext) {
        public void threadAction() {
          final SourcePosition position = getSourcePosition(node, debuggerContext);
          if (position != null) {
            DebuggerInvocationUtil.swingInvokeLater(project, new Runnable() {
              public void run() {
                presentation.setEnabled(true);
              }
            });
          }
        }
      });
    }

    e.getPresentation().setText(ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_SOURCE).getTemplatePresentation().getText());
  }

}
