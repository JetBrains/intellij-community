// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author Eugene Zhuravlev
 */
package com.intellij.debugger.engine;

import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.impl.DebuggerUtilsImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.util.Range;
import com.sun.jdi.Location;
import com.sun.jdi.Method;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.request.StepRequest;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RequestHint {
  public static final int STOP = 0;
  public static final int RESUME = -100;

  private static final Logger LOG = Logger.getInstance(RequestHint.class);
  @MagicConstant(intValues = {StepRequest.STEP_MIN, StepRequest.STEP_LINE})
  private final int mySize;
  @MagicConstant(intValues = {StepRequest.STEP_INTO, StepRequest.STEP_OVER, StepRequest.STEP_OUT})
  private final int myDepth;
  private final SourcePosition myPosition;
  private final int myFrameCount;
  private boolean mySteppedOut = false;

  @Nullable
  private final MethodFilter myMethodFilter;
  private int myFilterMatchedCount = 0;
  private boolean myTargetMethodMatched = false;

  private boolean myIgnoreFilters = false;
  private boolean myResetIgnoreFilters = false;
  private boolean myRestoreBreakpoints = false;

  @Nullable
  private final RequestHint myParentHint;

  public RequestHint(final ThreadReferenceProxyImpl stepThread, final SuspendContextImpl suspendContext, @NotNull MethodFilter methodFilter) {
    this(stepThread, suspendContext, StepRequest.STEP_LINE, StepRequest.STEP_INTO, methodFilter);
  }

  public RequestHint(final ThreadReferenceProxyImpl stepThread,
                     final SuspendContextImpl suspendContext,
                     @MagicConstant(intValues = {StepRequest.STEP_INTO, StepRequest.STEP_OVER, StepRequest.STEP_OUT}) int depth) {
    this(stepThread, suspendContext, StepRequest.STEP_LINE, depth, null);
  }

  public RequestHint(final ThreadReferenceProxyImpl stepThread,
                     final SuspendContextImpl suspendContext,
                     @MagicConstant(intValues = {StepRequest.STEP_MIN, StepRequest.STEP_LINE}) int stepSize,
                     @MagicConstant(intValues = {StepRequest.STEP_INTO, StepRequest.STEP_OVER, StepRequest.STEP_OUT}) int depth,
                     @Nullable MethodFilter methodFilter) {
    this(stepThread, suspendContext, stepSize, depth, methodFilter, null);
  }

  public RequestHint(final ThreadReferenceProxyImpl stepThread,
                     final SuspendContextImpl suspendContext,
                     @MagicConstant(intValues = {StepRequest.STEP_MIN, StepRequest.STEP_LINE}) int stepSize,
                     @MagicConstant(intValues = {StepRequest.STEP_INTO, StepRequest.STEP_OVER, StepRequest.STEP_OUT}) int depth,
                     @Nullable MethodFilter methodFilter,
                     @Nullable RequestHint parentHint) {
    mySize = stepSize;
    myDepth = depth;
    myMethodFilter = methodFilter;

    myFrameCount = DebugProcessImpl.getFrameCount(stepThread, suspendContext);
    myPosition =
      suspendContext.getDebugProcess().getPositionManager().getSourcePosition(DebugProcessImpl.getLocation(stepThread, suspendContext));
    myParentHint = parentHint;
  }

  public void setIgnoreFilters(boolean ignoreFilters) {
    myIgnoreFilters = ignoreFilters;
  }

  public void setResetIgnoreFilters(boolean resetIgnoreFilters) {
    myResetIgnoreFilters = resetIgnoreFilters;
  }

  public boolean isResetIgnoreFilters() {
    return myResetIgnoreFilters;
  }

  public void setRestoreBreakpoints(boolean restoreBreakpoints) {
    myRestoreBreakpoints = restoreBreakpoints;
  }

  public boolean isRestoreBreakpoints() {
    return myRestoreBreakpoints;
  }

  public boolean isIgnoreFilters() {
    return myIgnoreFilters;
  }

  @MagicConstant(intValues = {StepRequest.STEP_MIN, StepRequest.STEP_LINE})
  public int getSize() {
    return mySize;
  }

  @MagicConstant(intValues = {StepRequest.STEP_INTO, StepRequest.STEP_OVER, StepRequest.STEP_OUT})
  public int getDepth() {
    return myDepth;
  }

  @Nullable
  public MethodFilter getMethodFilter() {
    return myMethodFilter;
  }

  public boolean wasStepTargetMethodMatched() {
    return myMethodFilter instanceof BreakpointStepMethodFilter || myTargetMethodMatched;
  }

  protected boolean isTheSameFrame(SuspendContextImpl context) {
    if (mySteppedOut) return false;
    ThreadReferenceProxyImpl contextThread = context.getThread();
    if (contextThread != null) {
      int currentDepth = DebugProcessImpl.getFrameCount(contextThread, context);
      if (currentDepth < myFrameCount) mySteppedOut = true;
      return currentDepth == myFrameCount;
    }
    return false;
  }

  private boolean isOnTheSameLine(SourcePosition locationPosition) {
    if (myMethodFilter == null) {
      return myPosition.getLine() == locationPosition.getLine();
    }
    else {
      Range<Integer> exprLines = myMethodFilter.getCallingExpressionLines();
      return exprLines != null && exprLines.isWithin(locationPosition.getLine());
    }
  }

  protected boolean isSteppedOut() {
    return mySteppedOut;
  }

  public Integer checkCurrentPosition(SuspendContextImpl context, Location location) {
    if ((myDepth == StepRequest.STEP_OVER || myDepth == StepRequest.STEP_INTO) && myPosition != null) {
      SourcePosition locationPosition = context.getDebugProcess().getPositionManager().getSourcePosition(location);
      if (locationPosition != null) {
        return ReadAction.compute(() -> {
          if (myPosition.getFile().equals(locationPosition.getFile()) && isTheSameFrame(context) && !mySteppedOut) {
            return isOnTheSameLine(locationPosition) ? myDepth : STOP;
          }
          return null;
        });
      }
    }
    return null;
  }

  static boolean isProxyMethod(Method method) {
    return method.isBridge() || DebuggerUtilsEx.isProxyClass(method.declaringType());
  }

  @Nullable
  protected final Integer processSteppingFilters(@NotNull SuspendContextImpl context, @Nullable Location location) {
    final DebuggerSettings settings = DebuggerSettings.getInstance();

    if ((myMethodFilter != null || (settings.SKIP_SYNTHETIC_METHODS && !myIgnoreFilters)) &&
        location != null && DebuggerUtils.isSynthetic(location.method())) {
      return myDepth;
    }

    if (!myIgnoreFilters) {
      if (settings.SKIP_GETTERS) {
        boolean isGetter = ReadAction.compute(() -> {
          PsiElement contextElement = ContextUtil.getContextElement(context);
          return contextElement != null && DebuggerUtils.isInsideSimpleGetter(contextElement);
        }).booleanValue();

        if (isGetter) {
          return StepRequest.STEP_OUT;
        }
      }

      if (location != null) {
        if (settings.SKIP_CONSTRUCTORS) {
          final Method method = location.method();
          if (method != null && method.isConstructor()) {
            return StepRequest.STEP_OUT;
          }
        }

        if (settings.SKIP_CLASSLOADERS && DebuggerUtils.instanceOf(location.declaringType(), "java.lang.ClassLoader")) {
          return StepRequest.STEP_OUT;
        }
      }

      for (ExtraSteppingFilter filter : ExtraSteppingFilter.EP_NAME.getExtensionList()) {
        try {
          if (filter.isApplicable(context)) {
            return filter.getStepRequestDepth(context);
          }
        }
        catch (Exception | AssertionError e) {
          DebuggerUtilsImpl.logError(e);
        }
      }
    }

    return null;
  }

  public int getNextStepDepth(final SuspendContextImpl context) {
    try {
      Location location = context.getLocation();

      // smart step feature stop check
      if (myMethodFilter != null &&
          location != null &&
          !(myMethodFilter instanceof BreakpointStepMethodFilter) &&
          !isTheSameFrame(context)) {
        if (isProxyMethod(location.method())) { // step into bridge and proxy methods
          return StepRequest.STEP_INTO;
        }
        boolean proxyMatch =
          (myMethodFilter instanceof BasicStepMethodFilter && ((BasicStepMethodFilter)myMethodFilter).proxyCheck(location, context, this));
        if (proxyMatch || myMethodFilter.locationMatches(context.getDebugProcess(), location, context.getFrameProxy())) {
          if (myMethodFilter.getSkipCount() <= myFilterMatchedCount++) {
            myTargetMethodMatched = true;
            return myMethodFilter.onReached(context, this);
          }
        }
      }

      Integer resultDepth = checkCurrentPosition(context, location);
      if (resultDepth != null) {
        return resultDepth.intValue();
      }

      resultDepth = processSteppingFilters(context, location);
      if (resultDepth != null) {
        return resultDepth.intValue();
      }

      // smart step feature
      if (myMethodFilter != null) {
        isTheSameFrame(context); // to set mySteppedOut if needed
        if (!mySteppedOut) {
          return StepRequest.STEP_OUT;
        }
      }
    }
    catch (VMDisconnectedException ignored) {
    }
    catch (EvaluateException e) {
      LOG.error(e);
    }
    return STOP;
  }

  protected void doStep(@NotNull DebugProcessImpl debugProcess, SuspendContextImpl suspendContext, ThreadReferenceProxyImpl stepThread, int size, int depth, Object commandToken) {
    debugProcess.doStep(suspendContext, stepThread, size, depth, this, commandToken);
  }

  @Nullable
  final RequestHint getParentHint() {
    return myParentHint;
  }
}
