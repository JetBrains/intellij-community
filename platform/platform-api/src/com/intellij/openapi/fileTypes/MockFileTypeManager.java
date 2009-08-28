/*
 * @author max
 */
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

class MockFileTypeManager extends FileTypeManager {
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

  @NotNull
  @Override
  public String[] getAssociatedExtensions(@NotNull FileType type) {
    return new String[0];
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
  public FileType getKnownFileTypeOrAssociate(@NotNull VirtualFile file) {
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
}
