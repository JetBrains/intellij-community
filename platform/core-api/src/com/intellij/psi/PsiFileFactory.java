// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.lang.Language;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public abstract class PsiFileFactory {
  public static final Key<PsiFile> ORIGINAL_FILE = Key.create("ORIGINAL_FILE");

  public static PsiFileFactory getInstance(Project project) {
    return ServiceManager.getService(project, PsiFileFactory.class);
  }

  /**
   * Please use {@link #createFileFromText(String, com.intellij.openapi.fileTypes.FileType, CharSequence)},
   * since file type detecting by file extension becomes vulnerable when file type mappings are changed.
   * <p/>
   * Creates a file from the specified text.
   *
   * @param name the name of the file to create (the extension of the name determines the file type).
   * @param text the text of the file to create.
   * @return the created file.
   * @throws com.intellij.util.IncorrectOperationException
   *          if the file type with specified extension is binary.
   */
  @Deprecated
  @NotNull
  public abstract PsiFile createFileFromText(@NotNull @NonNls String name, @NotNull @NonNls String text);

  @NotNull
  public abstract PsiFile createFileFromText(@NonNls @NotNull String fileName, @NotNull FileType fileType, @NotNull CharSequence text);

  @NotNull
  public abstract PsiFile createFileFromText(@NonNls @NotNull String name, @NotNull FileType fileType, @NotNull CharSequence text,
                                             long modificationStamp, boolean eventSystemEnabled);

  @NotNull
  public abstract PsiFile createFileFromText(@NonNls @NotNull String name, @NotNull FileType fileType, @NotNull CharSequence text,
                                             long modificationStamp, boolean eventSystemEnabled, boolean markAsCopy);

  public abstract PsiFile createFileFromText(@NotNull String name, @NotNull Language language, @NotNull CharSequence text);

  public PsiFile createFileFromText(@NotNull Language language, @NotNull CharSequence text) {
    return createFileFromText("foo.bar", language, text);
  }

  public abstract PsiFile createFileFromText(@NotNull String name, @NotNull Language language, @NotNull CharSequence text,
                                             boolean eventSystemEnabled, boolean markAsCopy);

  public abstract PsiFile createFileFromText(@NotNull String name, @NotNull Language language, @NotNull CharSequence text,
                                             boolean eventSystemEnabled, boolean markAsCopy, boolean noSizeLimit);

  public abstract PsiFile createFileFromText(@NotNull String name, @NotNull Language language, @NotNull CharSequence text,
                                             boolean eventSystemEnabled, boolean markAsCopy, boolean noSizeLimit,
                                             @Nullable VirtualFile original);

  public abstract PsiFile createFileFromText(FileType fileType, String fileName, CharSequence chars, int startOffset, int endOffset);

  @Nullable
  public abstract PsiFile createFileFromText(@NotNull CharSequence chars, @NotNull PsiFile original);
}