/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.LanguageSubstitutors;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author traff
 */
public class SubstitutedFileType extends LanguageFileType{
  private final FileType originalFileType;
  private final FileType fileType;

  private SubstitutedFileType(FileType originalFileType, LanguageFileType substitutionFileType) {
    super(substitutionFileType.getLanguage());
    this.originalFileType = originalFileType;
    this.fileType = substitutionFileType;
  }


  public static FileType substituteFileType(VirtualFile file, FileType fileType, Project project) {
    if (project == null) {
      project = ProjectManager.getInstance().getDefaultProject();
    }
    if (fileType instanceof LanguageFileType) {
      final Language language = ((LanguageFileType)fileType).getLanguage();
      final Language substitutedLanguage = LanguageSubstitutors.INSTANCE.substituteLanguage(language, file, project);
      if (substitutedLanguage != null && !substitutedLanguage.equals(language)) {
        return new SubstitutedFileType(fileType, substitutedLanguage.getAssociatedFileType());
      }
    }

    return fileType;
  }

  @NotNull
  @Override
  public String getName() {
    return fileType.getName();
  }

  @NotNull
  @Override
  public String getDescription() {
    return fileType.getDescription();
  }

  @NotNull
  @Override
  public String getDefaultExtension() {
    return fileType.getDefaultExtension();
  }

  @Override
  public Icon getIcon() {
    return fileType.getIcon();
  }

  @Override
  public String getCharset(@NotNull VirtualFile file, byte[] content) {
    return fileType.getCharset(file, content);
  }

  public FileType getOriginalFileType() {
    return originalFileType;
  }

  public FileType getFileType() {
    return fileType;
  }
}
