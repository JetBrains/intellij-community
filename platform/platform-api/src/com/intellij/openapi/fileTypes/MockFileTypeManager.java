// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class MockFileTypeManager extends FileTypeManager {
  @Override
  public void registerFileType(@NotNull FileType type, @NotNull List<? extends FileNameMatcher> defaultAssociations) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public FileType getFileTypeByFileName(@NotNull @NonNls String fileName) {
    return MockLanguageFileType.INSTANCE;
  }

  @NotNull
  @Override
  public FileType getFileTypeByFile(@NotNull VirtualFile file) {
    return MockLanguageFileType.INSTANCE;
  }

  @NotNull
  @Override
  public FileType getFileTypeByExtension(@NonNls @NotNull String extension) {
    return MockLanguageFileType.INSTANCE;
  }

  @Override
  public FileType @NotNull [] getRegisteredFileTypes() {
    return new FileType[] {MockLanguageFileType.INSTANCE};
  }

  @Override
  public boolean isFileIgnored(@NonNls @NotNull String name) {
    return false;
  }

  @Override
  public boolean isFileIgnored(@NotNull VirtualFile file) {
    return false;
  }

  @Override
  public String @NotNull [] getAssociatedExtensions(@NotNull FileType type) {
    return ArrayUtilRt.EMPTY_STRING_ARRAY;
  }

  @NotNull
  @Override
  public List<FileNameMatcher> getAssociations(@NotNull FileType type) {
    return Collections.emptyList();
  }

  @Override
  public void addFileTypeListener(@NotNull FileTypeListener listener) {
  }

  @Override
  public void removeFileTypeListener(@NotNull FileTypeListener listener) {
  }

  @Override
  public FileType getKnownFileTypeOrAssociate(@NotNull VirtualFile file, @NotNull Project project) {
    return file.getFileType();
  }

  @NotNull
  @Override
  public String getIgnoredFilesList() {
    return "";
  }

  @Override
  public void setIgnoredFilesList(@NotNull String list) {
  }

  @Override
  public void associate(@NotNull FileType type, @NotNull FileNameMatcher matcher) {
  }

  @Override
  public void removeAssociation(@NotNull FileType type, @NotNull FileNameMatcher matcher) {
  }

  @NotNull
  @Override
  public FileType getStdFileType(@NotNull @NonNls String fileTypeName) {
    return MockLanguageFileType.INSTANCE;
  }

  @Nullable
  @Override
  public FileType findFileTypeByName(@NotNull String fileTypeName) {
    return null;
  }
}
