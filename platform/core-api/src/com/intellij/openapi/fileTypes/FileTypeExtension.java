// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.KeyedExtensionCollector;
import com.intellij.util.KeyedLazyInstance;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FileTypeExtension<T> extends KeyedExtensionCollector<T, FileType> {
  public FileTypeExtension(final @NonNls String epName) {
    super(epName);
  }

  public FileTypeExtension(@NotNull ExtensionPointName<KeyedLazyInstance<T>> epName) {
    super(epName);
  }

  @Override
  protected @NotNull String keyToString(final @NotNull FileType key) {
    return key.getName();
  }

  public @NotNull List<T> allForFileType(@NotNull FileType t) {
    return forKey(t);
  }

  public T forFileType(@NotNull FileType t) {
    final List<T> all = allForFileType(t);
    return all.isEmpty() ? null : all.get(0);
  }

  public Map<FileType, T> getAllRegisteredExtensions() {
    List<KeyedLazyInstance<T>> extensions = getExtensions();
    Map<FileType, T> result = new HashMap<>();
    for (KeyedLazyInstance<T> extension : extensions) {
      FileType fileType = FileTypeRegistry.getInstance().findFileTypeByName(extension.getKey());
      if (fileType != null) {
        result.put(fileType, extension.getInstance());
      }
    }
    return result;
  }
}