/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author anet, peter
 */
public class DfaOptionalSupport {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.dataFlow.DfaOptionalSupport");
  private static final String GUAVA_OPTIONAL = "com.google.common.base.Optional";

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
  private static boolean isJdkOptional(@NotNull PsiElement anchor) {
    final PsiElement parent = findCallExpression(anchor);
    PsiMethod method = parent == null ? null : resolveOfNullable(findCallExpression(anchor));
    return method != null && "ofNullable".equals(method.getName());
  }

  @NotNull
  static LocalQuickFix createReplaceOptionalOfNullableWithEmptyFix(@NotNull PsiElement anchor) {
    return new ReplaceOptionalCallFix(isJdkOptional(anchor) ? "empty" : "absent", true);
  }

  @NotNull
  static LocalQuickFix createReplaceOptionalOfNullableWithOfFix() {
    return new ReplaceOptionalCallFix("of", false);
  }

  @Nullable
  public static PsiMethod resolveOfNullable(@NotNull PsiMethodCallExpression expression) {
    String name = expression.getMethodExpression().getReferenceName();
    if ("ofNullable".equals(name) || "fromNullable".equals(name)) {
      PsiMethod method = expression.resolveMethod();
      PsiClass psiClass = method == null ? null : method.getContainingClass();
      String qname = psiClass == null ? null : psiClass.getQualifiedName();
      if (CommonClassNames.JAVA_UTIL_OPTIONAL.equals(qname) || GUAVA_OPTIONAL.equals(qname)) {
        return method;
      }
    }
    return null;
  }

  private static class ReplaceOptionalCallFix implements LocalQuickFix {
    private final String myTargetMethodName;
    private final boolean myClearArguments;

    public ReplaceOptionalCallFix(final String targetMethodName, boolean clearArguments) {
      myTargetMethodName = targetMethodName;
      myClearArguments = clearArguments;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return getFamilyName();
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
        final PsiElement ofNullableExprName =
          ((PsiMethodCallExpression)JavaPsiFacade.getElementFactory(project)
            .createExpressionFromText("Optional." + myTargetMethodName + "(null)", null)).getMethodExpression();
        final PsiElement referenceNameElement = methodCallExpression.getMethodExpression().getReferenceNameElement();
        if (referenceNameElement != null) {
          final PsiElement ofNullableNameElement = ((PsiReferenceExpression)ofNullableExprName).getReferenceNameElement();
          LOG.assertTrue(ofNullableNameElement != null);
          referenceNameElement.replace(ofNullableNameElement);
        }
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
