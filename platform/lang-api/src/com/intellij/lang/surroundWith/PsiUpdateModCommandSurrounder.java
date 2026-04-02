// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.surroundWith;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

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

  public @NotNull PsiElement getWritable(@NotNull ModPsiUpdater updater, @NotNull PsiElement element) {
    return updater.getWritable(element);
  }

  /**
   * Surrounds the given elements with specific code, using the provided context and updater
   * to modify the PSI structure accordingly.
   *
   * @param context        the context in which the action is invoked, including the project, file, caret position, and other contextual information
   * @param elementsInCopy the elements to be surrounded
   * @param updater        the updater used to modify PSI elements during the surrounding operation
   */
  public abstract void surroundElements(@NotNull ActionContext context,
                                        @NotNull PsiElement @NotNull [] elementsInCopy,
                                        @NotNull ModPsiUpdater updater);
}
