/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.lang.cacheBuilder;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * @author yole
 */
public class CacheBuilderRegistryImpl extends CacheBuilderRegistry {
  private final Map<FileType, WordsScanner> myMap = new HashMap<FileType, WordsScanner>();

  public void registerCacheBuilder(@NotNull FileType fileType, WordsScanner cacheBuilder) {
    myMap.put(fileType, cacheBuilder);
  }

  @Nullable
  public WordsScanner getCacheBuilder(@NotNull FileType fileType) {
    final WordsScanner scanner = myMap.get(fileType);
    if (scanner != null) {
      return scanner;
    }
    for(CacheBuilderEP ep: Extensions.getExtensions(CacheBuilderEP.EP_NAME)) {
      if (ep.getFileType().equals(fileType.getName())) {
        return ep.getWordsScanner();
      }
    }
    return null;
  }
}
