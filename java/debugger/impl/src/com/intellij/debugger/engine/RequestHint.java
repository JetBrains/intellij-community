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

/*
 * @author: Eugene Zhuravlev
 */
package com.intellij.debugger.engine;

import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.jdi.StackFrameProxy;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
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

  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.engine.RequestHint");
  @MagicConstant (intValues = {StepRequest.STEP_MIN, StepRequest.STEP_LINE})
  private final int mySize;
  @MagicConstant (intValues = {StepRequest.STEP_INTO, StepRequest.STEP_OVER, StepRequest.STEP_OUT})
  private final int myDepth;
  private final SourcePosition myPosition;
  private final int myFrameCount;
  private boolean mySteppedOut = false;

  @Nullable
  private final MethodFilter myMethodFilter;
  private boolean myTargetMethodMatched = false;

  private boolean myIgnoreFilters = false;
  private boolean myResetIgnoreFilters = false;
  private boolean myRestoreBreakpoints = false;

  public RequestHint(final ThreadReferenceProxyImpl stepThread, final SuspendContextImpl suspendContext, @NotNull MethodFilter methodFilter) {
    this(stepThread, suspendContext, StepRequest.STEP_LINE, StepRequest.STEP_INTO, methodFilter);
  }

  public RequestHint(final ThreadReferenceProxyImpl stepThread,
                     final SuspendContextImpl suspendContext,
                     @MagicConstant (intValues = {StepRequest.STEP_INTO, StepRequest.STEP_OVER, StepRequest.STEP_OUT}) int depth) {
    this(stepThread, suspendContext, StepRequest.STEP_LINE, depth, null);
  }

  protected RequestHint(final ThreadReferenceProxyImpl stepThread,
                      final SuspendContextImpl suspendContext,
                      @MagicConstant (intValues = {StepRequest.STEP_MIN, StepRequest.STEP_LINE}) int stepSize,
                      @MagicConstant (intValues = {StepRequest.STEP_INTO, StepRequest.STEP_OVER, StepRequest.STEP_OUT}) int depth,
                      @Nullable MethodFilter methodFilter) {
    mySize = stepSize;
    myDepth = depth;
    myMethodFilter = methodFilter;

    int frameCount = 0;
    SourcePosition position = null;
    try {
      frameCount = stepThread.frameCount();

      position = ContextUtil.getSourcePosition(new StackFrameContext() {
        public StackFrameProxy getFrameProxy() {
          try {
            return stepThread.frame(0);
          }
          catch (EvaluateException e) {
            LOG.debug(e);
            return null;
          }
        }

        @NotNull
        public DebugProcess getDebugProcess() {
          return suspendContext.getDebugProcess();
        }
      });
    }
    catch (Exception e) {
      LOG.info(e);
    }
    finally {
      myFrameCount = frameCount;
      myPosition = position;
    }
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

  @MagicConstant (intValues = {StepRequest.STEP_MIN, StepRequest.STEP_LINE})
  public int getSize() {
    return mySize;
  }

  @MagicConstant (intValues = {StepRequest.STEP_INTO, StepRequest.STEP_OVER, StepRequest.STEP_OUT})
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
    final ThreadReferenceProxyImpl contextThread = context.getThread();
    if (contextThread != null) {
      try {
        int currentDepth = contextThread.frameCount();
        if (currentDepth < myFrameCount) mySteppedOut = true;
        return currentDepth == myFrameCount;
      }
      catch (EvaluateException ignored) {
      }
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

  public int getNextStepDepth(final SuspendContextImpl context) {
    try {
      final StackFrameProxyImpl frameProxy = context.getFrameProxy();

      // smart step feature stop check
      if (myMethodFilter != null &&
          frameProxy != null &&
          !(myMethodFilter instanceof BreakpointStepMethodFilter) &&
          myMethodFilter.locationMatches(context.getDebugProcess(), frameProxy.location(), frameProxy::thisObject) &&
          !isTheSameFrame(context)
        ) {
        myTargetMethodMatched = true;
        return myMethodFilter.onReached(context, this);
      }

      if ((myDepth == StepRequest.STEP_OVER || myDepth == StepRequest.STEP_INTO) && myPosition != null) {
        SourcePosition locationPosition = ContextUtil.getSourcePosition(context);
        if (locationPosition != null) {
          Integer resultDepth = ReadAction.compute(() -> {
            if (myPosition.getFile().equals(locationPosition.getFile()) && isTheSameFrame(context) && !mySteppedOut) {
              return isOnTheSameLine(locationPosition) ? myDepth : STOP;
            }
            return null;
          });
          if (resultDepth != null) {
            return resultDepth.intValue();
          }
        }
      }

      // Now check filters

      final DebuggerSettings settings = DebuggerSettings.getInstance();

      if ((myMethodFilter != null || (settings.SKIP_SYNTHETIC_METHODS && !myIgnoreFilters))&& frameProxy != null) {
        final Location location = frameProxy.location();
        if (location != null) {
          if (DebuggerUtils.isSynthetic(location.method())) {
            return myDepth;
          }
        }
      }

      if (!myIgnoreFilters) {
        if(settings.SKIP_GETTERS) {
          boolean isGetter = ReadAction.compute(() -> {
            PsiElement contextElement = ContextUtil.getContextElement(context);
            return contextElement != null && DebuggerUtils.isInsideSimpleGetter(contextElement);
          }).booleanValue();

          if(isGetter) {
            return StepRequest.STEP_OUT;
          }
        }

        if (frameProxy != null) {
          if (settings.SKIP_CONSTRUCTORS) {
            final Location location = frameProxy.location();
            if (location != null) {
              final Method method = location.method();
              if (method != null && method.isConstructor()) {
                return StepRequest.STEP_OUT;
              }
            }
          }

          if (settings.SKIP_CLASSLOADERS) {
            final Location location = frameProxy.location();
            if (location != null && DebuggerUtilsEx.isAssignableFrom("java.lang.ClassLoader", location.declaringType())) {
              return StepRequest.STEP_OUT;
            }
          }
        }

        for (ExtraSteppingFilter filter : ExtraSteppingFilter.EP_NAME.getExtensions()) {
          try {
            if (filter.isApplicable(context)) return filter.getStepRequestDepth(context);
          }
          catch (Exception | AssertionError e) {LOG.error(e);}
        }
      }
      // smart step feature
      if (myMethodFilter != null && !mySteppedOut) {
        return StepRequest.STEP_OUT;
      }
    }
    catch (VMDisconnectedException ignored) {
    }
    catch (EvaluateException e) {
      LOG.error(e);
    }
    return STOP;
  }

}
