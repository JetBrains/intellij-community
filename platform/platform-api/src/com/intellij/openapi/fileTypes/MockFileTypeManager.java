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

/*
 * @author max
 */
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class MockFileTypeManager extends FileTypeManager {
  @Override
  public void registerFileType(@NotNull FileType type, @NotNull List<FileNameMatcher> defaultAssociations) {
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

  @NotNull
  @Override
  public FileType[] getRegisteredFileTypes() {
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

  @NotNull
  @Override
  public String[] getAssociatedExtensions(@NotNull FileType type) {
    return ArrayUtil.EMPTY_STRING_ARRAY;
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

  @Override
  public boolean isFileOfType(@NotNull VirtualFile file, @NotNull FileType type) {
    return false;
  }

  @Nullable
  @Override
  public FileType findFileTypeByName(@NotNull String fileTypeName) {
    return null;
  }
}
