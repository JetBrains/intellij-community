// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.cacheBuilder;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.FileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class CacheBuilderRegistryImpl extends CacheBuilderRegistry {
  private static final ExtensionPointName<CacheBuilderEP> EP_NAME = new ExtensionPointName<>("com.intellij.cacheBuilder");

  @Override
  @Nullable
  public WordsScanner getCacheBuilder(@NotNull FileType fileType) {
    for (CacheBuilderEP ep: EP_NAME.getExtensionList()) {
      if (ep.getFileType().equals(fileType.getName())) {
        return ep.getWordsScanner();
      }
    }
    return null;
  }
}
