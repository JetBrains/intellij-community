// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.siyeh.ipp.psiutils;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public final class PsiSelectionSearcher {
  private PsiSelectionSearcher() {
  }

  /**
   * Searches elements in selection
   *
   * @param file                  file to search in
   * @param selection             selected range
   * @param filter                PsiElement filter, e.g. PsiMethodCallExpression.class
   * @param searchChildrenOfFound if true, visitor will look for matching elements in the children of a found element, otherwise will not look inside found element.
   * @param <T>                   type based on PsiElement type
   * @return elements in selection
   */
  @NotNull
  public static <T extends PsiElement> List<T> searchElementsInSelection(@Nullable PsiFile file,
                                                                         @NotNull TextRange selection,
                                                                         @NotNull Class<T> filter, boolean searchChildrenOfFound) {
    if (file == null || file instanceof PsiCompiledElement) {
      return Collections.emptyList();
    }
    final List<T> results = new ArrayList<>();

    final PsiElementVisitor visitor = new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        if (!selection.intersects(element.getTextRange())) {
          return;
        }
        if (filter.isInstance(element)) {
          results.add(filter.cast(element));
          if (!searchChildrenOfFound) {
            return;
          }
        }
        super.visitElement(element);
      }
    };

    file.accept(visitor);
    return results;
  }
}
