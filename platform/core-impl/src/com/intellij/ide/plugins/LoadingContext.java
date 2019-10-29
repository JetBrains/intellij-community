// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SafeJdomFactory;
import gnu.trove.THashMap;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

final class LoadingContext implements AutoCloseable {
  final Map<File, ZipFile> openedFiles = new THashMap<>();
  final LoadDescriptorsContext parentContext;
  final boolean isBundled;
  final boolean isEssential;
  final boolean ignoreDisabled;
  final List<Pair<String, IdeaPluginDescriptorImpl>> visitedFiles = new ArrayList<>(3);

  File lastZipWithDescriptor;

  /**
   * parentContext is null only for CoreApplicationEnvironment - it is not valid otherwise because in this case XML is not interned.
   */
  LoadingContext(@Nullable LoadDescriptorsContext parentContext, boolean isBundled, boolean isEssential, boolean ignoreDisabled) {
    this.parentContext = parentContext;
    this.isBundled = isBundled;
    this.isEssential = isEssential;
    this.ignoreDisabled = ignoreDisabled;
  }

  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  ZipFile open(File file) throws IOException {
    ZipFile zipFile = openedFiles.get(file);
    if (zipFile == null) {
      openedFiles.put(file, zipFile = new ZipFile(file));
    }
    return zipFile;
  }

  @Nullable
  SafeJdomFactory getXmlFactory() {
    return parentContext != null ? parentContext.getXmlFactory() : null;
  }

  @Override
  public void close() {
    for (ZipFile file : openedFiles.values()) {
      try { file.close(); }
      catch (IOException ignore) { }
    }
  }
}
