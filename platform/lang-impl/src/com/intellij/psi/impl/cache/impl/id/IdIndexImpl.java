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
package com.intellij.psi.impl.cache.impl.id;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.ThreadLocalCachedIntArray;
import com.intellij.util.indexing.CustomInputsIndexFileBasedIndexExtension;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;

public class IdIndexImpl extends IdIndex implements CustomInputsIndexFileBasedIndexExtension<IdIndexEntry> {
  private static final ThreadLocalCachedIntArray spareBufferLocal = new ThreadLocalCachedIntArray();
  private final FileTypeRegistry myFileTypeManager;

  public IdIndexImpl(FileTypeRegistry manager) {
    myFileTypeManager = manager;
  }

  @Override
  public int getVersion() {
    FileType[] types = myFileTypeManager.getRegisteredFileTypes();
    Arrays.sort(types, (o1, o2) -> Comparing.compare(o1.getName(), o2.getName()));

    int version = super.getVersion();
    for(FileType fileType:types) {
      if (!isIndexable(fileType)) continue;
      FileTypeIdIndexer indexer = IdTableBuilding.getFileTypeIndexer(fileType);
      if (indexer == null) continue;
      version = version * 31 + (indexer.getVersion() ^ indexer.getClass().getName().hashCode());
    }
    return version;
  }

  @NotNull
  @Override
  public DataExternalizer<Collection<IdIndexEntry>> createExternalizer() {
    return new DataExternalizer<Collection<IdIndexEntry>>() {
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
    };
  }
}
