// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethodObject.reflect;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.ClassUtil;
import com.intellij.refactoring.extractMethodObject.ItemToReplaceDescriptor;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vitaliy.Bibaev
 */
public final class ConstructorDescriptor implements ItemToReplaceDescriptor {
  private static final Logger LOG = Logger.getInstance(ConstructorDescriptor.class);
  private final PsiNewExpression myNewExpression;
  private final PsiClass myPsiClass;

  // null if and only if default constructor is used
  @Nullable private final PsiMethod myConstructor;
  private ConstructorDescriptor(@NotNull PsiNewExpression expression, @Nullable PsiMethod constructor, @NotNull PsiClass psiClass) {
    myNewExpression = expression;
    this.myConstructor = constructor;
    this.myPsiClass = psiClass;
  }

  @Nullable
  public static ConstructorDescriptor createIfInaccessible(@NotNull PsiNewExpression expression) {
    PsiMethod constructor = expression.resolveConstructor();
    if (constructor != null) {
      PsiClass containingClass = constructor.getContainingClass();
      if (containingClass != null && !PsiReflectionAccessUtil.isPublicMember(constructor)) {
        return new ConstructorDescriptor(expression, constructor, containingClass);
      }
    }
    else {
      PsiJavaCodeReferenceElement classReference = expression.getClassReference();
      PsiElement referent = classReference != null ? classReference.resolve() : null;
      if (referent instanceof PsiClass && !PsiReflectionAccessUtil.isAccessible((PsiClass)referent)) {
        return new ConstructorDescriptor(expression, null, (PsiClass)referent);
      }
    }

    return null;
  }

  @Override
  public void replace(@NotNull PsiClass outerClass,
                      @NotNull PsiElementFactory elementFactory,
                      @NotNull PsiMethodCallExpression callExpression) {
    String className = ClassUtil.getJVMClassName(myPsiClass);
    String returnType = PsiReflectionAccessUtil.getAccessibleReturnType(myNewExpression, myPsiClass);
    PsiExpressionList argumentList = myNewExpression.getArgumentList();
    if (className == null || argumentList == null || returnType == null) {
      LOG.warn("code is incomplete: " + myNewExpression);
      return;
    }

    String methodName = PsiReflectionAccessUtil.getUniqueMethodName(outerClass, methodName(className));
    ReflectionAccessMethodBuilder methodBuilder = new ReflectionAccessMethodBuilder(methodName);
    methodBuilder.accessedConstructor(className)
      .setStatic(outerClass.hasModifierProperty(PsiModifier.STATIC))
      .setReturnType(returnType);
    if (myConstructor != null) {
      methodBuilder.addParameters(myConstructor.getParameterList());
    }

    PsiMethod newPsiMethod = methodBuilder.build(elementFactory, outerClass);
    outerClass.add(newPsiMethod);
    String args = StreamEx.of(argumentList.getExpressions()).map(x -> x.getText()).joining(", ", "(", ")");
    String newCallExpression = newPsiMethod.getName() + args;
    myNewExpression.replace(elementFactory.createExpressionFromText(newCallExpression, myNewExpression));
  }

  @NotNull
  private static String methodName(@NotNull String jvmClassName) {
    String name = StringUtil.getShortName(jvmClassName);
    return "new" + StringUtil.capitalize(name);
  }
}
