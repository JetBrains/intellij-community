// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class SubstitutedFileType extends LanguageFileType {
  private final @NotNull FileType myOriginalFileType;
  private final @NotNull FileType myFileType;

  private SubstitutedFileType(@NotNull FileType originalFileType,
                              @NotNull LanguageFileType substitutionFileType,
                              @NotNull Language substitutedLanguage) {
    super(substitutedLanguage);
    myOriginalFileType = originalFileType;
    myFileType = substitutionFileType;
  }

  public static @NotNull FileType substituteFileType(@NotNull VirtualFile file, @NotNull FileType fileType, @Nullable Project project) {
    if (project == null) {
      return fileType;
    }
    if (fileType instanceof LanguageFileType) {
      Language substLang = LanguageUtil.getLanguageForPsi(project, file, fileType);
      LanguageFileType substFileType = substLang != null && substLang != ((LanguageFileType)fileType).getLanguage() ?
                                       substLang.getAssociatedFileType() : null;
      if (substFileType != null) {
        return new SubstitutedFileType(fileType, substFileType, substLang);
      }
    }

    return fileType;
  }

  @Override
  public @NotNull String getName() {
    return myFileType.getName();
  }

  @Override
  public @NotNull String getDescription() {
    return myFileType.getDescription();
  }

  @Override
  public @NotNull String getDefaultExtension() {
    return myFileType.getDefaultExtension();
  }

  @Override
  public Icon getIcon() {
    return myFileType.getIcon();
  }

  @Override
  public String getCharset(@NotNull VirtualFile file, byte @NotNull [] content) {
    return myFileType.getCharset(file, content);
  }

  public @NotNull FileType getOriginalFileType() {
    return myOriginalFileType;
  }

  public @NotNull FileType getFileType() {
    return myFileType;
  }

  public boolean isSameFileType() {
    return myFileType.equals(myOriginalFileType);
  }

  @Override
  public String toString() {
    return "SubstitutedFileType: original="+myOriginalFileType+"; substituted="+myFileType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    SubstitutedFileType type = (SubstitutedFileType)o;

    if (!myOriginalFileType.equals(type.myOriginalFileType)) return false;
    if (!myFileType.equals(type.myFileType)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myOriginalFileType.hashCode();
    result = 31 * result + myFileType.hashCode();
    return result;
  }
}
