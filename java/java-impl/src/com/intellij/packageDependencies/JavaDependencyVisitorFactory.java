// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packageDependencies;

import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

public final class JavaDependencyVisitorFactory extends DependencyVisitorFactory {
  @Override
  public @NotNull PsiElementVisitor getVisitor(@NotNull DependenciesBuilder.DependencyProcessor processor, @NotNull VisitorOptions options) {
    return new MyVisitor(processor, options);
  }

  private static class MyVisitor extends JavaRecursiveElementWalkingVisitor {
    private final DependenciesBuilder.DependencyProcessor myProcessor;
    private final VisitorOptions myOptions;

    MyVisitor(DependenciesBuilder.DependencyProcessor processor, VisitorOptions options) {
      myProcessor = processor;
      myOptions = options;
    }

    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
      if (expression.getParent() instanceof PsiReferenceExpression expr && expr.isQualified()) return;
      visitReferenceElement(expression);
    }

    @Override
    public void visitElement(@NotNull PsiElement element) {
      processElement(element);
      super.visitElement(element);
    }

    private void processElement(@NotNull PsiElement element) {
      for (PsiReference ref : element.getReferences()) {
        PsiElement resolved = ref.resolve();
        if (resolved != null) myProcessor.process(ref.getElement(), resolved);
      }
    }

    @Override
    public void visitLiteralExpression(@NotNull PsiLiteralExpression expression) { }

    @Override
    public void visitDocComment(@NotNull PsiDocComment comment) { }

    private void visitImport(@NotNull PsiElement element) {
      if (!myOptions.skipImports()) {
        visitElement(element);
      }
    }

    @Override
    public void visitImportStatement(@NotNull PsiImportStatement statement) {
      visitImport(statement);
    }

    @Override
    public void visitImportStaticStatement(@NotNull PsiImportStaticStatement statement) {
      visitImport(statement);
    }

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);

      PsiMethod psiMethod = expression.resolveMethod();
      if (psiMethod != null) {
        PsiType returnType = psiMethod.getReturnType();
        if (returnType != null) {
          PsiClass psiClass = PsiUtil.resolveClassInType(returnType);
          if (psiClass != null && !(psiClass instanceof PsiTypeParameter)) {
            myProcessor.process(expression, psiClass);
          }
        }
      }
    }
  }
}