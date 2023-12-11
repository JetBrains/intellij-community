/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.packageDependencies;

import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

public final class JavaDependencyVisitorFactory extends DependencyVisitorFactory {
  @NotNull
  @Override
  public PsiElementVisitor getVisitor(@NotNull DependenciesBuilder.DependencyProcessor processor, @NotNull VisitorOptions options) {
    return new MyVisitor(processor, options);
  }

  private static class MyVisitor extends JavaRecursiveElementVisitor {
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