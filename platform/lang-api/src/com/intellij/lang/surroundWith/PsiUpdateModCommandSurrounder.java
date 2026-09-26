// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.surroundWith;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * A convenient abstract class to implement {@link ModCommandSurrounder}
 * that performs the surrounding operation on non-physical copies of the PSI elements
 * and only modifies the file where the elements are located and the corresponding editor state.
 * <p>
 * Handles the boilerplate of calling {@link ModCommand#psiUpdate} and obtaining writable copies
 * of the elements; subclasses only need to implement
 * {@link #surroundElements(ActionContext, PsiElement[], ModPsiUpdater)}.
 *
 * @see ModCommandSurrounder
 * @see com.intellij.modcommand.PsiUpdateModCommandAction
 */
@ApiStatus.Experimental
public abstract class PsiUpdateModCommandSurrounder extends ModCommandSurrounder {

  @Override
  public final @NotNull ModCommand surroundElements(@NotNull ActionContext context, @NotNull PsiElement @NotNull [] elements) {
    return ModCommand.psiUpdate(context,
                                updater ->
                                  surroundElements(context,
                                                   ContainerUtil.map(elements, element -> getWritable(updater, element)).toArray(PsiElement.EMPTY_ARRAY),
                                                   updater));
  }

  /**
   * Retrieves a writable version of the given PSI element using the provided updater.
   * <p>
   * Subclasses can override this method to customize how the writable copy is obtained,
   * e.g., to add some additional user holder information.
   *
   * @param updater the updater responsible for creating writable copies of PSI elements
   * @param element the PSI element to be made writable
   * @return a writable version of the provided PSI element
   */
  public @NotNull PsiElement getWritable(@NotNull ModPsiUpdater updater, @NotNull PsiElement element) {
    return updater.getWritable(element);
  }

  /**
   * Performs the surround operation in background on non-physical copies of the elements,
   * to record changes and construct the appropriate {@link ModCommand}.
   *
   * @param context        original context in which the action is executed (its file refers to the physical file)
   * @param elementsInCopy writable non-physical copies of the elements to be surrounded.
   * @param updater        updater to support advanced modification operations if necessary
   */
  public abstract void surroundElements(@NotNull ActionContext context,
                                        @NotNull PsiElement @NotNull [] elementsInCopy,
                                        @NotNull ModPsiUpdater updater);
}
