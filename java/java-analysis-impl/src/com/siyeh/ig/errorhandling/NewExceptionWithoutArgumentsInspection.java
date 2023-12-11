// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.errorhandling;

import com.intellij.codeInsight.options.JavaClassValidator;
import com.intellij.codeInspection.AddToInspectionOptionListFix;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.java.JavaBundle;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.codeInspection.options.OptPane.pane;
import static com.intellij.codeInspection.options.OptPane.stringList;

public final class NewExceptionWithoutArgumentsInspection extends BaseInspection {
  public final List<String> exceptions = new ArrayList<>();

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    //noinspection DialogTitleCapitalization
    return InspectionGadgetsBundle.message("new.exception.without.arguments.problem.descriptor");
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      stringList("exceptions", JavaBundle.message("label.ignored.exceptions"),
                 new JavaClassValidator().withSuperClass(CommonClassNames.JAVA_LANG_EXCEPTION)
                   .withTitle(InspectionGadgetsBundle.message("choose.exception.class"))));
  }

  @Override
  protected @Nullable LocalQuickFix buildFix(Object... infos) {
    String exceptionName = (String)infos[0];
    return new AddToInspectionOptionListFix<>(
      this, JavaAnalysisBundle.message("intention.name.ignore.exception", exceptionName),
      exceptionName, tool -> tool.exceptions);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NewExceptionWithoutArgumentsVisitor();
  }

  private class NewExceptionWithoutArgumentsVisitor extends BaseInspectionVisitor {

    @Override
    public void visitNewExpression(@NotNull PsiNewExpression expression) {
      super.visitNewExpression(expression);
      final PsiExpressionList argumentList = expression.getArgumentList();
      if (argumentList == null || !argumentList.isEmpty()) {
        return;
      }
      final PsiJavaCodeReferenceElement classReference = expression.getClassReference();
      if (classReference == null) {
        return;
      }
      final PsiElement target = classReference.resolve();
      if (!(target instanceof PsiClass aClass)) {
        return;
      }
      if (exceptions.contains(aClass.getQualifiedName())) return;
      if (!InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_LANG_EXCEPTION)) {
        return;
      }
      if (hasAccessibleConstructorWithParameters(aClass, expression)) {
        registerNewExpressionError(expression, aClass.getQualifiedName());
      }
    }

    @Override
    public void visitMethodReferenceExpression(@NotNull PsiMethodReferenceExpression expression) {
      super.visitMethodReferenceExpression(expression);
      if (!expression.isConstructor()) {
        return;
      }
      final PsiType type = PsiMethodReferenceUtil.getQualifierType(expression);
      if (!InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_LANG_EXCEPTION)) {
        return;
      }
      final PsiElement target = expression.resolve();
      if (!(target instanceof PsiMethod method)) {
        return;
      }
      if (method.getParameterList().getParametersCount() != 0) {
        return;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null || !hasAccessibleConstructorWithParameters(aClass, expression)) {
        return;
      }
      final PsiElement qualifier = expression.getQualifier();
      if (qualifier == null) {
        return;
      }
      registerError(qualifier, aClass.getQualifiedName());
    }

    private static boolean hasAccessibleConstructorWithParameters(PsiClass aClass, PsiElement context) {
      final PsiMethod[] constructors = aClass.getConstructors();
      for (PsiMethod constructor : constructors) {
        final PsiParameterList parameterList = constructor.getParameterList();
        final int count = parameterList.getParametersCount();
        if (count <= 0) {
          continue;
        }
        final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(context.getProject()).getResolveHelper();
        if (resolveHelper.isAccessible(constructor, context, aClass)) {
          return true;
        }
      }
      return false;
    }
  }
}
