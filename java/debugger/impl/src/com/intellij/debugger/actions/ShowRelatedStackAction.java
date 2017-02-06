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
import com.intellij.debugger.engine.JavaValue;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.memory.ui.StackFramePopup;
import com.intellij.debugger.memory.utils.StackFrameItem;
import com.intellij.debugger.ui.breakpoints.StackCapturingLineBreakpoint;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author egor
 */
public class ShowRelatedStackAction extends AnAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    List<StackFrameItem> stack = getRelatedStack(e);
    if (project != null && stack != null) {
      DebugProcessImpl debugProcess = DebuggerAction.getDebuggerContext(e.getDataContext()).getDebugProcess();
      if (debugProcess == null) {
        return;
      }

      StackFramePopup.show(stack, debugProcess);
    }
  }

  @Override
  public void update(AnActionEvent e) {
    List<StackFrameItem> stack = getRelatedStack(e);
    e.getPresentation().setEnabledAndVisible(stack != null);
  }

  @Nullable
  private static List<StackFrameItem> getRelatedStack(AnActionEvent e) {
    List<JavaValue> values = ViewAsGroup.getSelectedValues(e);
    if (values.size() == 1) {
      Value value = values.get(0).getDescriptor().getValue();
      if (value instanceof ObjectReference) {
        DebuggerContextImpl debuggerContext = DebuggerAction.getDebuggerContext(e.getDataContext());
        return StackCapturingLineBreakpoint.getRelatedStack((ObjectReference)value, debuggerContext.getDebugProcess());
      }
    }

    return null;
  }
}
