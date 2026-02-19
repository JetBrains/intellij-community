// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.core;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithAssignedFileType;
import com.intellij.util.containers.CollectionFactory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public final class CoreFileTypeRegistry extends FileTypeRegistry {
  private final Map<String, FileType> myExtensionsMap = CollectionFactory.createFilePathMap();
  private final List<FileType> myAllFileTypes = new ArrayList<>();

  public CoreFileTypeRegistry() {
    myAllFileTypes.add(UnknownFileType.INSTANCE);
  }

  @Override
  public boolean isFileIgnored(@NotNull VirtualFile file) {
    return false;
  }

  @Override
  public FileType @NotNull [] getRegisteredFileTypes() {
    return myAllFileTypes.toArray(FileType.EMPTY_ARRAY);
  }

  @Override
  public @NotNull FileType getFileTypeByFile(@NotNull VirtualFile file) {
    if (file instanceof VirtualFileWithAssignedFileType) {
      FileType fileType = ((VirtualFileWithAssignedFileType)file).getAssignedFileType();
      if (fileType != null) {
        return fileType;
      }
    }
    return getFileTypeByFileName(file.getNameSequence());
  }

  @Override
  public @NotNull FileType getFileTypeByFileName(@NotNull @NonNls String fileName) {
    return getFileTypeByExtension(FileUtilRt.getExtension(fileName));
  }

  @Override
  public @NotNull FileType getFileTypeByExtension(@NonNls @NotNull String extension) {
    final FileType result = myExtensionsMap.get(extension);
    return result == null ? UnknownFileType.INSTANCE : result;
  }

  public void registerFileType(@NotNull FileType fileType, @NotNull @NonNls String extension) {
    myAllFileTypes.add(fileType);
    for (final String ext : extension.split(";")) {
      myExtensionsMap.put(ext, fileType);
    }
  }

  @Override
  public @Nullable FileType findFileTypeByName(@NotNull String fileTypeName) {
    for (FileType type : myAllFileTypes) {
      if (type.getName().equals(fileTypeName)) {
        return type;
      }
    }
    return null;
  }
}
