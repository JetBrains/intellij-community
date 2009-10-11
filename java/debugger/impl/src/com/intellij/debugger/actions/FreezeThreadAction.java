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

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.ThreadDescriptorImpl;
import com.intellij.openapi.actionSystem.AnActionEvent;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Apr 14, 2004
 * Time: 3:35:58 PM
 * To change this template use File | Settings | File Templates.
 */
public class FreezeThreadAction extends DebuggerAction{
  public void actionPerformed(final AnActionEvent e) {
    DebuggerTreeNodeImpl[] selectedNode = getSelectedNodes(e.getDataContext());
    final DebuggerContextImpl debuggerContext = getDebuggerContext(e.getDataContext());
    final DebugProcessImpl debugProcess = debuggerContext.getDebugProcess();

    for (int i = 0; i < selectedNode.length; i++) {
      final DebuggerTreeNodeImpl debuggerTreeNode = selectedNode[i];
      ThreadDescriptorImpl threadDescriptor = ((ThreadDescriptorImpl)debuggerTreeNode.getDescriptor());
      final ThreadReferenceProxyImpl thread = threadDescriptor.getThreadReference();

      if(!threadDescriptor.isFrozen()) {
        debugProcess.getManagerThread().schedule(new SuspendContextCommandImpl(debuggerContext.getSuspendContext()) {
              public void contextAction() throws Exception {
                debugProcess.createFreezeThreadCommand(thread).run();
                debuggerTreeNode.calcValue();
              }
            });
      }
    }
  }

  public void update(AnActionEvent e) {
    DebuggerTreeNodeImpl[] selectedNode = getSelectedNodes(e.getDataContext());
    DebugProcessImpl debugProcess = getDebuggerContext(e.getDataContext()).getDebugProcess();

    boolean visible = false;
    if(debugProcess != null) {
      visible = true;
      for (int i = 0; i < selectedNode.length; i++) {
        NodeDescriptorImpl threadDescriptor = selectedNode[i].getDescriptor();
        if(!(threadDescriptor instanceof ThreadDescriptorImpl) || ((ThreadDescriptorImpl)threadDescriptor).isFrozen()) {
          visible = false;
          break;
        }
      }

    }

    e.getPresentation().setVisible(visible);
  }
}
