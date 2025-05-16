// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.extractMethodObject.reflect;

import com.intellij.psi.*;
import com.intellij.refactoring.extractMethodObject.ItemToReplaceDescriptor;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

public class ReflectionAccessorToEverything {
  private final PsiClass myOuterClass;
  private final PsiElementFactory myElementFactory;

  public ReflectionAccessorToEverything(@NotNull PsiClass generatedInnerClass, @NotNull PsiElementFactory elementFactory) {
    myOuterClass = generatedInnerClass;
    myElementFactory = elementFactory;
  }

  public void grantAccessThroughReflection(@NotNull PsiMethodCallExpression generatedMethodCall) {
    replaceAll(new MyInaccessibleMethodReferencesVisitor(), generatedMethodCall);
    replaceAll(new MyInaccessibleFieldVisitor(), generatedMethodCall);
    replaceAll(new MyInaccessibleItemsVisitor(), generatedMethodCall);
  }

  private void replaceAll(ReplaceVisitor visitor, @NotNull PsiMethodCallExpression generatedMethodCall) {
    myOuterClass.accept(visitor);
    visitor.getReplaceDescriptors().forEach(descriptor -> descriptor.replace(myOuterClass, myElementFactory, generatedMethodCall));
  }

  private class MyInaccessibleItemsVisitor extends ReplaceVisitor {
    @Override
    public void visitParameter(@NotNull PsiParameter parameter) {
      super.visitParameter(parameter);
      addIfNotNull(ParameterDescriptor.createIfInaccessible(parameter));
    }

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      super.visitMethod(method);
      addIfNotNull(MethodDeclarationDescriptor.createIfInaccessible(method, myOuterClass));
    }

    @Override
    public void visitThisExpression(@NotNull PsiThisExpression expression) {
      super.visitThisExpression(expression);
      addIfNotNull(ThisReferenceDescriptor.createIfInaccessible(expression));
    }

    @Override
    public void visitDeclarationStatement(@NotNull PsiDeclarationStatement statement) {
      super.visitDeclarationStatement(statement);
      addIfNotNull(LocalVariableDeclarationDescriptor.createIfInaccessible(statement));
    }

    @Override
    public void visitField(@NotNull PsiField field) {
      super.visitField(field);
      addIfNotNull(FieldDeclarationDescriptor.createIfInaccessible(field));
    }

    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      addIfNotNull(FieldDescriptor.createIfInaccessible(myOuterClass, expression));
    }

    @Override
    public void visitNewExpression(@NotNull PsiNewExpression expression) {
      super.visitNewExpression(expression);
      PsiAnonymousClass anonymousClass = expression.getAnonymousClass();
      // TODO: check if anonymous class is accessible
      if (anonymousClass != null || expression.getArrayInitializer() != null) return;
      addIfNotNull(ConstructorDescriptor.createIfInaccessible(expression));
    }

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      addIfNotNull(MethodDescriptor.createIfInaccessible(myOuterClass, expression));
    }
  }

  private class MyInaccessibleFieldVisitor extends ReplaceVisitor {
    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      addIfNotNull(FieldDescriptor.createIfInaccessible(myOuterClass, expression));
    }

    @Override
    public List<ItemToReplaceDescriptor> getReplaceDescriptors() {
      // first access descriptors, then update descriptors
      return ContainerUtil.sorted(super.getReplaceDescriptors(),
                                  Comparator.comparing(d -> d instanceof FieldDescriptor fieldDescriptor && fieldDescriptor.isUpdate()));
    }
  }

  private static class MyInaccessibleMethodReferencesVisitor extends ReplaceVisitor {
    @Override
    public void visitMethodReferenceExpression(@NotNull PsiMethodReferenceExpression expression) {
      super.visitMethodReferenceExpression(expression);
      addIfNotNull(MethodReferenceDescriptor.createIfInaccessible(expression));
    }
  }

  private abstract static class ReplaceVisitor extends JavaRecursiveElementVisitor {
    private final List<ItemToReplaceDescriptor> myReplaceDescriptors = new SmartList<>();

    void addIfNotNull(@Nullable ItemToReplaceDescriptor descriptor) {
      if (descriptor != null) {
        myReplaceDescriptors.add(descriptor);
      }
    }

    public List<ItemToReplaceDescriptor> getReplaceDescriptors() {
      return myReplaceDescriptors;
    }
  }
}
