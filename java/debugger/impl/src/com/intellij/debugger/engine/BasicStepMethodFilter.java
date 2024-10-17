// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine;

import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.Range;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Eugene Zhuravlev
 */
public class BasicStepMethodFilter implements NamedMethodFilter {
  private static final Logger LOG = Logger.getInstance(BasicStepMethodFilter.class);
  private static final String PROXY_CALL_SIGNATURE_POSTFIX = "Ljava/lang/Object;Ljava/lang/reflect/Method;[Ljava/lang/Object;)Ljava/lang/Object;";

  @NotNull
  protected final JVMName myDeclaringClassName;
  @NotNull
  private final String myTargetMethodName;
  @Nullable
  protected final JVMName myTargetMethodSignature;
  private final Range<Integer> myCallingExpressionLines;
  private final int myOrdinal;
  private final boolean myCheckCaller;

  public BasicStepMethodFilter(@NotNull PsiMethod psiMethod, Range<Integer> callingExpressionLines) {
    this(psiMethod, 0, callingExpressionLines);
  }

  public BasicStepMethodFilter(@NotNull PsiMethod psiMethod, int ordinal, Range<Integer> callingExpressionLines) {
    this(JVMNameUtil.getJVMQualifiedName(psiMethod.getContainingClass()),
         JVMNameUtil.getJVMMethodName(psiMethod),
         JVMNameUtil.getJVMSignature(psiMethod),
         ordinal,
         callingExpressionLines,
         checkCaller(psiMethod));
  }

  protected BasicStepMethodFilter(@NotNull JVMName declaringClassName,
                                  @NotNull String targetMethodName,
                                  @Nullable JVMName targetMethodSignature,
                                  int ordinal,
                                  Range<Integer> callingExpressionLines,
                                  boolean checkCaller) {
    myDeclaringClassName = declaringClassName;
    myTargetMethodName = targetMethodName;
    myTargetMethodSignature = targetMethodSignature;
    myCallingExpressionLines = callingExpressionLines;
    myOrdinal = ordinal;
    myCheckCaller = checkCaller;
  }

  private static boolean checkCaller(PsiMethod method) {
    PsiClass aClass = method.getContainingClass();
    return aClass != null && aClass.hasAnnotation("java.lang.FunctionalInterface");
  }

  @Override
  @NotNull
  public String getMethodName() {
    return myTargetMethodName;
  }

  @Override
  public boolean locationMatches(DebugProcessImpl process, Location location) throws EvaluateException {
    return locationMatches(process, location, null, false);
  }

  @Override
  public boolean locationMatches(DebugProcessImpl process, Location location, @Nullable StackFrameProxyImpl frameProxy) throws EvaluateException {
    return locationMatches(process, location, frameProxy, false);
  }

  private boolean locationMatches(DebugProcessImpl process, Location location, @Nullable StackFrameProxyImpl stackFrame, boolean caller)
    throws EvaluateException {
    Method method = location.method();
    String name = method.name();
    if (!myTargetMethodName.equals(name)) {
      if (isLambdaCall(process, name, location)) {
        return true;
      }
      if (!caller && myCheckCaller) {
        int index = stackFrame.getFrameIndex();
        StackFrameProxyImpl callerFrame = stackFrame.threadProxy().frame(index + 1);
        if (callerFrame != null) {
          return locationMatches(process, callerFrame.location(), callerFrame, true);
        }
      }
      return false;
    }
    if (myTargetMethodSignature != null && !signatureMatches(method, myTargetMethodSignature.getName(process))) {
      return false;
    }
    if (!caller && RequestHint.isProxyMethod(method)) {
      return false;
    }
    String declaringClassNameName = myDeclaringClassName.getName(process);
    boolean res = DebuggerUtilsEx.isAssignableFrom(declaringClassNameName, location.declaringType());
    if (!res && !method.isStatic() && stackFrame != null) {
      ObjectReference thisObject = stackFrame.thisObject();
      if (thisObject != null) {
        res = DebuggerUtilsEx.isAssignableFrom(declaringClassNameName, thisObject.referenceType());
      }
    }
    return res;
  }

