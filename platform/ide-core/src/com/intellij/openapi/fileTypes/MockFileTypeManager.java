// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MockFileTypeManager extends FileTypeManager {
  @Override
  public @NotNull FileType getFileTypeByFileName(@NotNull String fileName) {
    return MockLanguageFileType.INSTANCE;
  }

  @Override
  public @NotNull FileType getFileTypeByFile(@NotNull VirtualFile file) {
    return MockLanguageFileType.INSTANCE;
  }

  @Override
  public @NotNull FileType getFileTypeByExtension(@NotNull String extension) {
    return MockLanguageFileType.INSTANCE;
  }

  @Override
  public FileType @NotNull [] getRegisteredFileTypes() {
    return new FileType[] {MockLanguageFileType.INSTANCE};
  }

  @Override
  @SuppressWarnings("removal")
  public void registerFileType(@NotNull FileType type, String @Nullable ... defaultAssociatedExtensions) { }

  @Override
  public boolean isFileIgnored(@NotNull String name) {
    return false;
  }

  @Override
  public boolean isFileIgnored(@NotNull VirtualFile file) {
    return false;
  }

  @Override
  @SuppressWarnings("removal")
  public String @NotNull [] getAssociatedExtensions(@NotNull FileType type) {
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  @Override
  public @NotNull List<FileNameMatcher> getAssociations(@NotNull FileType type) {
    return List.of();
  }

  @Override
  public FileType getKnownFileTypeOrAssociate(@NotNull VirtualFile file, @NotNull Project project) {
    return file.getFileType();
  }

  @Override
  public @NotNull String getIgnoredFilesList() {
    return "";
  }

  @Override
  public void setIgnoredFilesList(@NotNull String list) { }

  @Override
  public void associate(@NotNull FileType type, @NotNull FileNameMatcher matcher) { }

  @Override
  public void removeAssociation(@NotNull FileType type, @NotNull FileNameMatcher matcher) { }

  @Override
  public @NotNull FileType getStdFileType(@NotNull String fileTypeName) {
    return MockLanguageFileType.INSTANCE;
  }

  @Override
  public @Nullable FileType findFileTypeByName(@NotNull String fileTypeName) {
    return null;
  }
}
