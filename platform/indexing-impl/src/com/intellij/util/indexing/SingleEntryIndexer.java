/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.util.indexing;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;

/**
 * Simplifies API and ensures that data key will always be equal to virtual file id
 *
 * @author Eugene Zhuravlev
 */
public abstract class SingleEntryIndexer<V> implements DataIndexer<Integer, V, FileContent>{
  private final boolean myAcceptNullValues;

  protected SingleEntryIndexer(boolean acceptNullValues) {
    myAcceptNullValues = acceptNullValues;
  }

  @Override
  @NotNull
  public final Map<Integer, V> map(@NotNull FileContent inputData) {
    if (inputData == null) {
      return Collections.emptyMap();
    }
    final V value = computeValue(inputData);
    if (value == null && !myAcceptNullValues) {
      return Collections.emptyMap();
    }
    final int key = Math.abs(FileBasedIndex.getFileId(inputData.getFile()));
    return Collections.singletonMap(key, value);
  }

  protected abstract @Nullable V computeValue(@NotNull FileContent inputData);
}
