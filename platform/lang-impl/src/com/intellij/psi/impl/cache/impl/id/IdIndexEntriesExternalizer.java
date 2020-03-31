// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.cache.impl.id;

import com.intellij.openapi.util.ThreadLocalCachedIntArray;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

class IdIndexEntriesExternalizer implements DataExternalizer<Collection<IdIndexEntry>> {
  private static final ThreadLocalCachedIntArray spareBufferLocal = new ThreadLocalCachedIntArray();

  @Override
  public void save(@NotNull DataOutput out, @NotNull Collection<IdIndexEntry> value) throws IOException {
    int size = value.size();
    final int[] values = spareBufferLocal.getBuffer(size);
    int ptr = 0;
    for(IdIndexEntry ie:value) {
      values[ptr++] = ie.getWordHashCode();
    }
    Arrays.sort(values, 0, size);
    DataInputOutputUtil.writeINT(out, size);
    int prev = 0;
    for(int i = 0; i < size; ++i) {
      DataInputOutputUtil.writeLONG(out, (long)values[i] - prev);
      prev = values[i];
    }
  }

  @Override
  public Collection<IdIndexEntry> read(@NotNull DataInput in) throws IOException {
    int length = DataInputOutputUtil.readINT(in);
    ArrayList<IdIndexEntry> entries = new ArrayList<>(length);
    int prev = 0;
    while(length-- > 0) {
      final int l = (int)(DataInputOutputUtil.readLONG(in) + prev);
      entries.add(new IdIndexEntry(l));
      prev = l;
    }
    return entries;
  }
}
