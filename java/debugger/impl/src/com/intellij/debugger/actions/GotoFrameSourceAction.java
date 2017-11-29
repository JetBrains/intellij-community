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

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.JavaExecutionStack;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.SuspendManagerUtil;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.StackFrameDescriptorImpl;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * @author lex
 */
public abstract class GotoFrameSourceAction extends DebuggerAction{
  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    doAction(dataContext);
  }

  public static void doAction(DataContext dataContext) {
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if(project == null) return;
    StackFrameDescriptorImpl stackFrameDescriptor = getStackFrameDescriptor(dataContext);
    XDebugSession session = XDebugSession.DATA_KEY.getData(dataContext);
    if (stackFrameDescriptor != null && session != null) {
      StackFrameProxyImpl frameProxy = stackFrameDescriptor.getFrameProxy();
      DebugProcessImpl process = (DebugProcessImpl)stackFrameDescriptor.getDebugProcess();
      process.getManagerThread().schedule(new SuspendContextCommandImpl((SuspendContextImpl)session.getSuspendContext()) {
        @Override
        public void contextAction(@NotNull SuspendContextImpl suspendContext) throws Exception {
          ThreadReferenceProxyImpl threadProxy = frameProxy.threadProxy();
          SuspendContextImpl threadSuspendContext = SuspendManagerUtil.findContextByThread(process.getSuspendManager(), threadProxy);
          JavaExecutionStack executionStack =
            new JavaExecutionStack(threadProxy, process, Objects.equals(threadSuspendContext.getThread(), threadProxy));
          executionStack.initTopFrame();
          XStackFrame frame = executionStack.createStackFrame(frameProxy);
          DebuggerUIUtil.invokeLater(() -> session.setCurrentStackFrame(executionStack, frame));
        }
      });
    }
  }

  public void update(AnActionEvent e) {
    e.getPresentation().setVisible(getStackFrameDescriptor(e.getDataContext()) != null);
  }

  private static StackFrameDescriptorImpl getStackFrameDescriptor(DataContext dataContext) {
    DebuggerTreeNodeImpl selectedNode = getSelectedNode(dataContext);
    if(selectedNode == null) return null;
    if(selectedNode.getDescriptor() == null || !(selectedNode.getDescriptor() instanceof StackFrameDescriptorImpl)) return null;
    return (StackFrameDescriptorImpl)selectedNode.getDescriptor();
  }

}
