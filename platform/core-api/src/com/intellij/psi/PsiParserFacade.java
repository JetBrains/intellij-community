// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public interface PsiParserFacade {
  /**
   * Creates an PsiWhiteSpace with the specified text.
   *
   * @param s the text of whitespace
   * @return the created whitespace instance.
   * @throws IncorrectOperationException if the text does not specify a valid whitespace.
   */
  @NotNull
  PsiElement createWhiteSpaceFromText(@NotNull @NonNls String s) throws IncorrectOperationException;

  /**
   * Creates a line comment for the specified language file type.
   */
  @NotNull
  PsiComment createLineCommentFromText(@NotNull LanguageFileType fileType, @NotNull String text) throws IncorrectOperationException;

  /**
   * Creates a line comment for the specified language.
   */
  @NotNull
  PsiComment createLineCommentFromText(@NotNull Language language, @NotNull String text) throws IncorrectOperationException;

  /**
   * Creates a block comment for the specified language.
   */
  @NotNull
  PsiComment createBlockCommentFromText(@NotNull Language language, @NotNull String text) throws IncorrectOperationException;

  /**
   * Creates a line comment for the specified language or block comment if language doesn't support line ones
   */
  @NotNull
  PsiComment createLineOrBlockCommentFromText(@NotNull Language language, @NotNull String text) throws IncorrectOperationException;

  static PsiParserFacade getInstance(Project project) {
    return project.getService(PsiParserFacade.class);
  }

  /**
   * @deprecated use {@link PsiParserFacade#getInstance(Project)} instead
   */
  @ScheduledForRemoval
  @Deprecated
  final class SERVICE {
    private SERVICE() {
    }

    public static PsiParserFacade getInstance(Project project) {
      return PsiParserFacade.getInstance(project);
    }
  }
}
