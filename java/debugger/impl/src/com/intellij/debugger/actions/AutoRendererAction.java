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

import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;

public class AutoRendererAction extends AnAction{
  public void actionPerformed(AnActionEvent e) {
    final DebuggerContextImpl debuggerContext = DebuggerAction.getDebuggerContext(e.getDataContext());
    final DebuggerTreeNodeImpl[] selectedNodes = DebuggerAction.getSelectedNodes(e.getDataContext());

    if(debuggerContext != null && debuggerContext.getDebugProcess() != null) {
      debuggerContext.getDebugProcess().getManagerThread().schedule(new DebuggerContextCommandImpl(debuggerContext) {
          public void threadAction() {
            for (int i = 0; i < selectedNodes.length; i++) {
              DebuggerTreeNodeImpl selectedNode = selectedNodes[i];
              NodeDescriptorImpl descriptor = selectedNode.getDescriptor();
              if (descriptor instanceof ValueDescriptorImpl) {
                ((ValueDescriptorImpl) descriptor).setRenderer(null);
                selectedNode.calcRepresentation();
              }
            }
          }
        });
    }
  }
}
