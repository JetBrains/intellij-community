// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethodObject.reflect;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.ClassUtil;
import com.intellij.refactoring.extractMethodObject.ItemToReplaceDescriptor;
import com.intellij.refactoring.extractMethodObject.PsiReflectionAccessUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public class ConstructorReflectionAccessor extends ReflectionAccessorBase<ConstructorReflectionAccessor.ConstructorDescriptor> {
  private static final Logger LOG = Logger.getInstance(ConstructorReflectionAccessor.class);

  protected ConstructorReflectionAccessor(@NotNull PsiClass psiClass,
                                          @NotNull PsiElementFactory elementFactory) {
    super(psiClass, elementFactory);
  }

  @Override
  protected List<ConstructorDescriptor> findItemsToReplace(@NotNull PsiElement element) {
    List<ConstructorDescriptor> result = new ArrayList<>();

    element.accept(new JavaRecursiveElementVisitor() {
      @Override
      public void visitNewExpression(PsiNewExpression expression) {
        super.visitNewExpression(expression);
        if (expression.getAnonymousClass() != null || expression.getArrayInitializer() != null) return;
        ConstructorDescriptor descriptor = ConstructorDescriptor.createIfInaccessible(expression);
        if (descriptor != null) {
          result.add(descriptor);
        }
      }
    });

    return result;
  }

  @Override
  protected void grantAccess(@NotNull ConstructorDescriptor descriptor) {
    String className = ClassUtil.getJVMClassName(descriptor.psiClass);
    String returnType = PsiReflectionAccessUtil.getAccessibleReturnType(descriptor.psiClass);
    PsiExpressionList argumentList = descriptor.newExpression.getArgumentList();
    if (className == null || argumentList == null || returnType == null) {
      LOG.debug("expression is incomplete");
      return;
    }

    String methodName = PsiReflectionAccessUtil.getUniqueMethodName(getOuterClass(), "new" + className);
    ReflectionAccessMethodBuilder methodBuilder = new ReflectionAccessMethodBuilder(methodName);
    methodBuilder.accessedConstructor(className)
                 .setStatic(getOuterClass().hasModifierProperty(PsiModifier.STATIC))
                 .setReturnType(returnType);
    if (descriptor.constructor != null) {
      methodBuilder.addParameters(descriptor.constructor.getParameterList());
    }

    PsiMethod newPsiMethod = methodBuilder.build(getElementFactory(), getOuterClass());
    getOuterClass().add(newPsiMethod);
    String args = StreamEx.of(argumentList.getExpressions()).map(x -> x.getText()).joining(", ", "(", ")");
    String newCallExpression = newPsiMethod.getName() + args;
    descriptor.newExpression.replace(getElementFactory().createExpressionFromText(newCallExpression, descriptor.newExpression));
  }

  public static class ConstructorDescriptor implements ItemToReplaceDescriptor {
    public final PsiNewExpression newExpression;
    public final PsiClass psiClass;

    // null if and only if default constructor is used
    @Nullable public final PsiMethod constructor;

    @Nullable
    public static ConstructorDescriptor createIfInaccessible(@NotNull PsiNewExpression expression) {
      PsiMethod constructor = expression.resolveConstructor();
      if (constructor != null) {
        PsiClass containingClass = constructor.getContainingClass();
        if (containingClass != null && !PsiReflectionAccessUtil.isAccessibleMember(constructor)) {
          return new ConstructorDescriptor(expression, constructor, containingClass);
        }
      }
      else {
        PsiJavaCodeReferenceElement classReference = expression.getClassReference();
        if (classReference instanceof PsiClass && !PsiReflectionAccessUtil.isAccessible((PsiClass)classReference)) {
          return new ConstructorDescriptor(expression, null, (PsiClass)classReference);
        }
      }

      return null;
    }

    public ConstructorDescriptor(@NotNull PsiNewExpression expression, @Nullable PsiMethod constructor, PsiClass psiClass) {
      newExpression = expression;
      this.constructor = constructor;
      this.psiClass = psiClass;
    }
  }
}
