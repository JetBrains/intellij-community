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
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.StringBuilderSpinAllocator;
import com.sun.jdi.Location;
import com.sun.jdi.Method;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.request.StepRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class RequestHint {
  public static final int STOP = 0;
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.engine.RequestHint");
  private final int myDepth;
  private SourcePosition myPosition;
  private int myFrameCount;
  private VirtualMachineProxyImpl myVirtualMachineProxy;

  private final @Nullable String myTargetMethodSignature;
  private boolean myIgnoreFilters = false;
  private boolean myRestoreBreakpoints = false;
  private boolean mySkipThisMethod = false;

  public RequestHint(final ThreadReferenceProxyImpl stepThread, final SuspendContextImpl suspendContext, @NotNull String targetMethodSignature) {
    this(stepThread, suspendContext, StepRequest.STEP_INTO, targetMethodSignature);
  }

  public RequestHint(final ThreadReferenceProxyImpl stepThread, final SuspendContextImpl suspendContext, int depth) {
    this(stepThread, suspendContext, depth, null);
  }

  private RequestHint(final ThreadReferenceProxyImpl stepThread, final SuspendContextImpl suspendContext, int depth, String targetMethodSignature) {
    final DebugProcessImpl debugProcess = suspendContext.getDebugProcess();
    myDepth = depth;
    myTargetMethodSignature = targetMethodSignature;
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
                return (psiMethod != null && isSimpleGetter(psiMethod))? Boolean.TRUE : Boolean.FALSE;
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
          final Method method = location.method();
          final StringBuilder builder = StringBuilderSpinAllocator.alloc();
          try {
            builder.append(method.declaringType().signature());
            builder.append(".");
            builder.append(method.name());
            builder.append(method.signature());
            if (!myTargetMethodSignature.equals(builder.toString())) {
              return StepRequest.STEP_OUT;
            }
          }
          finally{
            StringBuilderSpinAllocator.dispose(builder);
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

  private static boolean isSimpleGetter(PsiMethod method){
    final PsiCodeBlock body = method.getBody();
    if(body == null){
      return false;
    }

    final PsiStatement[] statements = body.getStatements();
    if(statements.length != 1){
      return false;
    }
    
    final PsiStatement statement = statements[0];
    if(!(statement instanceof PsiReturnStatement)){
      return false;
    }
    
    final PsiExpression value = ((PsiReturnStatement)statement).getReturnValue();
    if(!(value instanceof PsiReferenceExpression)){
      return false;
    }
    
    final PsiReferenceExpression reference = (PsiReferenceExpression)value;
    final PsiExpression qualifier = reference.getQualifierExpression();
    //noinspection HardCodedStringLiteral
    if(qualifier != null && !"this".equals(qualifier.getText())) {
      return false;
    }
    
    final PsiElement referent = reference.resolve();
    if(referent == null) {
      return false;
    }
    
    if(!(referent instanceof PsiField)) {
      return false;
    }
    
    return ((PsiField)referent).getContainingClass().equals(method.getContainingClass());
  }
}
