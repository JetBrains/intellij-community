// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.LanguageSubstitutors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class SubstitutedFileType extends LanguageFileType{
  @NotNull private final FileType myOriginalFileType;
  @NotNull private final FileType myFileType;

  private SubstitutedFileType(@NotNull FileType originalFileType,
                              @NotNull LanguageFileType substitutionFileType,
                              @NotNull Language substitutedLanguage) {
    super(substitutedLanguage);
    myOriginalFileType = originalFileType;
    myFileType = substitutionFileType;
  }

  @NotNull
  public static FileType substituteFileType(@NotNull VirtualFile file, @NotNull FileType fileType, @Nullable Project project) {
    if (project == null) {
      return fileType;
    }
    if (fileType instanceof LanguageFileType) {
      final Language language = ((LanguageFileType)fileType).getLanguage();
      final Language substitutedLanguage = LanguageSubstitutors.getInstance().substituteLanguage(language, file, project);
      LanguageFileType substFileType;
      if (!substitutedLanguage.equals(language) && (substFileType = substitutedLanguage.getAssociatedFileType()) != null) {
        return new SubstitutedFileType(fileType, substFileType, substitutedLanguage);
      }
    }

    return fileType;
  }

  @NotNull
  @Override
  public String getName() {
    return myFileType.getName();
  }

  @NotNull
  @Override
  public String getDescription() {
    return myFileType.getDescription();
  }

  @NotNull
  @Override
  public String getDefaultExtension() {
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

  @NotNull
  public FileType getOriginalFileType() {
    return myOriginalFileType;
  }

  @NotNull
  public FileType getFileType() {
    return myFileType;
  }

  public boolean isSameFileType() {
    return myFileType.equals(myOriginalFileType);
  }

  @Override
  public String toString() {
    return "SubstitutedFileType: original="+myOriginalFileType+"; substituted="+myFileType;
  }
}
