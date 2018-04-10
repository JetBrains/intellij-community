// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.concurrencyAnnotations;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.DummyHolder;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * check locks according to http://www.javaconcurrencyinpractice.com/annotations/doc/net/jcip/annotations/GuardedBy.html
 */
public class UnknownGuardInspection extends AbstractBaseJavaLocalInspectionTool {

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.CONCURRENCY_ANNOTATION_ISSUES;
  }

  @Override
  @Nls
  @NotNull
  public String getDisplayName() {
    return "Unknown @GuardedBy field";
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

    public Visitor(ProblemsHolder holder) {
      myHolder = holder;
    }

    @Override
    public void visitAnnotation(PsiAnnotation annotation) {
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
      myHolder.registerProblem(member, "Unknown @GuardedBy reference #ref #loc");
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
      if (expression instanceof PsiReferenceExpression) {
        final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)expression;
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
        if (!(target instanceof PsiField)) {
          return false;
        }
        final PsiField field = (PsiField)target;
        final PsiType type = field.getType();
        if (type instanceof PsiPrimitiveType) {
          return false;
        }
        final PsiExpression qualifier = referenceExpression.getQualifierExpression();
        return qualifier == null || isValidGuard(qualifier, context);
      }
      else if (expression instanceof PsiMethodCallExpression) {
        final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
        final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
        if (!argumentList.isEmpty()) {
          return false;
        }
        final JavaResolveResult result = methodCallExpression.resolveMethodGenerics();
        if (!result.isAccessible() || !result.isValidResult()) {
          return false;
        }
        final PsiElement element = result.getElement();
        if (!(element instanceof PsiMethod)) {
          return false;
        }
        final PsiMethod method = (PsiMethod)element;
        final PsiType type = method.getReturnType();
        if (type instanceof PsiPrimitiveType) {
          return false;
        }
        final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
        final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
        return qualifierExpression == null || isValidGuard(qualifierExpression, context);
      }
      else if (expression instanceof PsiThisExpression) {
        final PsiThisExpression thisExpression = (PsiThisExpression)expression;
        final PsiJavaCodeReferenceElement qualifier = thisExpression.getQualifier();
        if (qualifier == null) {
          return true;
        }
        final JavaResolveResult result = qualifier.advancedResolve(false);
        if (!result.isValidResult() || !result.isAccessible()) {
          return false;
        }
        final PsiElement target = result.getElement();
        if (!(target instanceof PsiClass)) {
          return false;
        }
        final PsiClass aClass = (PsiClass)target;
        return InheritanceUtil.hasEnclosingInstanceInScope(aClass, context, false, false);
      }
      else if (expression instanceof PsiClassObjectAccessExpression) {
        final PsiClassObjectAccessExpression classObjectAccessExpression = (PsiClassObjectAccessExpression)expression;
        final PsiTypeElement operand = classObjectAccessExpression.getOperand();
        final PsiType type = operand.getType();
        if (!(type instanceof PsiClassType)) {
          return false;
        }
        final PsiClassType classType = (PsiClassType)type;
        final PsiClass target = classType.resolve();
        return target != null;
      }
      return false;
    }

    @Override
    public void visitDocTag(PsiDocTag psiDocTag) {
      super.visitDocTag(psiDocTag);
      if (!JCiPUtil.isGuardedByTag(psiDocTag)) {
        return;
      }
      final String guardValue = JCiPUtil.getGuardValue(psiDocTag);
      if (isValidGuardText(guardValue, psiDocTag)) {
        return;
      }
      myHolder.registerProblem(psiDocTag, "Unknown @GuardedBy reference \"" + guardValue + "\" #loc");
    }
  }
}
