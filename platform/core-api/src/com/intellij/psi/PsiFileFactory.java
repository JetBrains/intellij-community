// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class PsiFileFactory {
  public static final Key<PsiFile> ORIGINAL_FILE = Key.create("ORIGINAL_FILE");

  public static PsiFileFactory getInstance(Project project) {
    return project.getService(PsiFileFactory.class);
  }

  /**
   * Creates a file from the specified text.
   *
   * @param name the name of the file to create (the extension of the name determines the file type).
   * @param text the text of the file to create.
   * @return the created file.
   * @throws IncorrectOperationException if the file type with specified extension is binary.
   * @deprecated Please use {@link #createFileFromText(String, FileType, CharSequence)} instead,
   * since file type detecting by file extension becomes vulnerable when file type mappings are changed.
   */
  @Deprecated
  @NotNull
  public abstract PsiFile createFileFromText(@NotNull @NonNls String name, @NotNull @NonNls String text) throws IncorrectOperationException;

  @NotNull
  public abstract PsiFile createFileFromText(@NonNls @NotNull String fileName, @NotNull FileType fileType, @NotNull @NonNls CharSequence text) throws IncorrectOperationException;

  @NotNull
  public abstract PsiFile createFileFromText(@NonNls @NotNull String name, @NotNull FileType fileType, @NotNull @NonNls CharSequence text,
                                             long modificationStamp, boolean eventSystemEnabled) throws IncorrectOperationException;

  @NotNull
  public abstract PsiFile createFileFromText(@NonNls @NotNull String name, @NotNull FileType fileType, @NotNull @NonNls CharSequence text,
                                             long modificationStamp, boolean eventSystemEnabled, boolean markAsCopy) throws IncorrectOperationException;

  public abstract PsiFile createFileFromText(@NonNls @NotNull String name, @NotNull Language language, @NotNull @NonNls CharSequence text) throws IncorrectOperationException;

  public PsiFile createFileFromText(@NotNull Language language, @NotNull @NonNls CharSequence text) throws IncorrectOperationException {
    return createFileFromText("foo.bar", language, text);
  }

  public abstract PsiFile createFileFromText(@NonNls @NotNull String name, @NotNull Language language, @NotNull @NonNls CharSequence text,
                                             boolean eventSystemEnabled, boolean markAsCopy) throws IncorrectOperationException;

  public abstract PsiFile createFileFromText(@NonNls @NotNull String name, @NotNull Language language, @NotNull @NonNls CharSequence text,
                                             boolean eventSystemEnabled, boolean markAsCopy, boolean noSizeLimit) throws IncorrectOperationException;

  public abstract PsiFile createFileFromText(@NonNls @NotNull String name, @NotNull Language language, @NotNull @NonNls CharSequence text,
                                             boolean eventSystemEnabled, boolean markAsCopy, boolean noSizeLimit,
                                             @Nullable VirtualFile original) throws IncorrectOperationException;

  public abstract PsiFile createFileFromText(FileType fileType, @NonNls String fileName, @NonNls CharSequence chars, int startOffset, int endOffset) throws IncorrectOperationException;

  @Nullable
  public abstract PsiFile createFileFromText(@NotNull @NonNls CharSequence chars, @NotNull PsiFile original) throws IncorrectOperationException;
}