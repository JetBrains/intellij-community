// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.KeyedExtensionCollector;
import com.intellij.util.KeyedLazyInstance;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

  public @NotNull @Unmodifiable List<T> allForFileType(@NotNull FileType t) {
    return forKey(t);
  }

  public @Nullable T forFileType(@NotNull FileType t) {
    final List<T> all = allForFileType(t);
    return all.isEmpty() ? null : all.get(0);
  }

  public @NotNull Map<FileType, T> getAllRegisteredExtensions() {
    return getAllRegisteredExtensionsLazy().entrySet().stream()
      .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getInstance()));
  }

  /**
   * Prefer this over {@link #getAllRegisteredExtensions()} when only the file-type keys are needed,
   * as it avoids eager class loading and service initialization of every registered extension.
   */
  @ApiStatus.Internal
  public @NotNull Set<FileType> getAllRegisteredFileTypes() {
    return getAllRegisteredExtensionsLazy().keySet();
  }

  private Map<FileType, KeyedLazyInstance<T>> getAllRegisteredExtensionsLazy() {
    List<KeyedLazyInstance<T>> extensions = getExtensions();
    Map<FileType, KeyedLazyInstance<T>> result = new HashMap<>();
    for (KeyedLazyInstance<T> extension : extensions) {
      FileType fileType = FileTypeRegistry.getInstance().findFileTypeByName(extension.getKey());
      if (fileType != null) {
        result.put(fileType, extension);
      }
    }
    return result;
  }
}
