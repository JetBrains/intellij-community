// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethodObject;

import com.intellij.psi.*;
import com.intellij.refactoring.extractMethodObject.reflect.ConstructorReflectionAccessor;
import com.intellij.refactoring.extractMethodObject.reflect.FieldReflectionAccessor;
import com.intellij.refactoring.extractMethodObject.reflect.MethodReferenceReflectionAccessor;
import com.intellij.refactoring.extractMethodObject.reflect.MethodReflectionAccessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

class ReflectionAccessorToEverything {
  private final PsiClass myOuterClass;
  private final PsiElementFactory myElementFactory;

  ReflectionAccessorToEverything(@NotNull PsiClass generatedInnerClass, @NotNull PsiElementFactory elementFactory) {
    myOuterClass = generatedInnerClass;
    myElementFactory = elementFactory;
  }

  void grantAccessThroughReflection(@NotNull PsiMethodCallExpression generatedMethodCall) {
    MyInaccessibleItemsVisitor inaccessibleItemsVisitor = new MyInaccessibleItemsVisitor();
    myOuterClass.accept(inaccessibleItemsVisitor);

    inaccessibleItemsVisitor.myReplaceDescriptors
      .forEach(descriptor -> descriptor.replace(myOuterClass, myElementFactory, generatedMethodCall));
  }

  private class MyInaccessibleItemsVisitor extends JavaRecursiveElementVisitor {
    private final List<ItemToReplaceDescriptor> myReplaceDescriptors = new ArrayList<>();

    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      addIfNotNull(FieldReflectionAccessor.createIfInaccessible(myOuterClass, expression));
      addIfNotNull(MethodReferenceReflectionAccessor.createIfInaccessible(expression));
    }

    @Override
    public void visitNewExpression(PsiNewExpression expression) {
      super.visitNewExpression(expression);
      PsiAnonymousClass anonymousClass = expression.getAnonymousClass();
      // TODO: check if anonymous class is accessible
      if (anonymousClass != null || expression.getArrayInitializer() != null) return;
      addIfNotNull(ConstructorReflectionAccessor.createIfInaccessible(expression));
    }

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      addIfNotNull(MethodReflectionAccessor.createIfInaccessible(myOuterClass, expression));
    }

    private void addIfNotNull(@Nullable ItemToReplaceDescriptor descriptor) {
      if (descriptor != null) {
        myReplaceDescriptors.add(descriptor);
      }
    }
  }
}
