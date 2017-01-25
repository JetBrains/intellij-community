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
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.memory.utils.StackFrameItem;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Key;
import com.intellij.util.containers.ContainerUtil;
import com.sun.jdi.Method;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;
import com.sun.jdi.event.LocatableEvent;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.debugger.breakpoints.properties.JavaMethodBreakpointProperties;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author egor
 */
public class StackCapturingLineBreakpoint extends WildcardMethodBreakpoint {
  private final String mySignature;
  private final int myParamNo;

  public static final Key<Map<ObjectReference, List<StackFrameItem>>> CAPTURED_STACKS = Key.create("CAPTURED_STACKS");
  private static final int MAX_STORED_STACKS = 1000;

  private final JavaMethodBreakpointProperties myProperties = new JavaMethodBreakpointProperties();

  public StackCapturingLineBreakpoint(Project project,
                                      String className,
                                      String methodName,
                                      String methodSignature,
                                      int paramNo) {
    super(project, null);
    mySignature = methodSignature;
    myParamNo = paramNo;
    myProperties.EMULATED = true;
    myProperties.WATCH_EXIT = false;
    myProperties.myClassPattern = className;
    myProperties.myMethodName = methodName;
  }

  @NotNull
  @Override
  protected JavaMethodBreakpointProperties getProperties() {
    return myProperties;
  }

  @Override
  public String getSuspendPolicy() {
    return DebuggerSettings.SUSPEND_THREAD;
  }

  @Override
  public boolean processLocatableEvent(SuspendContextCommandImpl action, LocatableEvent event) throws EventProcessingException {
    try {
      SuspendContextImpl suspendContext = action.getSuspendContext();
      if (suspendContext != null) {
        StackFrameProxyImpl frameProxy = suspendContext.getFrameProxy();
        if (frameProxy != null) {
          DebugProcessImpl process = suspendContext.getDebugProcess();
          Map<ObjectReference, List<StackFrameItem>> stacks = process.getUserData(CAPTURED_STACKS);
          if (stacks == null) {
            stacks = new LinkedHashMap<ObjectReference, List<StackFrameItem>>() {
              @Override
              protected boolean removeEldestEntry(Map.Entry eldest) {
                return size() > MAX_STORED_STACKS;
              }
            };
            process.putUserData(CAPTURED_STACKS, Collections.synchronizedMap(stacks));
          }
          Value key = ContainerUtil.getOrElse(frameProxy.getArgumentValues(), myParamNo, null);
          if (key instanceof ObjectReference) {
            stacks.put((ObjectReference)key, StackFrameItem.createFrames(suspendContext.getThread(), process, true));
          }
        }
      }
    }
    catch (EvaluateException ignored) {
    }
    return false;
  }

  @Override
  public StreamEx matchingMethods(StreamEx<Method> methods, DebugProcessImpl debugProcess) {
    String methodName = getMethodName();
    return methods
      .filter(m -> Comparing.equal(methodName, m.name()) && (mySignature == null || Comparing.equal(mySignature, m.signature())))
      .limit(1);
  }

  public static void track(DebugProcessImpl debugProcess, String classFqn, String methodName, @Nullable String methodSignature, int paramNo) {
    StackCapturingLineBreakpoint breakpoint =
      new StackCapturingLineBreakpoint(debugProcess.getProject(), classFqn, methodName, methodSignature, paramNo);
    breakpoint.createRequest(debugProcess);
  }

  @Nullable
  public static List<StackFrameItem> getRelatedStack(@Nullable StackFrameProxyImpl frame, @Nullable DebugProcessImpl process) {
    if (process != null && frame != null) {
      Map<ObjectReference, List<StackFrameItem>> data = process.getUserData(CAPTURED_STACKS);
      if (data != null) {
        try {
          return data.get(frame.thisObject());
        }
        catch (EvaluateException ignore) {
        }
      }
    }
    return null;
  }

  @Nullable
  public static List<StackFrameItem> getRelatedStack(@Nullable ObjectReference key, @Nullable DebugProcessImpl process) {
    if (process != null && key != null) {
      Map<ObjectReference, List<StackFrameItem>> data = process.getUserData(CAPTURED_STACKS);
      if (data != null) {
        return data.get(key);
      }
    }
    return null;
  }
}
