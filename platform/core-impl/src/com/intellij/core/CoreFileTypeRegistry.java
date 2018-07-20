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
package com.intellij.core;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author yole
 */
public class CoreFileTypeRegistry extends FileTypeRegistry {
  private final Map<String, FileType> myExtensionsMap = new THashMap<>(FileUtil.PATH_HASHING_STRATEGY);
  private final List<FileType> myAllFileTypes = new ArrayList<>();

  public CoreFileTypeRegistry() {
    myAllFileTypes.add(UnknownFileType.INSTANCE);
  }

  @Override
  public boolean isFileIgnored(@NotNull VirtualFile file) {
    return false;
  }

  @NotNull
  @Override
  public FileType[] getRegisteredFileTypes() {
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
    return getFileTypeByFileName(file.getName());
  }

  @NotNull
  @Override
  public FileType getFileTypeByFileName(@NotNull @NonNls String fileName) {
    final String extension = FileUtilRt.getExtension(fileName);
    return getFileTypeByExtension(extension);
  }

  @Override
  public boolean isFileOfType(@NotNull VirtualFile file, @NotNull FileType type) {
    return getFileTypeByFile(file) == type;
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
