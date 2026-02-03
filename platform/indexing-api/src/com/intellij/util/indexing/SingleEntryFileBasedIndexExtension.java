// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.util.indexing;

import com.intellij.util.io.EnumeratorIntegerDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Base implementation for <a href="https://en.wikipedia.org/wiki/Search_engine_indexing#The_forward_index">forward indices</a>
 * that produce single value per single file.
 * <p>
 * Can be used to cache heavy computable file's data while the IDE is indexing.
 */
@ApiStatus.OverrideOnly
public abstract class SingleEntryFileBasedIndexExtension<V> extends FileBasedIndexExtension<Integer, V>{
  @Override
  public final @NotNull KeyDescriptor<Integer> getKeyDescriptor() {
    return EnumeratorIntegerDescriptor.INSTANCE;
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  @Override
  public int getCacheSize() {
    return 5;
  }

  @Override
  public abstract @NotNull SingleEntryIndexer<V> getIndexer();

  @Override
  public boolean keyIsUniqueForIndexedFile() {
    return true;
  }
}
