// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethodObject.reflect;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.ClassUtil;
import com.intellij.refactoring.extractMethodObject.ItemToReplaceDescriptor;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author Vitaliy.Bibaev
 */
public class MethodReflectionAccessor extends ReflectionAccessorBase<MethodReflectionAccessor.MethodCallDescriptor> {
  private static final Logger LOG = Logger.getInstance(MethodReflectionAccessor.class);

  protected MethodReflectionAccessor(@NotNull PsiClass psiClass,
                                     @NotNull PsiElementFactory elementFactory) {
    super(psiClass, elementFactory);
  }

  @Override
  protected List<MethodCallDescriptor> findItemsToReplace(@NotNull PsiElement element) {
    List<MethodCallDescriptor> result = new ArrayList<>();

    element.accept(new JavaRecursiveElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        super.visitMethodCallExpression(expression);
        PsiMethod method = expression.resolveMethod();
        if (method != null && !Objects.equals(method.getContainingClass(), getOuterClass()) &&
            needReplace(method, expression.getMethodExpression())) {
          result.add(new MethodCallDescriptor(expression, method));
        }
      }
    });

    return result;
  }

  @Override
  protected void grantAccess(@NotNull MethodCallDescriptor descriptor) {
    PsiClass outerClass = getOuterClass();
    String returnType = PsiReflectionAccessUtil.getAccessibleReturnType(descriptor.callExpression, resolveMethodReturnType(descriptor));
    PsiClass containingClass = descriptor.method.getContainingClass();
    String containingClassName = containingClass == null ? null : ClassUtil.getJVMClassName(containingClass);
    String name = descriptor.method.getName();
    if (returnType == null) {
      LOG.warn("return type of" + descriptor.method.getName() + " method is null");
      return;
    }

    if (containingClassName == null) {
      LOG.warn("containing class for method \"" + name + "\" not found");
      return;
    }

    String newMethodName = PsiReflectionAccessUtil.getUniqueMethodName(outerClass, "call" + StringUtil.capitalize(name));
    ReflectionAccessMethodBuilder methodBuilder = new ReflectionAccessMethodBuilder(newMethodName);
    PsiMethod newMethod = methodBuilder.accessedMethod(containingClassName, descriptor.method.getName())
                                       .setStatic(outerClass.hasModifierProperty(PsiModifier.STATIC))
                                       .addParameter("java.lang.Object", "object")
                                       .addParameters(descriptor.method.getParameterList())
                                       .setReturnType(returnType)
                                       .build(getElementFactory(), getOuterClass());

    outerClass.add(newMethod);
    String qualifier = qualify(descriptor);
    String args = StreamEx.of(descriptor.callExpression.getArgumentList().getExpressions())
                          .map(x -> x.getText())
                          .prepend(qualifier == null ? "null" : qualifier)
                          .joining(", ", "(", ")");
    String newMethodCallExpression = newMethod.getName() + args;

    descriptor.callExpression.replace(getElementFactory().createExpressionFromText(newMethodCallExpression, descriptor.callExpression));
  }

  private static boolean needReplace(@NotNull PsiMethod method, @NotNull PsiReferenceExpression referenceExpression) {
    return !PsiReflectionAccessUtil.isAccessibleMember(method) ||
           !PsiReflectionAccessUtil.isQualifierAccessible(referenceExpression.getQualifierExpression());
  }

  @Nullable
  private static PsiType resolveMethodReturnType(@NotNull MethodCallDescriptor descriptor) {
    PsiSubstitutor substitutor = descriptor.callExpression.resolveMethodGenerics().getSubstitutor();
    return substitutor.substitute(descriptor.method.getReturnType());
  }

  @Nullable
  private static String qualify(@NotNull MethodCallDescriptor descriptor) {
    String qualifier = PsiReflectionAccessUtil.extractQualifier(descriptor.callExpression.getMethodExpression());
    if (qualifier == null) {
      if (!descriptor.method.hasModifierProperty(PsiModifier.STATIC)) {
        PsiClass containingClass = descriptor.method.getContainingClass();
        if (containingClass != null) {
          qualifier = containingClass.getQualifiedName() + ".this";
        }
      }
    }

    return qualifier;
  }

  public static class MethodCallDescriptor implements ItemToReplaceDescriptor {
    public final PsiMethodCallExpression callExpression;
    public final PsiMethod method;

    public MethodCallDescriptor(@NotNull PsiMethodCallExpression expression, @NotNull PsiMethod method) {
      callExpression = expression;
      this.method = method;
    }
  }
}
