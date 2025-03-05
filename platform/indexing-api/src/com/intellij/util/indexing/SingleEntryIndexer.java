// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.util.indexing;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;

/**
 * Simplifies API and ensures that data key will always be equal to virtual file id
 *
 * @author Eugene Zhuravlev
 */
@ApiStatus.OverrideOnly
public abstract class SingleEntryIndexer<V> implements DataIndexer<Integer, V, FileContent>{
  private final boolean myAcceptNullValues;

  protected SingleEntryIndexer(boolean acceptNullValues) {
    myAcceptNullValues = acceptNullValues;
  }

  @Override
  public final @NotNull Map<Integer, V> map(@NotNull FileContent inputData) {
    final V value = computeValue(inputData);
    if (value == null && !myAcceptNullValues) {
      return Collections.emptyMap();
    }
    VirtualFile file = inputData.getFile();
    int key = file instanceof LightVirtualFile ? -1 : Math.abs(FileBasedIndex.getFileId(file));
    return Collections.singletonMap(key, value);
  }

  protected abstract @Nullable V computeValue(@NotNull FileContent inputData);

  @ApiStatus.Internal
  public boolean isAcceptNullValues() {
    return myAcceptNullValues;
  }
}
