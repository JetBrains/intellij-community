// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

final class DescriptorLoadingContext implements AutoCloseable {
  private @Nullable Map<Path, FileSystem> openedFiles;
  final DescriptorListLoadingContext parentContext;
  final boolean isBundled;
  final boolean isEssential;

  final PathBasedJdomXIncluder.PathResolver pathResolver;

  /**
   * parentContext is null only for CoreApplicationEnvironment - it is not valid otherwise because in this case XML is not interned.
   */
  DescriptorLoadingContext(@NotNull DescriptorListLoadingContext parentContext,
                           boolean isBundled,
                           boolean isEssential,
                           @NotNull PathBasedJdomXIncluder.PathResolver pathResolver) {
    this.parentContext = parentContext;
    this.isBundled = isBundled;
    this.isEssential = isEssential;
    this.pathResolver = pathResolver;
  }

  @NotNull FileSystem open(@NotNull Path file) {
    if (openedFiles == null) {
      openedFiles = new HashMap<>();
    }
    return openedFiles.computeIfAbsent(file, it -> {
      try {
        //noinspection SpellCheckingInspection
        return parentContext.getZipFsProvider().newFileSystem(it, Collections.singletonMap("zipinfo-time", "false"));
      }
      catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    });
  }

  @Override
  public void close() {
    if (openedFiles != null) {
      for (FileSystem file : openedFiles.values()) {
        try {
          file.close();
        }
        catch (IOException ignore) {
        }
      }
    }
  }

  public @NotNull DescriptorLoadingContext copy(boolean isEssential) {
    return new DescriptorLoadingContext(parentContext, isBundled, isEssential, pathResolver);
  }
}
