package com.intellij.psi;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public interface PsiParserFacade {
  /**
   * Creates an PsiWhiteSpace with the specified text.
   *
   * @param s the text of whitespace
   * @return the created whitespace instance.
   * @throws com.intellij.util.IncorrectOperationException if the text does not specify a valid whitespace.
   */
  @NotNull
  PsiElement createWhiteSpaceFromText(@NotNull @NonNls String s) throws IncorrectOperationException;

  /**
   * Creates a comment for the specified language.
   */
  @NotNull
  PsiComment createLineCommentFromText(@NotNull LanguageFileType fileType, @NotNull String text) throws IncorrectOperationException;

  class SERVICE {
    private SERVICE() {
    }

    public static PsiParserFacade getInstance(Project project) {
      return ServiceManager.getService(project, PsiParserFacade.class);
    }
  }
}
