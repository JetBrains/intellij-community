/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ide.scratch;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.fileTypes.ex.FileTypeIdentifiableByVirtualFile;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author gregsh
 */
public class ScratchFileType extends LanguageFileType implements FileTypeIdentifiableByVirtualFile {

  public static final LanguageFileType INSTANCE = new ScratchFileType();

  ScratchFileType() {
    super(PlainTextLanguage.INSTANCE);
  }

  @Override
  public boolean isMyFileType(@NotNull VirtualFile file) {
    return ScratchFileService.getInstance().getRootType(file) != null;
  }

  @NotNull
  @Override
  public String getName() {
    return "Scratch";
  }

  @NotNull
  @Override
  public String getDescription() {
    return "Scratch";
  }

  @NotNull
  @Override
  public String getDefaultExtension() {
    return "";
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return PlainTextFileType.INSTANCE.getIcon();
  }

  @Override
  public boolean isReadOnly() {
    return true;
  }

  @Nullable
  @Override
  public String getCharset(@NotNull VirtualFile file, @NotNull byte[] content) {
    return null;
  }

  @Nullable
  public static FileType getFileTypeOfScratch(@NotNull Project project, @Nullable VirtualFile file) {
    if (!isScratch(file)) return null;

    Language language = ScratchFileServiceImpl.Substitutor.substituteLanguage(project, file);
    LanguageFileType fileType = language != null ? language.getAssociatedFileType() : null;
    if (fileType != null) return fileType;

    return getFileTypeOfScratchFromFileName(file);
  }

  @Nullable
  public static Language getScratchLanguage(@NotNull Project project, @Nullable VirtualFile file) {
    if (!isScratch(file)) return null;

    Language language = ScratchFileServiceImpl.Substitutor.substituteLanguage(project, file);
    return language != null ? language : getScratchLanguageFromFileName(file);
  }

  @Nullable
  static Language getScratchLanguageFromFileName(@Nullable VirtualFile file) {
    FileType fileType = getFileTypeOfScratchFromFileName(file);
    return fileType instanceof LanguageFileType ? ((LanguageFileType)fileType).getLanguage() : null;
  }

  @Nullable
  private static FileType getFileTypeOfScratchFromFileName(@Nullable VirtualFile file) {
    return isScratch(file) ? FileTypeManager.getInstance().getFileTypeByFileName(file.getName()) : null;
  }

  private static boolean isScratch(@Nullable VirtualFile file) {
    return file != null && file.getFileType() == INSTANCE;
  }
}
