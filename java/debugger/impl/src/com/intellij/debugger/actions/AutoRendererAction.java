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
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class AutoRendererAction extends AnAction{
  public void actionPerformed(@NotNull final AnActionEvent e) {
    final DebuggerContextImpl debuggerContext = DebuggerAction.getDebuggerContext(e.getDataContext());

    final DebugProcessImpl debugProcess = debuggerContext.getDebugProcess();
    if(debugProcess != null) {
      final List<JavaValue> selectedValues = ViewAsGroup.getSelectedValues(e);
      if (!selectedValues.isEmpty()) {
        debugProcess.getManagerThread().schedule(new DebuggerContextCommandImpl(debuggerContext) {
          @Override
          public void threadAction(@NotNull SuspendContextImpl suspendContext) {
              for (JavaValue selectedValue : selectedValues) {
                selectedValue.getDescriptor().setRenderer(null);
              }
              DebuggerAction.refreshViews(e);
            }
          });
      }
    }
  }
}
