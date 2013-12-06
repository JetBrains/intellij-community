/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
 * Date: Jul 23, 2002
 * Time: 11:10:11 AM
 */
package com.intellij.debugger.engine;

import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.jdi.StackFrameProxy;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.impl.PositionUtil;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.sun.jdi.Location;
import com.sun.jdi.Method;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.request.StepRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RequestHint {
  public static final int STOP = 0;
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.engine.RequestHint");
  private final int myDepth;
  private final SourcePosition myPosition;
  private final int myFrameCount;
  private VirtualMachineProxyImpl myVirtualMachineProxy;

  @Nullable
  private final MethodFilter myMethodFilter;
  private boolean myTargetMethodMatched = false;

  private boolean myIgnoreFilters = false;
  private boolean myRestoreBreakpoints = false;

  public RequestHint(final ThreadReferenceProxyImpl stepThread, final SuspendContextImpl suspendContext, @NotNull MethodFilter methodFilter) {
    this(stepThread, suspendContext, StepRequest.STEP_INTO, methodFilter);
  }

  public RequestHint(final ThreadReferenceProxyImpl stepThread, final SuspendContextImpl suspendContext, int depth) {
    this(stepThread, suspendContext, depth, null);
  }

  private RequestHint(final ThreadReferenceProxyImpl stepThread, final SuspendContextImpl suspendContext, int depth, @Nullable MethodFilter methodFilter) {
    final DebugProcessImpl debugProcess = suspendContext.getDebugProcess();
    myDepth = depth;
    myMethodFilter = methodFilter;
    myVirtualMachineProxy = debugProcess.getVirtualMachineProxy();

    int frameCount = 0;
    SourcePosition position = null;
    try {
      frameCount = stepThread.frameCount();

      position = ApplicationManager.getApplication().runReadAction(new Computable<SourcePosition>() {
        public SourcePosition compute() {
          return ContextUtil.getSourcePosition(new StackFrameContext() {
            public StackFrameProxy getFrameProxy() {
              try {
                return stepThread.frame(0);
              }
              catch (EvaluateException e) {
                if (LOG.isDebugEnabled()) {
                  LOG.debug(e);
                }
                return null;
              }
            }

            public DebugProcess getDebugProcess() {
              return suspendContext.getDebugProcess();
            }
          });
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

  public void setRestoreBreakpoints(boolean restoreBreakpoints) {
    myRestoreBreakpoints = restoreBreakpoints;
  }

  public boolean isRestoreBreakpoints() {
    return myRestoreBreakpoints;
  }

  public boolean isIgnoreFilters() {
    return myIgnoreFilters;
  }

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

  public int getNextStepDepth(final SuspendContextImpl context) {
    try {
      if ((myDepth == StepRequest.STEP_OVER || myDepth == StepRequest.STEP_INTO) && myPosition != null) {
        final Integer resultDepth = ApplicationManager.getApplication().runReadAction(new Computable<Integer>() {
          public Integer compute() {
            final SourcePosition locationPosition = ContextUtil.getSourcePosition(context);
            if (locationPosition == null) {
              return null;
            }
            int frameCount = -1;
            final ThreadReferenceProxyImpl contextThread = context.getThread();
            if (contextThread != null) {
              try {
                frameCount = contextThread.frameCount();
              }
              catch (EvaluateException e) {
              }
            }
            final boolean filesEqual = myPosition.getFile().equals(locationPosition.getFile());
            if (filesEqual && myPosition.getLine() == locationPosition.getLine() && myFrameCount == frameCount) {
              return myDepth;
            }
            if (myDepth == StepRequest.STEP_INTO) {
              if (filesEqual) {
                // we are actually one or more frames upper than the original frame, should stop
                if (myFrameCount > frameCount) {
                  return STOP;
                }
                // check if we are still at the line from which the stepping begun
                if (myFrameCount == frameCount && myPosition.getLine() != locationPosition.getLine()) {
                  return STOP;
                }
              }
            }
            return null;
          }
        });
        if (resultDepth != null) {
          return resultDepth.intValue();
        }
      }
      // the rest of the code makes sense for depth == STEP_INTO only

      if (myDepth == StepRequest.STEP_INTO) {
        final DebuggerSettings settings = DebuggerSettings.getInstance();
        final StackFrameProxyImpl frameProxy = context.getFrameProxy();

        if (settings.SKIP_SYNTHETIC_METHODS && frameProxy != null) {
          final Location location = frameProxy.location();
          final Method method = location.method();
          if (method != null) {
            if (myVirtualMachineProxy.canGetSyntheticAttribute()? method.isSynthetic() : method.name().indexOf('$') >= 0) {
              return myDepth;
            }
          }
        }

        if (!myIgnoreFilters) {
          if(settings.SKIP_GETTERS) {
            boolean isGetter = ApplicationManager.getApplication().runReadAction(new Computable<Boolean>(){
              public Boolean compute() {
                final PsiMethod psiMethod = PsiTreeUtil.getParentOfType(PositionUtil.getContextElement(context), PsiMethod.class);
                return (psiMethod != null && DebuggerUtils.isSimpleGetter(psiMethod))? Boolean.TRUE : Boolean.FALSE;
              }
            }).booleanValue();

            if(isGetter) {
              return StepRequest.STEP_OUT;
            }
          }

          if (frameProxy != null) {
            if (settings.SKIP_CONSTRUCTORS) {
              final Location location = frameProxy.location();
              final Method method = location.method();
              if (method != null && method.isConstructor()) {
                return StepRequest.STEP_OUT;
              }
            }

            if (settings.SKIP_CLASSLOADERS) {
              final Location location = frameProxy.location();
              if (DebuggerUtilsEx.isAssignableFrom("java.lang.ClassLoader", location.declaringType())) {
                return StepRequest.STEP_OUT;
              }
            }
          }
        }
        // smart step feature
        if (myMethodFilter != null) {
          if (myMethodFilter instanceof BreakpointStepMethodFilter) {
            // continue stepping if stop criterion is implemented as breakpoint request
            return StepRequest.STEP_OUT;
          }
          if (frameProxy != null) {
            if (!myMethodFilter.locationMatches(context.getDebugProcess(), frameProxy.location())) {
              return StepRequest.STEP_OUT;
            }
            myTargetMethodMatched = true;
          }
        }
      }
    }
    catch (VMDisconnectedException e) {
    }
    catch (EvaluateException e) {
      LOG.error(e);
    }
    return STOP;
  }

}
