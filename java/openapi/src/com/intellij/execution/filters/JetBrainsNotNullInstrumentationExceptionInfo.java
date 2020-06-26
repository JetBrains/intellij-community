// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.filters;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.ClassUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JetBrainsNotNullInstrumentationExceptionInfo extends ExceptionInfo {
  /**
   * @see com.intellij.compiler.notNullVerification.NotNullVerifyingInstrumenter.NotNullState#getNullParamMessage(String)
   */
  private static final Pattern INSTRUMENTATION_MESSAGE_PATTERN = Pattern.compile(
    "Argument for @(?:\\w+) parameter '(\\w+)' of (\\S+)\\.(\\w+) must not be null");
  private final String myParameterName;
  private final String myClassName;
  private final String myMethodName;
  private final String myFullClassName;
  private final int myWantLines;

  private JetBrainsNotNullInstrumentationExceptionInfo(int offset,
                                                       @NotNull String exceptionClassName,
                                                       @NotNull String exceptionMessage,
                                                       @NotNull String parameterName,
                                                       @NotNull String className,
                                                       @NotNull String methodName,
                                                       int wantLines) {
    super(offset, exceptionClassName, exceptionMessage);
    myParameterName = parameterName;
    myFullClassName = className;
    myClassName = StringUtil.getShortName(className, '/');
    myMethodName = methodName;
    myWantLines = wantLines;
  }

  @Override
  PsiElement matchSpecificExceptionElement(@NotNull PsiElement element) {
    if (myWantLines != 0) return null;
    return getArgument(element);
  }

  private PsiExpression getArgument(PsiElement element) {
    if (!(element instanceof PsiIdentifier)) return null;
    if (!element.getText().equals(myMethodName)) return null;
    PsiReferenceExpression ref = ObjectUtils.tryCast(element.getParent(), PsiReferenceExpression.class);
    if (ref == null) return null;
    PsiCallExpression call = ObjectUtils.tryCast(ref.getParent(), PsiMethodCallExpression.class);
    if (call == null) return null;
    PsiExpressionList argumentList = call.getArgumentList();
    if (argumentList == null) return null;
    PsiMethod method = call.resolveMethod();
    if (method == null) return null;
    PsiClass psiClass = method.getContainingClass();
    if (psiClass == null) return null;
    if (!myClassName.equals(psiClass.getName())) {
      PsiClass aClass = ClassUtil.findPsiClass(method.getManager(), myFullClassName.replace('/', '.'), null, true);
      if (aClass == null || !aClass.isInheritor(psiClass, true)) return null;
      PsiMethod subClassMethod = aClass.findMethodBySignature(method, false);
      if (subClassMethod == null) return null;
      method = subClassMethod;
    }
    PsiParameter[] parameters = method.getParameterList().getParameters();
    for (int i = 0; i < parameters.length; i++) {
      if (parameters[i].getName().equals(myParameterName)) {
        PsiExpression[] expressions = argumentList.getExpressions();
        if (expressions.length > i) {
          return expressions[i];
        }
        return null;
      }
    }
    return null;
  }

  @Override
  public ExceptionInfo consumeStackLine(String line) {
    switch (myWantLines) {
      case 2:
        if (line.contains(myClassName+".$$$reportNull$$$0")) {
          return new JetBrainsNotNullInstrumentationExceptionInfo(getClassNameOffset(), getExceptionClassName(), getExceptionMessage(),
                                                                  myParameterName, myFullClassName, myMethodName, 1);
        }
        break;
      case 1:
        if (line.contains(myClassName+"."+myMethodName)) {
          return new JetBrainsNotNullInstrumentationExceptionInfo(getClassNameOffset(), getExceptionClassName(), getExceptionMessage(),
                                                                  myParameterName, myFullClassName, myMethodName, 0);
        }
    }
    return null;
  }

  static JetBrainsNotNullInstrumentationExceptionInfo tryCreate(int offset,
                                                                @NotNull String exceptionClassName,
                                                                @NotNull String exceptionMessage) {
    if (!exceptionClassName.equals("java.lang.IllegalArgumentException")) return null;
    if (!exceptionMessage.startsWith("Argument ")) return null;
    Matcher matcher = INSTRUMENTATION_MESSAGE_PATTERN.matcher(exceptionMessage);
    if (!matcher.matches()) return null;
    String parameterName = matcher.group(1);
    String className = matcher.group(2);
    String methodName = matcher.group(3);
    return new JetBrainsNotNullInstrumentationExceptionInfo(offset, exceptionClassName, exceptionMessage, parameterName, className,
                                                            methodName, 2);
  }
}
