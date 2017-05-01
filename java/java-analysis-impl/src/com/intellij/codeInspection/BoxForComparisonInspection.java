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
package com.intellij.codeInspection;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Tagir Valeev
 */
public class BoxForComparisonInspection extends BaseJavaBatchLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if(!PsiUtil.getLanguageLevel(holder.getFile()).isAtLeast(LanguageLevel.JDK_1_4)) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression call) {
        PsiElement nameElement = call.getMethodExpression().getReferenceNameElement();
        if (nameElement == null) return;
        String name = nameElement.getText();
        if (!"compareTo".equals(name)) return;
        PsiExpression[] args = call.getArgumentList().getExpressions();
        if (args.length != 1) return;
        PsiExpression arg = args[0];
        PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
        if (qualifier == null) return;
        PsiClassType boxedType = getBoxedType(call);
        if (boxedType == null) return;
        PsiPrimitiveType primitiveType = PsiPrimitiveType.getUnboxedType(boxedType);
        if (primitiveType == null || !PsiType.DOUBLE.equals(primitiveType) && !PsiType.FLOAT.equals(primitiveType) &&
                                     !PsiUtil.isLanguageLevel7OrHigher(call)) {
          return;
        }
        PsiExpression left = extractPrimitive(boxedType, primitiveType, qualifier);
        if (left == null) return;
        PsiExpression right = extractPrimitive(boxedType, primitiveType, arg);
        if (right == null) return;
        holder.registerProblem(nameElement, "Can be replaced with '" + boxedType.getClassName() + ".compare'",
                               new ReplaceWithPrimitiveCompareFix(boxedType.getCanonicalText()));
      }
    };
  }

  @Nullable
  static PsiClassType getBoxedType(PsiMethodCallExpression call) {
    PsiMethod method = call.resolveMethod();
    if (method == null) return null;
    PsiClass aClass = method.getContainingClass();
    if (aClass == null) return null;
    return JavaPsiFacade.getElementFactory(call.getProject()).createType(aClass);
  }

  @Nullable
  static PsiExpression extractPrimitive(PsiClassType type, PsiPrimitiveType primitiveType, PsiExpression expression) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (expression == null) return null;
    if (primitiveType.equals(expression.getType())) {
      return expression;
    }
    if (expression instanceof PsiMethodCallExpression) {
      PsiMethodCallExpression call = (PsiMethodCallExpression)expression;
      if (!"valueOf".equals(call.getMethodExpression().getReferenceName())) return null;
      PsiExpression[] args = call.getArgumentList().getExpressions();
      if (args.length != 1) return null;
      PsiMethod method = call.resolveMethod();
      if (method == null || type.resolve() != method.getContainingClass()) return null;
      return checkPrimitive(args[0]);
    }
    if (expression instanceof PsiTypeCastExpression) {
      PsiTypeCastExpression cast = (PsiTypeCastExpression)expression;
      if (!type.equals(cast.getType())) return null;
      return checkPrimitive(cast.getOperand());
    }
    if (expression instanceof PsiNewExpression) {
      PsiNewExpression newExpression = (PsiNewExpression)expression;
      if (!type.equals(newExpression.getType())) return null;
      PsiExpressionList argumentList = newExpression.getArgumentList();
      if (argumentList == null) return null;
      PsiExpression[] args = argumentList.getExpressions();
      if (args.length != 1) return null;
      if (!(args[0].getType() instanceof PsiPrimitiveType)) return null;
      return checkPrimitive(args[0]);
    }
    return null;
  }

  private static PsiExpression checkPrimitive(PsiExpression expression) {
    return expression != null && expression.getType() instanceof PsiPrimitiveType ? expression : null;
  }

  private static class ReplaceWithPrimitiveCompareFix implements LocalQuickFix {
    private String myClassName;

    public ReplaceWithPrimitiveCompareFix(String className) {
      myClassName = className;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return "Replace with '" + StringUtil.getShortName(myClassName) + ".compare'";
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace with static 'compare' method";
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(descriptor.getStartElement(), PsiMethodCallExpression.class);
      if (call == null) return;
      PsiClassType boxedType = getBoxedType(call);
      if (boxedType == null) return;
      PsiPrimitiveType primitiveType = PsiPrimitiveType.getUnboxedType(boxedType);
      if (primitiveType == null) return;
      PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
      if (qualifier == null) return;
      PsiExpression[] args = call.getArgumentList().getExpressions();
      if (args.length != 1) return;
      PsiExpression left = extractPrimitive(boxedType, primitiveType, qualifier);
      if (left == null) return;
      PsiExpression right = extractPrimitive(boxedType, primitiveType, args[0]);
      if (right == null) return;

      CommentTracker ct = new CommentTracker();
      ct.replaceAndRestoreComments(call, boxedType.getCanonicalText() + ".compare(" + ct.text(left) + "," + ct.text(right) + ")");
    }
  }
}
