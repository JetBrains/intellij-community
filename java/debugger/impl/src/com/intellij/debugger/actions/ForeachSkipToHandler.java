/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.debugger.impl.ForeachLoop;
import com.intellij.debugger.ui.impl.watch.ArrayElementDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.impl.actions.DebuggerActionHandler;
import org.jetbrains.annotations.NotNull;

/**
 * Created with IntelliJ IDEA.
 * User: zajac
 * Date: 21.02.12
 * Time: 14:10
 * To change this template use File | Settings | File Templates.
 */
public class ForeachSkipToHandler extends DebuggerActionHandler {

  @Override
  public boolean isHidden(@NotNull Project project, AnActionEvent event) {
    final DebuggerTreeNodeImpl node = DebuggerAction.getSelectedNode(event.getDataContext());
    if (node == null) {
      return true;
    }
    final NodeDescriptorImpl descriptor = node.getDescriptor();
    if (descriptor instanceof ArrayElementDescriptorImpl) {
      final ForeachLoop foreach = ((ArrayElementDescriptorImpl)descriptor).getForeach();
      return foreach == null || !foreach.isMultiline();
    }
    return true;
  }

  @Override
  public boolean isEnabled(@NotNull Project project, AnActionEvent event) {
    return !isHidden(project, event);
  }

  @Override
  public void perform(@NotNull Project project, AnActionEvent event) {
    final DebuggerTreeNodeImpl node = DebuggerAction.getSelectedNode(event.getDataContext());
    final NodeDescriptorImpl descriptor = node.getDescriptor();
    if (descriptor instanceof ArrayElementDescriptorImpl) {
      final ForeachLoop foreach = ((ArrayElementDescriptorImpl)descriptor).getForeach();
      if (foreach != null) {
        final DebugProcessImpl process = DebuggerAction.getDebuggerContext(event.getDataContext()).getDebugProcess();
        process.getForeachVisualization().skipTo(foreach, (ArrayElementDescriptorImpl)descriptor,
                                                 DebuggerAction.getDebuggerContext(event.getDataContext()));
      }

    }
  }
}
