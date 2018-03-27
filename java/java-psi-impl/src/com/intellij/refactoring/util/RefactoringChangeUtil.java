/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.refactoring.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RefactoringChangeUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.util.ChangeUtil");

  @Nullable
  private static String getMethodExpressionName(@Nullable PsiElement element) {
    if (!(element instanceof PsiMethodCallExpression)) return null;
    PsiReferenceExpression methodExpression = ((PsiMethodCallExpression)element).getMethodExpression();
    return methodExpression.getReferenceName();
  }

  public static boolean isSuperOrThisMethodCall(@Nullable PsiElement element) {
    String name = getMethodExpressionName(element);
    return PsiKeyword.SUPER.equals(name) || PsiKeyword.THIS.equals(name);
  }

  public static boolean isSuperMethodCall(@Nullable PsiElement element) {
    String name = getMethodExpressionName(element);
    return PsiKeyword.SUPER.equals(name);
  }

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
    PsiClass refClass = PsiUtil.resolveClassInType(type);
    if (refClass instanceof PsiAnonymousClass) {
      type = ((PsiAnonymousClass)refClass).getBaseClassType();
    }

    return GenericsUtil.getVariableTypeByExpressionType(type);
  }
  
  public static PsiReferenceExpression qualifyReference(@NotNull PsiReferenceExpression referenceExpression,
                                                        @NotNull PsiMember member,
                                                        @Nullable final PsiClass qualifyingClass) throws IncorrectOperationException {
    PsiManager manager = referenceExpression.getManager();
    PsiMethodCallExpression methodCallExpression = PsiTreeUtil.getParentOfType(referenceExpression, PsiMethodCallExpression.class, true);
    while (methodCallExpression != null) {
      if (isSuperOrThisMethodCall(methodCallExpression)) {
        return referenceExpression;
      }
      methodCallExpression = PsiTreeUtil.getParentOfType(methodCallExpression, PsiMethodCallExpression.class, true);
    }
    PsiReferenceExpression expressionFromText;
    final PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
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

  static <T extends PsiQualifiedExpression> T createQualifiedExpression(@NotNull PsiManager manager,
                                                                        PsiClass qualifierClass,
                                                                        @NotNull String qName) throws IncorrectOperationException {
     PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
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
