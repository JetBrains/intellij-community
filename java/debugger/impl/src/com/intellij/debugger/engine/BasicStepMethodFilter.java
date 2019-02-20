// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine;

import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
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
  private static final String PROXY_CALL_SIGNATURE = "(Ljava/lang/Object;Ljava/lang/reflect/Method;[Ljava/lang/Object;)Ljava/lang/Object;";

  @NotNull
  protected final JVMName myDeclaringClassName;
  @NotNull
  private final String myTargetMethodName;
  @Nullable
  protected final JVMName myTargetMethodSignature;
  private final Range<Integer> myCallingExpressionLines;

  public BasicStepMethodFilter(@NotNull PsiMethod psiMethod, Range<Integer> callingExpressionLines) {
    this(JVMNameUtil.getJVMQualifiedName(psiMethod.getContainingClass()),
         JVMNameUtil.getJVMMethodName(psiMethod),
         JVMNameUtil.getJVMSignature(psiMethod),
         callingExpressionLines);
  }

  protected BasicStepMethodFilter(@NotNull JVMName declaringClassName,
                                  @NotNull String targetMethodName,
                                  @Nullable JVMName targetMethodSignature,
                                  Range<Integer> callingExpressionLines) {
    myDeclaringClassName = declaringClassName;
    myTargetMethodName = targetMethodName;
    myTargetMethodSignature = targetMethodSignature;
    myCallingExpressionLines = callingExpressionLines;
  }

  @Override
  @NotNull
  public String getMethodName() {
    return myTargetMethodName;
  }

  @Override
  public boolean locationMatches(DebugProcessImpl process, Location location) throws EvaluateException {
    return locationMatches(process, location, null);
  }

  @Override
  public boolean locationMatches(DebugProcessImpl process, @NotNull StackFrameProxyImpl frameProxy) throws EvaluateException {
    return locationMatches(process, frameProxy.location(), frameProxy);
  }

  private boolean locationMatches(DebugProcessImpl process, Location location, @Nullable StackFrameProxyImpl stackFrame)
    throws EvaluateException {
    Method method = location.method();
    String name = method.name();
    if (!myTargetMethodName.equals(name)) {
      if (isLambdaCall(process, name, location) || isProxyCall(process, method, stackFrame)) {
        return true;
      }
      return false;
    }
    if (myTargetMethodSignature != null && !signatureMatches(method, myTargetMethodSignature.getName(process))) {
      return false;
    }
    if (RequestHint.isProxyMethod(method)) {
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

  private boolean isProxyCall(DebugProcessImpl process, Method method, @Nullable StackFrameProxyImpl stackFrame) {
    try {
      if (stackFrame != null && PROXY_CALL_SIGNATURE.equals(method.signature())) {
        if ("invoke".equals(method.name())) {
          ReferenceType type = method.declaringType();
          if (!(type instanceof ClassType) ||
              ((ClassType)type).interfaces().stream().map(InterfaceType::name).noneMatch("java.lang.reflect.InvocationHandler"::equals)) {
            return false;
          }
        }
        else if (!DebuggerUtilsEx.isLambdaName(method.name())) {
          return false;
        }
        List<Value> argumentValues = stackFrame.getArgumentValues();
        if (argumentValues.size() == 3) {
          Value proxyValue = argumentValues.get(0);
          if (proxyValue != null) {
            Type proxyType = proxyValue.type();
            if (proxyType instanceof ReferenceType &&
                DebuggerUtilsEx.isAssignableFrom(myDeclaringClassName.getName(process), (ReferenceType)proxyType)) {
              Value methodValue = argumentValues.get(1);
              if (methodValue instanceof ObjectReference) {
                // TODO: no signature check for now
                ReferenceType methodType = ((ObjectReference)methodValue).referenceType();
                return myTargetMethodName.equals(
                  ((StringReference)((ObjectReference)methodValue).getValue(methodType.fieldByName("name"))).value());
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
}
