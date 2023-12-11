// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.miscGenerics;

import com.intellij.codeInspection.*;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import static com.intellij.util.ObjectUtils.tryCast;

public final class IterableUsedAsVarargInspection extends AbstractBaseJavaLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.isLanguageLevel5OrHigher(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression call) {
        PsiExpressionList argList = call.getArgumentList();
        int argCount = argList.getExpressionCount();
        if (argCount < 2) return;
        JavaResolveResult result = call.getMethodExpression().advancedResolve(false);
        PsiMethod method = (PsiMethod)result.getElement();
        if (method == null || !method.isVarArgs()) return;
        PsiParameterList paramList = method.getParameterList();
        if (paramList.getParametersCount() != argCount) return;
        PsiParameter varArgParam = paramList.getParameters()[argCount - 1];
        PsiEllipsisType type = tryCast(varArgParam.getType(), PsiEllipsisType.class);
        if (type == null) return;
        PsiTypeParameter componentType = tryCast(PsiUtil.resolveClassInClassTypeOnly(type.getComponentType()), PsiTypeParameter.class);
        if (componentType == null || componentType.getOwner() != method) return;
        PsiSubstitutor substitutor = result.getSubstitutor();
        if (!TypeUtils.isJavaLangObject(substitutor.substitute(componentType))) return;
        PsiExpression[] args = argList.getExpressions();
        PsiExpression varArgExpression = args[argCount - 1];
        PsiType elementType = PsiUtil.substituteTypeParameter(varArgExpression.getType(), CommonClassNames.JAVA_LANG_ITERABLE, 0, false);
        if (elementType == null || TypeUtils.isJavaLangObject(elementType)) return;
        PsiClass elementClass = PsiUtil.resolveClassInClassTypeOnly(GenericsUtil.getVariableTypeByExpressionType(elementType));
        if (elementClass == null) return;
        PsiMethodCallExpression callCopy = (PsiMethodCallExpression)call.copy();
        if (callCopy == null) return;
        PsiExpression argCopy = ArrayUtil.getLastElement(callCopy.getArgumentList().getExpressions());
        if (argCopy == null) return;
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(holder.getProject());
        String className = elementClass.getQualifiedName();
        if (className == null) {
          className = elementClass.getName();
          if (className == null) return;
        }
        String replacement = "new " + className + "[0]";
        argCopy.replace(factory.createExpressionFromText(replacement, argCopy));
        JavaResolveResult copyResult = callCopy.getMethodExpression().advancedResolve(false);
        if (copyResult.getElement() == method) {
          PsiType substitutionWithArray = copyResult.getSubstitutor().substitute(componentType);
          if (substitutionWithArray == null || TypeUtils.isJavaLangObject(substitutionWithArray)) return;
        } else {
          PsiMethod newMethod = (PsiMethod)copyResult.getElement();
          if (newMethod == null || !newMethod.isVarArgs() || newMethod.getParameterList().getParametersCount() != argCount) return;
        }
        LocalQuickFix fix = null;
        if (InheritanceUtil.isInheritor(varArgExpression.getType(), CommonClassNames.JAVA_UTIL_COLLECTION)) {
          fix = new AddToArrayFix(className);
        }
        holder.registerProblem(varArgExpression, JavaBundle.message("inspection.collection.used.as.vararg.message"), fix);
      }
    };
  }

  private static class AddToArrayFix extends PsiUpdateModCommandQuickFix {
    private final String myClassName;

    AddToArrayFix(String className) {myClassName = className;}

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return CommonQuickFixBundle.message("fix.call", "toArray(new " + StringUtil.getShortName(myClassName) + "[0])");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiExpression expression = tryCast(element, PsiExpression.class);
      if (expression == null) return;
      if (!InheritanceUtil.isInheritor(expression.getType(), CommonClassNames.JAVA_UTIL_COLLECTION)) return;
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      String fullReplacementText =
        ParenthesesUtils.getText(expression, ParenthesesUtils.METHOD_CALL_PRECEDENCE + 1) + ".toArray(new " + myClassName + "[0])";
      expression.replace(factory.createExpressionFromText(fullReplacementText, expression));
    }
  }
}
