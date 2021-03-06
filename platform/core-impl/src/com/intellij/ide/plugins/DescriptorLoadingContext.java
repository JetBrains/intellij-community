// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

final class DescriptorLoadingContext implements AutoCloseable {
  @SuppressWarnings("SpellCheckingInspection")
  private static final Map<String, String> ZIP_OPTIONS = Collections.singletonMap("zipinfo-time", "false");

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
    return openedFiles.computeIfAbsent(file, it -> {
      try {
        return parentContext.zipFsProvider.newFileSystem(it, ZIP_OPTIONS);
      }
      catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    });
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

  public @NotNull DescriptorLoadingContext copy(boolean isEssential) {
    return new DescriptorLoadingContext(parentContext, isBundled, isEssential, pathResolver);
  }
}
