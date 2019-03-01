// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.snapshot;

import com.intellij.util.indexing.FileBasedIndexExtension;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.indexing.PsiDependentIndex;
import com.intellij.util.indexing.impl.AbstractMapProviderForwardIndexAccessor;
import com.intellij.util.io.EnumeratorIntegerDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Map;

public class ContentHashForwardIndexAccessor<Key, Value> extends AbstractMapProviderForwardIndexAccessor<Key, Value, Integer, FileContent> {
  @NotNull
  private final FileContentHasher myHasher;
  @NotNull
  private final UpdatableSnapshotIndex<Key, Value> mySnapshotInputMappings;

  public ContentHashForwardIndexAccessor(@NotNull FileBasedIndexExtension<Key, Value> extension) throws IOException {
    super(EnumeratorIntegerDescriptor.INSTANCE);
    myHasher = new Sha1FileContentHasher(extension instanceof PsiDependentIndex);
    mySnapshotInputMappings = new SnapshotInputMappings<>(extension);
  }

  @Nullable
  @Override
  protected Integer convertToDataType(@Nullable Map<Key, Value> map, @Nullable FileContent input) throws IOException {
    return input == null ? null : myHasher.getEnumeratedHash(input);
  }

  @Nullable
  @Override
  public Map<Key, Value> getMapFromData(@Nullable Integer hashId) throws IOException {
    return hashId == null ? null : mySnapshotInputMappings.readSnapshot(hashId);
  }

  @NotNull
  public UpdatableSnapshotIndex<Key, Value> getSnapshotInputMappings() {
    return mySnapshotInputMappings;
  }
}
