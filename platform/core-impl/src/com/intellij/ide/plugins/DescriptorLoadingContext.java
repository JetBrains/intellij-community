// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import gnu.trove.THashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class DescriptorLoadingContext implements AutoCloseable {
  final Map<Path, FileSystem> openedFiles = new THashMap<>();
  final DescriptorListLoadingContext parentContext;
  final boolean isBundled;
  final boolean isEssential;

  private List<AbstractMap.SimpleEntry<String, IdeaPluginDescriptorImpl>> visitedFiles;

  Path lastZipWithDescriptor;

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

  void readDescriptor(@NotNull IdeaPluginDescriptorImpl descriptor,
                      @NotNull Element element,
                      @NotNull Path basePath,
                      @NotNull PathBasedJdomXIncluder.PathResolver<?> resolver) {
    descriptor.readExternal(element, basePath, PluginManagerCore.isUnitTestMode, resolver, this);
  }
}
