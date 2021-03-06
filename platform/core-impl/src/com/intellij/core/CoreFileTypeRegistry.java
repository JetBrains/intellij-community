// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.core;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.containers.CollectionFactory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author yole
 */
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

  @NotNull
  @Override
  public FileType getFileTypeByFile(@NotNull VirtualFile file) {
    if (file instanceof LightVirtualFile) {
      FileType fileType = ((LightVirtualFile)file).getAssignedFileType();
      if (fileType != null) {
        return fileType;
      }
    }
    return getFileTypeByFileName(file.getNameSequence());
  }

  @NotNull
  @Override
  public FileType getFileTypeByFileName(@NotNull @NonNls String fileName) {
    return getFileTypeByExtension(FileUtilRt.getExtension(fileName));
  }

  @NotNull
  @Override
  public FileType getFileTypeByExtension(@NonNls @NotNull String extension) {
    final FileType result = myExtensionsMap.get(extension);
    return result == null ? UnknownFileType.INSTANCE : result;
  }

  public void registerFileType(@NotNull FileType fileType, @NotNull @NonNls String extension) {
    myAllFileTypes.add(fileType);
    for (final String ext : extension.split(";")) {
      myExtensionsMap.put(ext, fileType);
    }
  }

  @Nullable
  @Override
  public FileType findFileTypeByName(@NotNull String fileTypeName) {
    for (FileType type : myAllFileTypes) {
      if (type.getName().equals(fileTypeName)) {
        return type;
      }
    }
    return null;
  }
}
