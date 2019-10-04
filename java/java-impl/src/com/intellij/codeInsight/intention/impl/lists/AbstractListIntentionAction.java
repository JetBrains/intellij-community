// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl.lists;

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class AbstractListIntentionAction<L extends PsiElement, E extends PsiElement> extends PsiElementBaseIntentionAction {
  @Nullable("When failed")
  abstract L extractList(@NotNull PsiElement element);

  @Nullable("When failed")
  abstract List<E> getElements(@NotNull L list);

  @Nullable
  abstract PsiElement prevBreak(@NotNull PsiElement element);

  @Nullable
  abstract PsiElement nextBreak(@NotNull PsiElement element);

  /**
   * Min count of elements for intention to work
   * Expected to be > 0
   */
  protected int minElementCount() {
    return 2;
  }


  /**
   * supposed to delegate to language formatter settings
   * @return true if it requires line break after last element
   */
  abstract boolean needTailBreak(@NotNull E last);

  /**
   * supposed to delegate to language formatter settings
   * @return true if it requires line break before first element
   */
  abstract  boolean needHeadBreak(@NotNull E first);
}
