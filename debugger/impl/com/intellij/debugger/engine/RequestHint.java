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
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.StringBuilderSpinAllocator;
import com.sun.jdi.Location;
import com.sun.jdi.Method;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.request.StepRequest;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RequestHint {
  public static final int STOP = 0;
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.engine.RequestHint");
  private final int myDepth;
  private SourcePosition myPosition;
  private int myFrameCount;
  private VirtualMachineProxyImpl myVirtualMachineProxy;

  private final @Nullable SmartStepFilter myTargetMethodSignature;
  private boolean myIgnoreFilters = false;
  private boolean myRestoreBreakpoints = false;
  private boolean mySkipThisMethod = false;

  public static final class SmartStepFilter {
    private final String myDeclaringClassName;
    private final String myTargetMethodSignature;

    public SmartStepFilter(PsiMethod psiMethod, final DebugProcessImpl debugProcess) throws EvaluateException {
      myDeclaringClassName = JVMNameUtil.getJVMQualifiedName(psiMethod.getContainingClass()).getName(debugProcess);
      final JVMName methodSignature = JVMNameUtil.getJVMSignature(psiMethod);
      @NonNls final StringBuilder builder = StringBuilderSpinAllocator.alloc();
      try {
        builder.append(psiMethod.isConstructor()? "<init>" : psiMethod.getName());
        builder.append(methodSignature.getName(debugProcess));
        myTargetMethodSignature = builder.toString();
      }
      finally {
        StringBuilderSpinAllocator.dispose(builder);
      }
    }

    public boolean shouldStopAtLocation(Location location) {
      final Method method = location.method();
      final StringBuilder builder = StringBuilderSpinAllocator.alloc();
      try {
        builder.append(method.name());
        builder.append(method.signature());
        if (!myTargetMethodSignature.equals(builder.toString())) {
          return false;
        }
      }
      finally{
        StringBuilderSpinAllocator.dispose(builder);
      }
      final ReferenceType declaringType = method.declaringType();
      return DebuggerUtilsEx.isAssignableFrom(myDeclaringClassName, declaringType);
    }

  }

  public RequestHint(final ThreadReferenceProxyImpl stepThread, final SuspendContextImpl suspendContext, @NotNull SmartStepFilter smartStepFilter) {
    this(stepThread, suspendContext, StepRequest.STEP_INTO, smartStepFilter);
  }

  public RequestHint(final ThreadReferenceProxyImpl stepThread, final SuspendContextImpl suspendContext, int depth) {
    this(stepThread, suspendContext, depth, null);
  }

  private RequestHint(final ThreadReferenceProxyImpl stepThread, final SuspendContextImpl suspendContext, int depth, SmartStepFilter smartStepFilter) {
    final DebugProcessImpl debugProcess = suspendContext.getDebugProcess();
    myDepth = depth;
    myTargetMethodSignature = smartStepFilter;
    myVirtualMachineProxy = debugProcess.getVirtualMachineProxy();

    try {
      myFrameCount = stepThread.frameCount();

      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          myPosition = ContextUtil.getSourcePosition(new StackFrameContext() {
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
      myPosition = null;
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
    return mySkipThisMethod ? StepRequest.STEP_OUT : myDepth;
  }

  public int getNextStepDepth(final SuspendContextImpl context) {
    try {
      if (myPosition != null) {
        final SourcePosition locationPosition = ApplicationManager.getApplication().runReadAction(new Computable<SourcePosition>() {
          public SourcePosition compute() {
            return ContextUtil.getSourcePosition(context);          
          }
        });

        if(locationPosition == null) {
          return myDepth;
        }

        if (myDepth == StepRequest.STEP_OVER || myDepth == StepRequest.STEP_INTO) {
          int frameCount = -1;
          if (context.getFrameProxy() != null) {
            try {
              frameCount = context.getFrameProxy().threadProxy().frameCount();
            }
            catch (EvaluateException e) {
            }
          }
          final boolean filesEqual = myPosition.getFile().equals(locationPosition.getFile());
          if (filesEqual && myPosition.getLine() == locationPosition.getLine() && myFrameCount == frameCount) {
            return myDepth;
          }
          if (myDepth == StepRequest.STEP_INTO) {
            // check if we are still at the line from which the stepping begun
            if (filesEqual && myFrameCount == frameCount && myPosition.getLine() != locationPosition.getLine()) {
              return STOP;
            }
          }
        }
      }
      // the rest of the code makes sence for depth == STEP_INTO only

      if (myDepth == StepRequest.STEP_INTO) {
        DebuggerSettings settings = DebuggerSettings.getInstance();
        if (settings.SKIP_SYNTHETIC_METHODS) {
          Location location = context.getFrameProxy().location();
          Method method = location.method();
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

          if (settings.SKIP_CONSTRUCTORS) {
            Location location = context.getFrameProxy().location();
            Method method = location.method();
            if (method != null && method.isConstructor()) {
              return StepRequest.STEP_OUT;
            }
          }

          if (settings.SKIP_CLASSLOADERS) {
            Location location = context.getFrameProxy().location();
            if (DebuggerUtilsEx.isAssignableFrom("java.lang.ClassLoader", location.declaringType())) {
              return StepRequest.STEP_OUT;
            }
          }
        }
        // smart step feature
        if (myTargetMethodSignature != null) {
          final Location location = context.getFrameProxy().location();
          if (!myTargetMethodSignature.shouldStopAtLocation(location)) {
            return StepRequest.STEP_OUT;
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
