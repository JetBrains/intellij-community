/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.indexing;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.LanguageSubstitutors;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author traff
 */
public class SubstitutedFileType extends LanguageFileType{
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
  public static FileType substituteFileType(@NotNull VirtualFile file, @NotNull FileType fileType, Project project) {
    if (project == null) {
      return fileType;
    }
    if (fileType instanceof LanguageFileType) {
      final Language language = ((LanguageFileType)fileType).getLanguage();
      final Language substitutedLanguage = LanguageSubstitutors.INSTANCE.substituteLanguage(language, file, project);
      LanguageFileType substFileType = substitutedLanguage.getAssociatedFileType();
      if (!substitutedLanguage.equals(language) && substFileType != null) {
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
  public String getCharset(@NotNull VirtualFile file, @NotNull byte[] content) {
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
