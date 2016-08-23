/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
  private final Map<FileType, WordsScanner> myMap = new HashMap<>();

  @Override
  public void registerCacheBuilder(@NotNull FileType fileType, WordsScanner cacheBuilder) {
    myMap.put(fileType, cacheBuilder);
  }

  @Override
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
