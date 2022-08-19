// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.*;
import com.intellij.debugger.engine.evaluation.*;
import com.intellij.debugger.engine.evaluation.expression.Evaluator;
import com.intellij.debugger.engine.evaluation.expression.EvaluatorBuilderImpl;
import com.intellij.debugger.engine.evaluation.expression.ExpressionEvaluator;
import com.intellij.debugger.engine.evaluation.expression.ExpressionEvaluatorImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.DecompiledLocalVariable;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.memory.utils.StackFrameItem;
import com.intellij.debugger.settings.CapturePoint;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FixedHashMap;
import com.sun.jdi.*;
import com.sun.jdi.event.LocatableEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;


public class StackCapturingLineBreakpoint extends SyntheticMethodBreakpoint {
  private static final Logger LOG = Logger.getInstance(StackCapturingLineBreakpoint.class);

  private final CapturePoint myCapturePoint;

  private final MyEvaluator myCaptureEvaluator;
  private final MyEvaluator myInsertEvaluator;

  public static final Key<List<StackCapturingLineBreakpoint>> CAPTURE_BREAKPOINTS = Key.create("CAPTURE_BREAKPOINTS");
  private static final Key<Map<Object, List<StackFrameItem>>> CAPTURED_STACKS = Key.create("CAPTURED_STACKS");
  private static final int MAX_STORED_STACKS = 1000;

  public StackCapturingLineBreakpoint(Project project, CapturePoint capturePoint) {
    super(capturePoint.myClassName, capturePoint.myMethodName, null, project);
    myCapturePoint = capturePoint;
    myCaptureEvaluator = new MyEvaluator(myCapturePoint.myCaptureKeyExpression);
    myInsertEvaluator = new MyEvaluator(myCapturePoint.myInsertKeyExpression);
    setSuspendPolicy(DebuggerSettings.SUSPEND_THREAD);
  }

  @Override
  public boolean processLocatableEvent(@NotNull SuspendContextCommandImpl action, LocatableEvent event) {
    SuspendContextImpl suspendContext = action.getSuspendContext();
    if (suspendContext != null) {
      ThreadReferenceProxyImpl thread = suspendContext.getThread();
      if (thread != null) {
        DebugProcessImpl process = suspendContext.getDebugProcess();
        try {
          StackFrameProxyImpl frameProxy = ContainerUtil.getFirstItem(thread.forceFrames());
          if (frameProxy != null) {
            Map<Object, List<StackFrameItem>> stacks = process.getUserData(CAPTURED_STACKS);
            if (stacks == null) {
              stacks = new FixedHashMap<>(MAX_STORED_STACKS);
              AsyncStacksUtils.putProcessUserData(CAPTURED_STACKS, Collections.synchronizedMap(stacks), process);
            }
            Value key = myCaptureEvaluator.evaluate(new EvaluationContextImpl(suspendContext, frameProxy));
            if (key instanceof ObjectReference) {
              List<StackFrameItem> frames = StackFrameItem.createFrames(suspendContext, true);
              frames = ContainerUtil.getFirstItems(frames, AsyncStacksUtils.getMaxStackLength());
              stacks.put(getKey((ObjectReference)key), frames);
            }
          }
        }
        catch (EvaluateException e) {
          LOG.debug(e);
          process.printToConsole(JavaDebuggerBundle.message("error.unable.to.evaluate.capture.expression", e.getMessage()) + "\n");
        }
      }
    }

    return false;
  }

