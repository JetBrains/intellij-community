// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.cache.impl.id;

import com.intellij.util.indexing.CustomInputMapIndexExtension;
import com.intellij.util.indexing.CustomInputsIndexFileBasedIndexExtension;
import com.intellij.util.indexing.InputMapExternalizer;
import com.intellij.util.io.DataExternalizer;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;

public final class IdIndexImpl extends IdIndex implements CustomInputsIndexFileBasedIndexExtension<IdIndexEntry>,
                                                          CustomInputMapIndexExtension<IdIndexEntry, Integer> {
  @Override
  public @NotNull DataExternalizer<Map<IdIndexEntry, Integer>> createInputMapExternalizer() {
    DataExternalizer<Collection<IdIndexEntry>> keysExternalizer = createExternalizer();
    InputMapExternalizer<IdIndexEntry, Integer> fallbackExternalizer = new InputMapExternalizer<>(
      keysExternalizer,
      getValueExternalizer(),
      /*valueIsAbsent: */ false
    );
    return new IdIndexEntryMapExternalizer(fallbackExternalizer);
  }

  @Override
  public @NotNull DataExternalizer<Collection<IdIndexEntry>> createExternalizer() {
    return new IdIndexEntriesExternalizer();
  }
}
