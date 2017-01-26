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
import com.intellij.debugger.engine.evaluation.CodeFragmentKind;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilderImpl;
import com.intellij.debugger.engine.evaluation.expression.ExpressionEvaluator;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.memory.utils.StackFrameItem;
import com.intellij.debugger.settings.CapturePoint;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColoredTextContainer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.XSourcePosition;
import com.sun.jdi.Location;
import com.sun.jdi.Method;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;
import com.sun.jdi.event.LocatableEvent;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.debugger.breakpoints.properties.JavaMethodBreakpointProperties;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author egor
 */
public class StackCapturingLineBreakpoint extends WildcardMethodBreakpoint {
  private static final Logger LOG = Logger.getInstance(StackCapturingLineBreakpoint.class);

  private final CapturePoint myCapturePoint;
  private final String mySignature;

  private final NullableLazyValue<ExpressionEvaluator> myEvaluator;

  public static final Key<List<StackCapturingLineBreakpoint>> CAPTURE_BREAKPOINTS = Key.create("CAPTURE_BREAKPOINTS");
  public static final Key<Map<ObjectReference, List<StackFrameItem>>> CAPTURED_STACKS = Key.create("CAPTURED_STACKS");
  private static final int MAX_STORED_STACKS = 1000;
  private static final int MAX_STACK_LENGTH = 500;

  private final JavaMethodBreakpointProperties myProperties = new JavaMethodBreakpointProperties();

  public StackCapturingLineBreakpoint(Project project, CapturePoint capturePoint) {
    super(project, null);
    myCapturePoint = capturePoint;
    mySignature = null;
    myProperties.EMULATED = true;
    myProperties.WATCH_EXIT = false;
    myProperties.myClassPattern = myCapturePoint.myClassName;
    myProperties.myMethodName = myCapturePoint.myMethodName;

    myEvaluator = NullableLazyValue.createValue(() -> ApplicationManager.getApplication().runReadAction((Computable<ExpressionEvaluator>)() -> {
        try {
          return EvaluatorBuilderImpl.build(new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, myCapturePoint.myInsertKeyExpression),
                                            null, null, project);
        }
        catch (EvaluateException e) {
          LOG.warn(e);
        }
        return null;
      }
    ));
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
          Value key = ContainerUtil.getOrElse(frameProxy.getArgumentValues(), myCapturePoint.myParamNo, null);
          if (key instanceof ObjectReference) {
            List<StackFrameItem> frames = StackFrameItem.createFrames(suspendContext.getThread(), suspendContext, true);
            if (frames.size() > MAX_STACK_LENGTH) {
              ArrayList<StackFrameItem> truncated = new ArrayList<>(MAX_STACK_LENGTH + 1);
              truncated.addAll(frames.subList(0, MAX_STACK_LENGTH));
              truncated.add(TOO_MANY_FRAMES);
              frames = truncated;
            }
            stacks.put((ObjectReference)key, frames);
          }
        }
      }
    }
    catch (EvaluateException ignored) {
    }
    return false;
  }

  private static StackFrameItem TOO_MANY_FRAMES = new StackFrameItem(null, null, "", "", -1) {
    @Nullable
    @Override
    public XSourcePosition getSourcePosition() {
      return null;
    }

    @Override
    public void customizePresentation(@NotNull ColoredTextContainer component) {
      component.append("Too many frames, the rest is truncated...", SimpleTextAttributes.REGULAR_ITALIC_ATTRIBUTES);
    }
  };

  @Override
  public StreamEx matchingMethods(StreamEx<Method> methods, DebugProcessImpl debugProcess) {
    String methodName = getMethodName();
    return methods
      .filter(m -> Comparing.equal(methodName, m.name()) && (mySignature == null || Comparing.equal(mySignature, m.signature())))
      .limit(1);
  }

  public static void track(DebugProcessImpl debugProcess, CapturePoint capturePoint) {
    StackCapturingLineBreakpoint breakpoint = new StackCapturingLineBreakpoint(debugProcess.getProject(), capturePoint);
    breakpoint.createRequest(debugProcess);
    List<StackCapturingLineBreakpoint> bpts = debugProcess.getUserData(CAPTURE_BREAKPOINTS);
    if (bpts == null) {
      bpts = new CopyOnWriteArrayList<>();
      debugProcess.putUserData(CAPTURE_BREAKPOINTS, bpts);
    }
    bpts.add(breakpoint);
  }

  @Nullable
  public static List<StackFrameItem> getRelatedStack(@Nullable StackFrameProxyImpl frame, @NotNull SuspendContextImpl suspendContext) {
    if (frame != null) {
      DebugProcessImpl debugProcess = suspendContext.getDebugProcess();
      Map<ObjectReference, List<StackFrameItem>> capturedStacks = debugProcess.getUserData(CAPTURED_STACKS);
      if (ContainerUtil.isEmpty(capturedStacks)) {
        return null;
      }
      List<StackCapturingLineBreakpoint> captureBreakpoints = debugProcess.getUserData(CAPTURE_BREAKPOINTS);
      if (ContainerUtil.isEmpty(captureBreakpoints)) {
        return null;
      }
      try {
        Location location = frame.location();
        String className = location.declaringType().name();
        String methodName = location.method().name();
        List<Value> argumentValues = null;

        for (StackCapturingLineBreakpoint b : captureBreakpoints) {
          if (StringUtil.equals(b.myCapturePoint.myInsertClassName, className) &&
              StringUtil.equals(b.myCapturePoint.myInsertMethodName, methodName)) {
            if (argumentValues == null) {
              argumentValues = frame.getArgumentValues();
            }

            try {
              ExpressionEvaluator evaluator = b.myEvaluator.getValue();
              if (evaluator != null) {
                EvaluationContextImpl evaluationContext = new EvaluationContextImpl(suspendContext, frame, frame.thisObject());
                Value key = evaluator.evaluate(evaluationContext);

                if (key instanceof ObjectReference) {
                  return capturedStacks.get(key);
                }
              }
            }
            catch (EvaluateException ignore) {
            }
          }
        }
      }
      catch (EvaluateException ignore) {
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
