// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

final class DescriptorLoadingContext implements AutoCloseable {
  private final Map<Path, FileSystem> openedFiles = new HashMap<>();
  final DescriptorListLoadingContext parentContext;
  final boolean isBundled;
  final boolean isEssential;

  final PathBasedJdomXIncluder.PathResolver<?> pathResolver;

  /**
   * parentContext is null only for CoreApplicationEnvironment - it is not valid otherwise because in this case XML is not interned.
   */
  DescriptorLoadingContext(@NotNull DescriptorListLoadingContext parentContext,
                           boolean isBundled,
                           boolean isEssential,
                           @NotNull PathBasedJdomXIncluder.PathResolver<?> pathResolver) {
    this.parentContext = parentContext;
    this.isBundled = isBundled;
    this.isEssential = isEssential;
    this.pathResolver = pathResolver;
  }

  @NotNull FileSystem open(@NotNull Path file) throws IOException {
    FileSystem result = openedFiles.get(file);
    if (result == null) {
      result = FileSystems.newFileSystem(file, null);
      openedFiles.put(file, result);
    }
    return result;
  }

  @Override
  public void close() {
    for (FileSystem file : openedFiles.values()) {
      try {
        file.close();
      }
      catch (IOException ignore) {
      }
    }
  }

  @NotNull
  public DescriptorLoadingContext copy(boolean isEssential) {
    return new DescriptorLoadingContext(parentContext, isBundled, isEssential, pathResolver);
  }
}
