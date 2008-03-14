/*
 * @author max
 */
package com.intellij.psi;

import com.intellij.lang.Language;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public abstract class PsiFileFactory {
  public static PsiFileFactory getInstance(Project project) {
    return ServiceManager.getService(project, PsiFileFactory.class);
  }

  /**
   * Creates a file from the specified text.
   *
   * @param name the name of the file to create (the extension of the name determines the file type).
   * @param text the text of the file to create.
   * @return the created file.
   * @throws com.intellij.util.IncorrectOperationException if the file type with specified extension is binary.
   */
  @NotNull
  public abstract PsiFile createFileFromText(@NotNull @NonNls String name, @NotNull @NonNls String text);

  @NotNull
  public abstract PsiFile createFileFromText(@NonNls @NotNull String fileName, @NotNull FileType fileType, @NotNull CharSequence text);

  @NotNull
  public abstract PsiFile createFileFromText(@NonNls @NotNull String name, @NotNull FileType fileType, @NotNull CharSequence text,
                                      long modificationStamp, boolean physical);

  @NotNull
  public abstract PsiFile createFileFromText(@NonNls @NotNull String name, @NotNull FileType fileType, @NotNull CharSequence text,
                                      long modificationStamp, boolean physical, boolean markAsCopy);

  public abstract PsiFile createFileFromText(@NotNull String name, @NotNull Language language, @NotNull String text);

  public abstract PsiFile createFileFromText(@NotNull String name, @NotNull Language language, @NotNull String text,
                                             boolean physical, boolean markAsCopy);

  public abstract PsiFile createFileFromText(FileType fileType, String fileName, CharSequence chars, int startOffset, int endOffset);
}