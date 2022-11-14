// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.surroundWith;

import com.intellij.openapi.application.WriteActionAware;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayFactory;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Defines a single template which can be used in <em>Code | Surround With</em> action.
 *
 * @see SurroundDescriptor
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/surround-with.html">Surround With (IntelliJ Platform Docs)</a>
 */
public interface Surrounder extends WriteActionAware {
  Surrounder[] EMPTY_ARRAY = new Surrounder[0];
  ArrayFactory<Surrounder> myArrayFactory = count -> count == 0 ? EMPTY_ARRAY : new Surrounder[count];

  /**
   * @return the user-visible name of the <em>Surround With</em> template
   */
  @NlsActions.ActionText
  String getTemplateDescription();

  /**
   * Checks if the template can be used to surround the specified range of elements.
   *
   * @param elements the elements to be surrounded
   * @return {@code true} if the template is applicable to the elements, {@code false} otherwise
   */
  boolean isApplicable(PsiElement @NotNull [] elements);

  /**
   * Performs the <em>Code | Surround With</em> action on the specified range of elements.
   *
   * @param elements the elements to be surrounded
   * @return range to select/to position the caret
   */
  @Nullable
  TextRange surroundElements(@NotNull Project project,
                             @NotNull Editor editor,
                             PsiElement @NotNull [] elements) throws IncorrectOperationException;
}
