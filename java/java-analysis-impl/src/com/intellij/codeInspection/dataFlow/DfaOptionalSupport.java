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
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author anet, peter
 */
public class DfaOptionalSupport {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.dataFlow.DfaOptionalSupport");
  private static final String GUAVA_OPTIONAL = "com.google.common.base.Optional";

  public static final CallMatcher JDK_OPTIONAL_OF_NULLABLE = CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_OPTIONAL, "ofNullable").parameterCount(1);
  public static final CallMatcher GUAVA_OPTIONAL_FROM_NULLABLE = CallMatcher.staticCall(GUAVA_OPTIONAL, "fromNullable").parameterCount(1);
  public static final CallMatcher OPTIONAL_OF_NULLABLE = CallMatcher.anyOf(JDK_OPTIONAL_OF_NULLABLE, GUAVA_OPTIONAL_FROM_NULLABLE);

  @Nullable
  static LocalQuickFix registerReplaceOptionalOfWithOfNullableFix(@NotNull PsiExpression qualifier) {
    final PsiElement call = findCallExpression(qualifier);
    final PsiMethod method = call == null ? null : ((PsiMethodCallExpression)call).resolveMethod();
    final PsiClass containingClass = method == null ? null : method.getContainingClass();
    if (containingClass != null && "of".equals(method.getName())) {
      final String qualifiedName = containingClass.getQualifiedName();
      if (CommonClassNames.JAVA_UTIL_OPTIONAL.equals(qualifiedName)) {
        return new ReplaceOptionalCallFix("ofNullable", false);
      }
      if (GUAVA_OPTIONAL.equals(qualifiedName)) {
        return new ReplaceOptionalCallFix("fromNullable", false);
      }
    }
    return null;
  }

  private static PsiMethodCallExpression findCallExpression(@NotNull PsiElement anchor) {
    final PsiElement argList = PsiUtil.skipParenthesizedExprUp(anchor).getParent();
    if (argList instanceof PsiExpressionList) {
      final PsiElement parent = argList.getParent();
      if (parent instanceof PsiMethodCallExpression) {
        return (PsiMethodCallExpression)parent;
      }
    }
    return null;
  }

  @Nullable
  static LocalQuickFix createReplaceOptionalOfNullableWithEmptyFix(@NotNull PsiElement anchor) {
    final PsiMethodCallExpression parent = findCallExpression(anchor);
    if (parent == null) return null;
    boolean jdkOptional = JDK_OPTIONAL_OF_NULLABLE.test(parent);
    return new ReplaceOptionalCallFix(jdkOptional ? "empty" : "absent", true);
  }

  @Nullable
  static LocalQuickFix createReplaceOptionalOfNullableWithOfFix(@NotNull PsiElement anchor) {
    final PsiMethodCallExpression parent = findCallExpression(anchor);
    if (parent == null) return null;
    return new ReplaceOptionalCallFix("of", false);
  }

  static boolean isOptionalGetMethodName(String name) {
    return "get".equals(name) || "getAsDouble".equals(name) || "getAsInt".equals(name) || "getAsLong".equals(name);
  }

  private static class ReplaceOptionalCallFix implements LocalQuickFix {
    private final String myTargetMethodName;
    private final boolean myClearArguments;

    public ReplaceOptionalCallFix(final String targetMethodName, boolean clearArguments) {
      myTargetMethodName = targetMethodName;
      myClearArguments = clearArguments;
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace with '." + myTargetMethodName + "()'";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiMethodCallExpression
        methodCallExpression = PsiTreeUtil.getParentOfType(descriptor.getPsiElement(), PsiMethodCallExpression.class);
      if (methodCallExpression != null) {
        ExpressionUtils.bindCallTo(methodCallExpression, myTargetMethodName);
        if (myClearArguments) {
          PsiExpressionList argList = methodCallExpression.getArgumentList();
          PsiExpression[] args = argList.getExpressions();
          if (args.length > 0) {
            argList.deleteChildRange(args[0], args[args.length - 1]);
          }
        }
      }
    }
  }
}
