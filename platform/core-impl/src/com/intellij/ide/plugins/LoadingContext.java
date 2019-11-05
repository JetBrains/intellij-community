// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.SafeJdomFactory;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.*;

final class LoadingContext implements AutoCloseable {
  final Map<Path, FileSystem> openedFiles = new THashMap<>();
  final LoadDescriptorsContext parentContext;
  final boolean isBundled;
  final boolean isEssential;
  final Set<PluginId> disabledPlugins;

  private List<AbstractMap.SimpleEntry<String, IdeaPluginDescriptorImpl>> visitedFiles;

  Path lastZipWithDescriptor;

  final PathBasedJdomXIncluder.PathResolver<?> pathResolver;

  /**
   * parentContext is null only for CoreApplicationEnvironment - it is not valid otherwise because in this case XML is not interned.
   */
  LoadingContext(@Nullable LoadDescriptorsContext parentContext,
                 boolean isBundled,
                 boolean isEssential,
                 @NotNull Set<PluginId> disabledPlugins,
                 @NotNull PathBasedJdomXIncluder.PathResolver<?> pathResolver) {
    this.parentContext = parentContext;
    this.isBundled = isBundled;
    this.isEssential = isEssential;
    this.disabledPlugins = disabledPlugins;
    this.pathResolver = pathResolver;
  }

  @NotNull
  List<AbstractMap.SimpleEntry<String, IdeaPluginDescriptorImpl>> getVisitedFiles() {
    List<AbstractMap.SimpleEntry<String, IdeaPluginDescriptorImpl>> result = visitedFiles;
    if (result == null) {
      result = new ArrayList<>(3);
      visitedFiles = result;
    }
    return result;
  }

  @NotNull
  FileSystem open(@NotNull Path file) throws IOException {
    FileSystem result = openedFiles.get(file);
    if (result == null) {
      result = FileSystems.newFileSystem(file, null);
      openedFiles.put(file, result);
    }
    return result;
  }

  @Nullable
  SafeJdomFactory getXmlFactory() {
    return parentContext == null ? null : parentContext.getXmlFactory();
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
  public LoadingContext copy(boolean isEssential) {
    return new LoadingContext(parentContext, isBundled, isEssential, disabledPlugins, pathResolver);
  }
}
