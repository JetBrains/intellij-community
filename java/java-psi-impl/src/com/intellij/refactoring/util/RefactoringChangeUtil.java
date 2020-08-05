// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.JavaPsiConstructorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class RefactoringChangeUtil {
  private static final Logger LOG = Logger.getInstance(RefactoringChangeUtil.class);

  public static PsiType getTypeByExpression(PsiExpression expr) {
    PsiType type = expr != null ? expr.getType() : null;
    if (type == null) {
      if (expr instanceof PsiArrayInitializerExpression) {
        PsiExpression[] initializers = ((PsiArrayInitializerExpression)expr).getInitializers();
        if (initializers.length > 0) {
          PsiType initType = getTypeByExpression(initializers[0]);
          if (initType == null) return null;
          return initType.createArrayType();
        }
      }

      if (expr instanceof PsiReferenceExpression && PsiUtil.isOnAssignmentLeftHand(expr)) {
        return getTypeByExpression(((PsiAssignmentExpression)expr.getParent()).getRExpression());
      }
      return null;
    }

    return GenericsUtil.getVariableTypeByExpressionType(type);
  }

  public static PsiReferenceExpression qualifyReference(@NotNull PsiReferenceExpression referenceExpression,
                                                        @NotNull PsiMember member,
                                                        @Nullable final PsiClass qualifyingClass) throws IncorrectOperationException {
    PsiManager manager = referenceExpression.getManager();
    PsiMethodCallExpression methodCallExpression = PsiTreeUtil.getParentOfType(referenceExpression, PsiMethodCallExpression.class, true);
    while (methodCallExpression != null) {
      if (JavaPsiConstructorUtil.isConstructorCall(methodCallExpression)) {
        return referenceExpression;
      }
      methodCallExpression = PsiTreeUtil.getParentOfType(methodCallExpression, PsiMethodCallExpression.class, true);
    }
    PsiReferenceExpression expressionFromText;
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(manager.getProject());
    if (qualifyingClass == null) {
      PsiClass parentClass = PsiTreeUtil.getParentOfType(referenceExpression, PsiClass.class);
      final PsiClass containingClass = member.getContainingClass();
      if (parentClass != null && !InheritanceUtil.isInheritorOrSelf(parentClass, containingClass, true)) {
        while (parentClass != null && !InheritanceUtil.isInheritorOrSelf(parentClass, containingClass, true)) {
          parentClass = PsiTreeUtil.getParentOfType(parentClass, PsiClass.class, true);
        }
        LOG.assertTrue(parentClass != null);
        expressionFromText = (PsiReferenceExpression)factory.createExpressionFromText("A.this." + member.getName(), null);
        PsiThisExpression thisExpression = (PsiThisExpression)expressionFromText.getQualifierExpression();
        assert thisExpression != null; // just created A.this.name expression, thus thisExpression and its qualifier are non-null
        PsiJavaCodeReferenceElement qualifier = thisExpression.getQualifier();
        assert qualifier != null;
        qualifier.replace(factory.createClassReferenceElement(parentClass));
      }
      else {
        final PsiModifierListOwner staticElement = PsiUtil.getEnclosingStaticElement(referenceExpression, null);
        if (staticElement != null && containingClass != null && !PsiTreeUtil.isAncestor(staticElement, containingClass, false)) {
          return referenceExpression;
        }
        else {
          expressionFromText = (PsiReferenceExpression)factory.createExpressionFromText("this." + member.getName(), null);
        }
      }
    }
    else {
      expressionFromText = (PsiReferenceExpression)factory.createExpressionFromText("A." + member.getName(), null);
      expressionFromText.setQualifierExpression(factory.createReferenceExpression(qualifyingClass));
    }
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(manager.getProject());
    expressionFromText = (PsiReferenceExpression)codeStyleManager.reformat(expressionFromText);
    return (PsiReferenceExpression)referenceExpression.replace(expressionFromText);
  }

  public static PsiClass getThisClass(@NotNull PsiElement place) {
    PsiElement parent = place.getContext();
    if (parent == null) return null;
    PsiElement prev = null;
    while (true) {
      if (parent instanceof PsiClass) {
        if (!(parent instanceof PsiAnonymousClass && ((PsiAnonymousClass)parent).getArgumentList() == prev)) {
          return (PsiClass)parent;
        }
      }
      prev = parent;
      parent = parent.getContext();
      if (parent == null) return null;
    }
  }

  /**
   * Calculates class or interface where referenced member should be searched
   * @param expression reference to the class member
   * @return class based on the type of the qualifier expression,
   *         or containing class, if {@code expression} is not qualified
   */
  @Nullable
  public static PsiClass getQualifierClass(@NotNull PsiReferenceExpression expression) {
    PsiExpression qualifierExpression = expression.getQualifierExpression();
    if (qualifierExpression != null) {
      PsiType expressionType = qualifierExpression.getType();
      if (expressionType instanceof PsiCapturedWildcardType) {
        expressionType = ((PsiCapturedWildcardType)expressionType).getUpperBound();
      }
      PsiClass aClass = PsiUtil.resolveClassInType(expressionType);
      if (aClass != null) return aClass;
      if (qualifierExpression instanceof PsiReferenceExpression) {
        PsiElement qResolved = ((PsiReferenceExpression)qualifierExpression).resolve();
        return qResolved instanceof PsiClass ? (PsiClass)qResolved : null;
      }
      return null;
    }
    return getThisClass(expression);
  }

  static <T extends PsiQualifiedExpression> T createQualifiedExpression(@NotNull PsiManager manager,
                                                                        PsiClass qualifierClass,
                                                                        @NotNull String qName) throws IncorrectOperationException {
     PsiElementFactory factory = JavaPsiFacade.getElementFactory(manager.getProject());
     if (qualifierClass != null) {
       T qualifiedThis = (T)factory.createExpressionFromText("q." + qName, qualifierClass);
       qualifiedThis = (T)CodeStyleManager.getInstance(manager.getProject()).reformat(qualifiedThis);
       PsiJavaCodeReferenceElement thisQualifier = qualifiedThis.getQualifier();
       LOG.assertTrue(thisQualifier != null);
       thisQualifier.bindToElement(qualifierClass);
       return qualifiedThis;
     }
     else {
       return (T)factory.createExpressionFromText(qName, null);
     }
   }

  public static PsiThisExpression createThisExpression(PsiManager manager, PsiClass qualifierClass) throws IncorrectOperationException {
    return createQualifiedExpression(manager, qualifierClass, "this");
  }

  public static PsiSuperExpression createSuperExpression(PsiManager manager, PsiClass qualifierClass) throws IncorrectOperationException {
    return createQualifiedExpression(manager, qualifierClass, "super");
  }
}
