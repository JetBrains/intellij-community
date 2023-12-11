// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.concurrencyAnnotations;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * check locks according to http://www.javaconcurrencyinpractice.com/annotations/doc/net/jcip/annotations/GuardedBy.html
 */
public final class UnknownGuardInspection extends AbstractBaseJavaLocalInspectionTool {

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return InspectionsBundle.message("group.names.concurrency.annotation.issues");
  }

  @Override
  @NotNull
  public String getShortName() {
    return "UnknownGuard";
  }

  @Override
  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new Visitor(holder);
  }

  private static class Visitor extends JavaElementVisitor {
    private final ProblemsHolder myHolder;

    Visitor(ProblemsHolder holder) {
      myHolder = holder;
    }

    @Override
    public void visitAnnotation(@NotNull PsiAnnotation annotation) {
      super.visitAnnotation(annotation);
      if (!JCiPUtil.isGuardedByAnnotation(annotation)) {
        return;
      }
      final String guardValue = JCiPUtil.getGuardValue(annotation);
      if (isValidGuardText(guardValue, annotation)) {
        return;
      }
      final PsiAnnotationMemberValue member = annotation.findAttributeValue("value");
      if (member == null) {
        return;
      }
      myHolder.registerProblem(member, JavaAnalysisBundle.message("unknown.guardedby.reference.ref.loc"));
    }

    private static boolean isValidGuardText(@Nullable String guardText, @NotNull PsiElement context) {
      if (guardText == null || "itself".equals(guardText)) {
        return false;
      }
      try {
        final JavaPsiFacade facade = JavaPsiFacade.getInstance(context.getProject());
        final PsiExpression expression = facade.getElementFactory().createExpressionFromText(guardText, context);
        return isValidGuard(expression, context);
      } catch (IncorrectOperationException ignore) {
        return false;
      }
    }

    private static boolean isValidGuard(PsiExpression expression, PsiElement context) {
      if (expression instanceof PsiReferenceExpression referenceExpression) {
        final JavaResolveResult result = referenceExpression.advancedResolve(false);
        if (!result.isAccessible() || !result.isValidResult()) {
          return false;
        }
        final PsiElement target = result.getElement();
        final PsiElement parent = expression.getParent();
        if (!(parent instanceof DummyHolder)) {
          // checking qualifier
          return target != null;
        }
        if (!(target instanceof PsiField field)) {
          return false;
        }
        final PsiType type = field.getType();
        if (type instanceof PsiPrimitiveType) {
          return false;
        }
        final PsiExpression qualifier = referenceExpression.getQualifierExpression();
        return qualifier == null || isValidGuard(qualifier, context);
      }
      else if (expression instanceof PsiMethodCallExpression methodCallExpression) {
        final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
        if (!argumentList.isEmpty()) {
          return false;
        }
        final JavaResolveResult result = methodCallExpression.resolveMethodGenerics();
        if (!result.isAccessible() || !result.isValidResult()) {
          return false;
        }
        final PsiElement element = result.getElement();
        if (!(element instanceof PsiMethod method)) {
          return false;
        }
        final PsiType type = method.getReturnType();
        if (type instanceof PsiPrimitiveType) {
          return false;
        }
        final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
        final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
        return qualifierExpression == null || isValidGuard(qualifierExpression, context);
      }
      else if (expression instanceof PsiThisExpression thisExpression) {
        final PsiJavaCodeReferenceElement qualifier = thisExpression.getQualifier();
        if (qualifier == null) {
          return true;
        }
        final JavaResolveResult result = qualifier.advancedResolve(false);
        if (!result.isValidResult() || !result.isAccessible()) {
          return false;
        }
        final PsiElement target = result.getElement();
        if (!(target instanceof PsiClass aClass)) {
          return false;
        }
        return InheritanceUtil.hasEnclosingInstanceInScope(aClass, context, false, false);
      }
      else if (expression instanceof PsiClassObjectAccessExpression classObjectAccessExpression) {
        final PsiTypeElement operand = classObjectAccessExpression.getOperand();
        final PsiType type = operand.getType();
        if (!(type instanceof PsiClassType classType)) {
          return false;
        }
        final PsiClass target = classType.resolve();
        return target != null;
      }
      return false;
    }

    @Override
    public void visitDocTag(@NotNull PsiDocTag psiDocTag) {
      super.visitDocTag(psiDocTag);
      if (!JCiPUtil.isGuardedByTag(psiDocTag)) {
        return;
      }
      final String guardValue = JCiPUtil.getGuardValue(psiDocTag);
      if (isValidGuardText(guardValue, psiDocTag)) {
        return;
      }
      myHolder.registerProblem(psiDocTag, JavaAnalysisBundle.message("unknown.guardedby.reference.0.loc", guardValue));
    }
  }
}
