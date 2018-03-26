// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethodObject.reflect;

import com.intellij.psi.*;
import com.intellij.refactoring.extractMethodObject.ItemToReplaceDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Vitaliy.Bibaev
 */
public abstract class ReferenceReflectionAccessorBase<T extends ItemToReplaceDescriptor> extends ReflectionAccessorBase<T> {
  public ReferenceReflectionAccessorBase(@NotNull PsiClass psiClass, @NotNull PsiElementFactory elementFactory) {
    super(psiClass, elementFactory);
  }

  @Override
  protected List<T> findItemsToReplace(@NotNull PsiElement element) {
    List<T> result = new ArrayList<>();
    element.accept(new JavaRecursiveElementVisitor() {
      @Override
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        final T descriptor = createDescriptor(expression);
        if (descriptor != null) {
          result.add(descriptor);
        }
      }
    });

    return result;
  }

  @Nullable
  protected abstract T createDescriptor(@NotNull PsiReferenceExpression expression);
}
