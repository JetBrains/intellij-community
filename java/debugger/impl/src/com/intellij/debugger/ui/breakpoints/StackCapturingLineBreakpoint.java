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
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.memory.utils.StackFrameItem;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.util.containers.ContainerUtil;
import com.sun.jdi.Value;
import com.sun.jdi.event.LocatableEvent;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author egor
 */
public class StackCapturingLineBreakpoint extends RunToCursorBreakpoint {
  private final DebugProcessImpl myDebugProcess;
  private static final Key<Map<Object, List<StackFrameItem>>> CAPTURED_STACKS = Key.create("CAPTURED_STACKS");
  private static final int MAX_STORED_STACKS = 1000;

  public StackCapturingLineBreakpoint(Project project, DebugProcessImpl debugProcess, @NotNull SourcePosition pos) {
    super(project, pos, false);
    myDebugProcess = debugProcess;
  }

  @Override
  public boolean processLocatableEvent(SuspendContextCommandImpl action, LocatableEvent event) throws EventProcessingException {
    try {
      SuspendContextImpl suspendContext = action.getSuspendContext();
      if (suspendContext != null) {
        Map<Object, List<StackFrameItem>> stacks = myDebugProcess.getUserData(CAPTURED_STACKS);
        if (stacks == null) {
          stacks = new LinkedHashMap<Object, List<StackFrameItem>>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry eldest) {
              return size() > MAX_STORED_STACKS;
            }
          };
          myDebugProcess.putUserData(CAPTURED_STACKS, stacks);
        }
        Value key = ContainerUtil.getFirstItem(suspendContext.getFrameProxy().getArgumentValues());
        stacks.put(key, StackFrameItem.createFrames(suspendContext.getThread()));
      }
    }
    catch (EvaluateException ignored) {
    }
    return false;
  }
}