  public static void deleteAll(DebugProcessImpl debugProcess) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    List<StackCapturingLineBreakpoint> bpts = debugProcess.getUserData(CAPTURE_BREAKPOINTS);
    if (!ContainerUtil.isEmpty(bpts)) {
      bpts.forEach(debugProcess.getRequestsManager()::deleteRequest);
      bpts.clear();
    }
  }

  public static void createAll(DebugProcessImpl debugProcess) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    DebuggerSettings.getInstance().getCapturePoints().stream().filter(c -> c.myEnabled).forEach(c -> track(debugProcess, c));
  }

  public static void clearCaches(DebugProcessImpl debugProcess) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    List<StackCapturingLineBreakpoint> bpts = debugProcess.getUserData(CAPTURE_BREAKPOINTS);
    if (!ContainerUtil.isEmpty(bpts)) {
      bpts.forEach(b -> {
        b.myCaptureEvaluator.clearCache();
        b.myInsertEvaluator.clearCache();
      });
    }
  }

  @Override
  public void createRequest(DebugProcessImpl debugProcess) {
    if (!StringUtil.isEmpty(getClassName())) {
      super.createRequest(debugProcess);
    }
  }

  @Override
  public void customizeRenderer(SimpleColoredComponent renderer) {
    renderer.append(JavaDebuggerBundle.message("label.capture.point.at", myCapturePoint.myClassName, myCapturePoint.myMethodName));
  }

  @Override
  public boolean isEnabled() {
    return myCapturePoint.myEnabled;
  }

  @Override
  public void setEnabled(boolean enabled) {
    myCapturePoint.myEnabled = enabled;
    DebuggerSettings.getInstance().setCapturePoints(DebuggerSettings.getInstance().getCapturePoints()); // to fire change event
  }

  private static void track(DebugProcessImpl debugProcess, CapturePoint capturePoint) {
    StackCapturingLineBreakpoint breakpoint = new StackCapturingLineBreakpoint(debugProcess.getProject(), capturePoint);
    breakpoint.createRequest(debugProcess);
    List<StackCapturingLineBreakpoint> bpts = debugProcess.getUserData(CAPTURE_BREAKPOINTS);
    if (bpts == null) {
      bpts = new CopyOnWriteArrayList<>();
      AsyncStacksUtils.putProcessUserData(CAPTURE_BREAKPOINTS, bpts, debugProcess);
    }
    bpts.add(breakpoint);
  }

  @Nullable
  public static List<StackFrameItem> getRelatedStack(@NotNull StackFrameProxyImpl frame, @NotNull SuspendContextImpl suspendContext) {
    DebugProcessImpl debugProcess = suspendContext.getDebugProcess();
    Map<Object, List<StackFrameItem>> capturedStacks = debugProcess.getUserData(CAPTURED_STACKS);
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
      String methodName = DebuggerUtilsEx.getLocationMethodName(location);

      for (StackCapturingLineBreakpoint b : captureBreakpoints) {
        String insertClassName = b.myCapturePoint.myInsertClassName;
        if ((StringUtil.isEmpty(insertClassName) || StringUtil.equals(insertClassName, className)) &&
            StringUtil.equals(b.myCapturePoint.myInsertMethodName, methodName)) {
          try {
            Value key = b.myInsertEvaluator.evaluate(new EvaluationContextImpl(suspendContext, frame));
            return key instanceof ObjectReference ? capturedStacks.get(getKey((ObjectReference)key)) : null;
          }
          catch (EvaluateException e) {
            LOG.debug(e);
            if (!(e.getCause() instanceof IncompatibleThreadStateException)) {
              debugProcess.printToConsole(JavaDebuggerBundle.message("error.unable.to.evaluate.insert.expression", e.getMessage()) + "\n");
            }
          }
        }
      }
    }
    catch (EvaluateException e) {
      LOG.debug(e);
    }
    return null;
  }

  @Nullable
  public static List<StackFrameItem> getRelatedStack(@Nullable ObjectReference key, @Nullable DebugProcessImpl process) {
    if (process != null && key != null) {
      Map<Object, List<StackFrameItem>> data = process.getUserData(CAPTURED_STACKS);
      if (data != null) {
        return data.get(getKey(key));
      }
    }
    return null;
  }

  private static Object getKey(ObjectReference reference) {
    return reference instanceof StringReference ? ((StringReference)reference).value() : reference;
  }

  private static class MyEvaluator {
    private final String myExpression;
    private ExpressionEvaluator myEvaluator;
    private final Map<Location, ExpressionEvaluator> myEvaluatorCache = new WeakHashMap<>();

    MyEvaluator(String expression) {
      myExpression = expression;
      int paramId = DecompiledLocalVariable.getParamId(myExpression);
      boolean paramEvaluator = paramId > -1;
      if (paramEvaluator) {
        myEvaluator = new ExpressionEvaluatorImpl(new Evaluator() {
          @Override
          public Object evaluate(EvaluationContextImpl context) throws EvaluateException {
            @SuppressWarnings("ConstantConditions")
            List<Value> argumentValues = context.getFrameProxy().getArgumentValues();
            if (paramId >= argumentValues.size()) {
              throw new EvaluateException("Param index " + paramId + " requested, but only " + argumentValues.size() + " available");
            }
            return argumentValues.get(paramId);
          }
        });
      }
    }

    @Nullable
    Value evaluate(final EvaluationContext context) throws EvaluateException {
      ExpressionEvaluator evaluator = myEvaluator;
      if (evaluator == null) {
        @SuppressWarnings("ConstantConditions")
        Location location = context.getFrameProxy().location();
        evaluator = location == null ? null : myEvaluatorCache.get(location);
        if (evaluator == null && !StringUtil.isEmpty(myExpression)) {
          evaluator = ApplicationManager.getApplication().runReadAction((ThrowableComputable<ExpressionEvaluator, EvaluateException>)() -> {
            SourcePosition sourcePosition = ContextUtil.getSourcePosition(context);
            PsiElement contextElement = ContextUtil.getContextElement(sourcePosition);
            return EvaluatorBuilderImpl.build(
              new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, myExpression), contextElement, sourcePosition, context.getProject());
          });
          myEvaluatorCache.put(location, evaluator);
        }
      }
      if (evaluator != null) {
        return evaluator.evaluate(context);
      }
      return null;
    }

    void clearCache() {
      DebuggerManagerThreadImpl.assertIsManagerThread();
      myEvaluatorCache.clear();
    }
  }

  public static class CaptureAsyncStackTraceProvider implements AsyncStackTraceProvider {
    @Nullable
    @Override
    public List<StackFrameItem> getAsyncStackTrace(@NotNull JavaStackFrame stackFrame, @NotNull SuspendContextImpl suspendContext) {
      return getRelatedStack(stackFrame.getStackFrameProxy(), suspendContext);
    }
  }
}