  private boolean isLambdaCall(DebugProcessImpl process, String name, Location location) {
    if (DebuggerUtilsEx.isLambdaName(name)) {
      SourcePosition position = process.getPositionManager().getSourcePosition(location);
      return ReadAction.compute(() -> {
        PsiElement psiMethod = DebuggerUtilsEx.getContainingMethod(position);
        if (psiMethod instanceof PsiLambdaExpression) {
          PsiType type = ((PsiLambdaExpression)psiMethod).getFunctionalInterfaceType();
          PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(type);
          if (type != null && interfaceMethod != null && myTargetMethodName.equals(interfaceMethod.getName())) {
            try {
              return InheritanceUtil.isInheritor(type, myDeclaringClassName.getName(process).replace('$', '.'));
            }
            catch (EvaluateException e) {
              LOG.info(e);
            }
          }
        }
        return false;
      });
    }
    return false;
  }

  public boolean proxyCheck(Location location, SuspendContextImpl context, RequestHint hint) {
    DebugProcessImpl debugProcess = context.getDebugProcess();
    if (isProxyCall(debugProcess, location.method(), context.getFrameProxy())) {
      if (!DebugProcessImpl.isPositionFiltered(location)) {
        return true;
      }
      try {
        StepIntoMethodBreakpoint breakpoint =
          new StepIntoMethodBreakpoint(myDeclaringClassName.getName(debugProcess),
                                       myTargetMethodName,
                                       myTargetMethodSignature != null ? myTargetMethodSignature.getName(debugProcess) : null,
                                       debugProcess.getProject());
        DebugProcessImpl.prepareAndSetSteppingBreakpoint(context, breakpoint, hint, false);
      }
      catch (EvaluateException e) {
        LOG.error(e);
      }
    }
    return false;
  }

  private boolean isProxyCall(DebugProcessImpl process, Method method, @Nullable StackFrameProxyImpl stackFrame) {
    try {
      String signature = method.signature();
      if (stackFrame != null && signature != null && signature.endsWith(PROXY_CALL_SIGNATURE_POSTFIX)) {
        String methodName = method.name();
        boolean match = false;
        // standard
        if ("invoke".equals(methodName)) {
          ReferenceType type = method.declaringType();
          if ((type instanceof ClassType) &&
              ((ClassType)type).interfaces().stream().map(InterfaceType::name).anyMatch("java.lang.reflect.InvocationHandler"::equals)) {
            match = true;
          }
        }
        if (DebuggerUtilsEx.isLambdaName(methodName)) {
          match = true;
        }
        else {
          ObjectReference thisObject = stackFrame.thisObject();
          if (thisObject != null && StringUtil.containsIgnoreCase(thisObject.referenceType().name(), "CGLIB")) {
            match = true;
          }
        }
        if (match) {
          List<Value> argumentValues = stackFrame.getArgumentValues();
          int size = argumentValues.size();
          if (size >= 3) {
            Value proxyValue = argumentValues.get(size - 3);
            if (proxyValue != null) {
              Type proxyType = proxyValue.type();
              if (proxyType instanceof ReferenceType &&
                  DebuggerUtilsEx.isAssignableFrom(myDeclaringClassName.getName(process), proxyType)) {
                Value methodValue = argumentValues.get(size - 2);
                if (methodValue instanceof ObjectReference) {
                  // TODO: no signature check for now
                  ReferenceType methodType = ((ObjectReference)methodValue).referenceType();
                  return myTargetMethodName.equals(
                    ((StringReference)((ObjectReference)methodValue).getValue(DebuggerUtils.findField(methodType, "name"))).value());
                }
              }
            }
          }
        }
      }
    }
    catch (EvaluateException e) {
      LOG.info(e);
    }
    return false;
  }

  private static boolean signatureMatches(Method method, final String expectedSignature) throws EvaluateException {
    if (expectedSignature.equals(method.signature())) {
      return true;
    }
    // check if there are any bridge methods that match
    //noinspection SSBasedInspection
    for (Method candidate : method.declaringType().methodsByName(method.name())) {
      if (candidate != method && candidate.isBridge() && expectedSignature.equals(candidate.signature())) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  @Override
  public Range<Integer> getCallingExpressionLines() {
    return myCallingExpressionLines;
  }

  @Override
  public int getSkipCount() {
    return myOrdinal;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "{" +
           "myDeclaringClassName=" + myDeclaringClassName +
           ", myTargetMethodName='" + myTargetMethodName + '\'' +
           ", myTargetMethodSignature=" + myTargetMethodSignature +
           ", myCallingExpressionLines=" + myCallingExpressionLines +
           ", myOrdinal=" + myOrdinal +
           ", myCheckCaller=" + myCheckCaller +
           '}';
  }
}
