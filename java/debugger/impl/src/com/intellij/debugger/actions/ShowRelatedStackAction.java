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
package com.intellij.debugger.actions;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.JavaValue;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.memory.ui.StackFramePopup;
import com.intellij.debugger.memory.utils.StackFrameItem;
import com.intellij.debugger.ui.breakpoints.StackCapturingLineBreakpoint;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * @author egor
 */
public class ShowRelatedStackAction extends AnAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    List<StackFrameItem> stack = getRelatedStack(e);
    if (project != null && stack != null) {
      DebuggerContextImpl debuggerContext = DebuggerAction.getDebuggerContext(e.getDataContext());

      DebugProcessImpl debugProcess = debuggerContext.getDebugProcess();
      if (debugProcess == null) {
        return;
      }

      new StackFramePopup(project, stack, debugProcess.getSearchScope()).show();
    }
  }

  @Override
  public void update(AnActionEvent e) {
    List<StackFrameItem> stack = getRelatedStack(e);
    e.getPresentation().setEnabledAndVisible(stack != null);
  }

  @Nullable
  private static List<StackFrameItem> getRelatedStack(AnActionEvent e) {
    DebuggerContextImpl debuggerContext = DebuggerAction.getDebuggerContext(e.getDataContext());

    DebugProcessImpl debugProcess = debuggerContext.getDebugProcess();
    if (debugProcess == null) {
      return null;
    }

    Map<ObjectReference, List<StackFrameItem>> data = debugProcess.getUserData(StackCapturingLineBreakpoint.CAPTURED_STACKS);
    if (data == null) {
      return null;
    }

    List<JavaValue> values = ViewAsGroup.getSelectedValues(e);
    if (values.size() == 1) {
      return data.get(values.get(0).getDescriptor().getValue());
    }
    return null;
  }
}
