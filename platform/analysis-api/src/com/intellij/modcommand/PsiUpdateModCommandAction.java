// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

/**
 * A convenient abstract class to implement {@link ModCommandAction}
 * that starts on a given {@link PsiElement}, or a {@link PsiElement} of a given type under the caret
 * and only modifies the file where the element is located and the corresponding editor state
 *
 * @param <E> type of the starting element
 */
@ApiStatus.Experimental
public abstract class PsiUpdateModCommandAction<E extends PsiElement> extends PsiBasedModCommandAction<E> {
  /**
   * Constructs an instance, which is bound to a specified element
   *
   * @param element element to start the action at.
   */
  protected PsiUpdateModCommandAction(@NotNull E element) {
    super(element);
  }

  /**
   * Constructs an instance, which will look for an element 
   * of a specified class at the caret offset.
   *
   * @param elementClass element class
   */
  protected PsiUpdateModCommandAction(@NotNull Class<E> elementClass) {
    super(elementClass);
  }

  /**
   * Constructs an instance, which will look for an element
   * of a specified class at the caret offset, satisfying the specified filter.
   *
   * @param elementClass element class
   * @param filter       predicate to check the elements: elements that don't satisfy will be skipped
   */
  protected PsiUpdateModCommandAction(@NotNull Class<E> elementClass, @NotNull Predicate<? super E> filter) {
    super(elementClass, filter);
  }

  @Override
  protected final @NotNull ModCommand perform(@NotNull ActionContext context, @NotNull E element) {
    return ModCommand.psiUpdate(element, (e, upd) -> invoke(context, e, upd));
  }

  /**
   * Performs the action in background on the non-physical copy of the element, to record changes
   * and construct the appropriate {@link ModCommand}.
   * 
   * @param context original context in which the action is executed (its file refers to the physical file)
   * @param element starting element copy. It's allowed only to modify the file where this element is located
   * @param updater updater to change caret position and selection if necessary
   */
  protected abstract void invoke(@NotNull ActionContext context, @NotNull E element, @NotNull ModPsiUpdater updater);
}
