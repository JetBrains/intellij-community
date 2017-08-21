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
package com.intellij.debugger.actions;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.ThreadDescriptorImpl;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.xdebugger.impl.XDebuggerManagerImpl;

import java.util.ArrayList;
import java.util.List;

public class InterruptThreadAction extends DebuggerAction{
  
  public void actionPerformed(final AnActionEvent e) {
    final DebuggerTreeNodeImpl[] nodes = getSelectedNodes(e.getDataContext());
    if (nodes == null) {
      return;
    }

    //noinspection ConstantConditions
    final List<ThreadReferenceProxyImpl> threadsToInterrupt = new ArrayList<>();
    for (final DebuggerTreeNodeImpl debuggerTreeNode : nodes) {
      final NodeDescriptorImpl descriptor = debuggerTreeNode.getDescriptor();
      if (descriptor instanceof ThreadDescriptorImpl) {
        threadsToInterrupt.add(((ThreadDescriptorImpl)descriptor).getThreadReference());
      }
    }
    
    if (!threadsToInterrupt.isEmpty()) {
      final DebuggerContextImpl debuggerContext = getDebuggerContext(e.getDataContext());
      final DebugProcessImpl debugProcess = debuggerContext.getDebugProcess();
      if (debugProcess != null) {
        debugProcess.getManagerThread().schedule(new DebuggerCommandImpl() {
          protected void action() throws Exception {
            boolean unsupported = false;
            for (ThreadReferenceProxyImpl thread : threadsToInterrupt) {
              try {
                thread.getThreadReference().interrupt();
              }
              catch (UnsupportedOperationException ignored) {
                unsupported = true;
              }
            }
            if (unsupported) {
              final Project project = debugProcess.getProject();
              XDebuggerManagerImpl.NOTIFICATION_GROUP
                .createNotification("Thread operation 'interrupt' is not supported by VM", MessageType.INFO).notify(project);
            }
          }
        });
      }
    }
  }

  public void update(AnActionEvent e) {
    final DebuggerTreeNodeImpl[] selectedNodes = getSelectedNodes(e.getDataContext());

    boolean visible = false;
    boolean enabled = false;

    if(selectedNodes != null && selectedNodes.length > 0){
      visible = true;
      enabled = true;
      for (DebuggerTreeNodeImpl selectedNode : selectedNodes) {
        final NodeDescriptorImpl threadDescriptor = selectedNode.getDescriptor();
        if (!(threadDescriptor instanceof ThreadDescriptorImpl)) {
          visible = false;
          break;
        }
      }
      
      if (visible) {
        for (DebuggerTreeNodeImpl selectedNode : selectedNodes) {
          final ThreadDescriptorImpl threadDescriptor = (ThreadDescriptorImpl)selectedNode.getDescriptor();
          if (threadDescriptor.isFrozen() || threadDescriptor.isSuspended()) {
            enabled = false;
            break;
          }
        }
      }
    }
    final Presentation presentation = e.getPresentation();
    presentation.setText(DebuggerBundle.message("action.interrupt.thread.text"));
    presentation.setEnabledAndVisible(visible && enabled);
  }
}
