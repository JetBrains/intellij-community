// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethodObject.reflect;

import com.intellij.psi.*;
import com.intellij.refactoring.extractMethodObject.ItemToReplaceDescriptor;
import com.intellij.refactoring.util.LambdaRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ReflectionAccessorToEverything {
  private final PsiClass myOuterClass;
  private final PsiElementFactory myElementFactory;

  public ReflectionAccessorToEverything(@NotNull PsiClass generatedInnerClass, @NotNull PsiElementFactory elementFactory) {
    myOuterClass = generatedInnerClass;
    myElementFactory = elementFactory;
  }

  public void grantAccessThroughReflection(@NotNull PsiMethodCallExpression generatedMethodCall) {
    MyInaccessibleMethodReferencesVisitor methodReferencesVisitor = new MyInaccessibleMethodReferencesVisitor();
    myOuterClass.accept(methodReferencesVisitor);
    methodReferencesVisitor.replaceAll();

    MyInaccessibleItemsVisitor inaccessibleItemsVisitor = new MyInaccessibleItemsVisitor();
    myOuterClass.accept(inaccessibleItemsVisitor);

    inaccessibleItemsVisitor.myReplaceDescriptors
      .forEach(descriptor -> descriptor.replace(myOuterClass, myElementFactory, generatedMethodCall));
  }

  private class MyInaccessibleItemsVisitor extends JavaRecursiveElementVisitor {
    private final List<ItemToReplaceDescriptor> myReplaceDescriptors = new ArrayList<>();

    @Override
    public void visitParameter(PsiParameter parameter) {
      super.visitParameter(parameter);
      addIfNotNull(ParameterDescriptor.createIfInaccessible(parameter));
    }

    @Override
    public void visitMethod(PsiMethod method) {
      super.visitMethod(method);
      addIfNotNull(MethodDeclarationDescriptor.createIfInaccessible(method, myOuterClass));
    }

    @Override
    public void visitThisExpression(PsiThisExpression expression) {
      super.visitThisExpression(expression);
      addIfNotNull(ThisReferenceDescriptor.createIfInaccessible(expression));
    }

    @Override
    public void visitDeclarationStatement(PsiDeclarationStatement statement) {
      super.visitDeclarationStatement(statement);
      addIfNotNull(LocalVariableDeclarationDescriptor.createIfInaccessible(statement));
    }

    @Override
    public void visitField(PsiField field) {
      super.visitField(field);
      addIfNotNull(FieldDeclarationDescriptor.createIfInaccessible(field));
    }

    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      addIfNotNull(FieldDescriptor.createIfInaccessible(myOuterClass, expression));
    }

    @Override
    public void visitNewExpression(PsiNewExpression expression) {
      super.visitNewExpression(expression);
      PsiAnonymousClass anonymousClass = expression.getAnonymousClass();
      // TODO: check if anonymous class is accessible
      if (anonymousClass != null || expression.getArrayInitializer() != null) return;
      addIfNotNull(ConstructorDescriptor.createIfInaccessible(expression));
    }

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      addIfNotNull(MethodDescriptor.createIfInaccessible(myOuterClass, expression));
    }

    private void addIfNotNull(@Nullable ItemToReplaceDescriptor descriptor) {
      if (descriptor != null) {
        myReplaceDescriptors.add(descriptor);
      }
    }
  }

  private static class MyInaccessibleMethodReferencesVisitor extends JavaRecursiveElementVisitor {
    private final List<PsiMethodReferenceExpression> myMethodReferencesToReplace = new ArrayList<>();

    @Override
    public void visitMethodReferenceExpression(PsiMethodReferenceExpression expression) {
      if (!PsiReflectionAccessUtil.isAccessibleMethodReference(expression)) {
        myMethodReferencesToReplace.add(expression);
      }
    }

    private void replaceAll() {
      for (PsiMethodReferenceExpression referenceExpression : myMethodReferencesToReplace) {
        LambdaRefactoringUtil.convertMethodReferenceToLambda(referenceExpression, false, true);
      }
    }
  }
}
