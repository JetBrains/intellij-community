// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.find.ngrams;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.util.ThreadLocalCachedIntArray;
import com.intellij.openapi.util.text.TrigramBuilder;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.EnumeratorIntegerDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.*;

/**
 * Implementation of <a href="https://en.wikipedia.org/wiki/Trigram">trigram index</a> for fast text search.
 *
 * Should not be used directly, please consider {@link com.intellij.find.TextSearchService}
 */
public final class TrigramIndex extends ScalarIndexExtension<Integer> implements CustomInputsIndexFileBasedIndexExtension<Integer> {
  public static final ID<Integer,Void> INDEX_ID = ID.create("Trigram.Index");

  @ApiStatus.Internal
  public static boolean isEnabled() {
    return TrigramTextSearchService.useIndexingSearchExtensions();
  }

  @ApiStatus.Internal
  public static boolean isIndexable(FileType fileType) {
    return !fileType.isBinary() &&
           isEnabled() &&
           (!FileBasedIndex.IGNORE_PLAIN_TEXT_FILES || fileType != PlainTextFileType.INSTANCE);
  }

  @NotNull
  @Override
  public ID<Integer, Void> getName() {
    return INDEX_ID;
  }

  @NotNull
  @Override
  public DataIndexer<Integer, Void, FileContent> getIndexer() {
    return new DataIndexer<>() {
      @Override
      @NotNull
      public Map<Integer, Void> map(@NotNull FileContent inputData) {
        return TrigramBuilder.getTrigramsAsMap(inputData.getContentAsText());
      }
    };
  }

  @NotNull
  @Override
  public KeyDescriptor<Integer> getKeyDescriptor() {
    return EnumeratorIntegerDescriptor.INSTANCE;
  }

  @NotNull
  @Override
  public FileBasedIndex.InputFilter getInputFilter() {
    return file -> isIndexable(file.getFileType());
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  @Override
  public int getVersion() {
    return 3;
  }

  @Override
  public boolean hasSnapshotMapping() {
    return true;
  }

  @Override
  public boolean needsForwardIndexWhenSharing() {
    return false;
  }

  private static final ThreadLocalCachedIntArray SPARE_BUFFER_LOCAL = new ThreadLocalCachedIntArray();

  @NotNull
  @Override
  public DataExternalizer<Collection<Integer>> createExternalizer() {
    return new DataExternalizer<>() {
      @Override
      public void save(@NotNull DataOutput out, @NotNull Collection<Integer> value) throws IOException {
        final int numberOfValues = value.size();

        int[] buffer = SPARE_BUFFER_LOCAL.getBuffer(numberOfValues);
        int ptr = 0;
        for (Integer i : value) {
          buffer[ptr++] = i;
        }
        Arrays.sort(buffer, 0, numberOfValues);

        DataInputOutputUtil.writeINT(out, numberOfValues);
        int prev = 0;
        for (ptr = 0; ptr < numberOfValues; ++ptr) {
          DataInputOutputUtil.writeLONG(out, (long)buffer[ptr] - prev);
          prev = buffer[ptr];
        }
      }

      @NotNull
      @Override
      public Collection<Integer> read(@NotNull DataInput in) throws IOException {
        int size = DataInputOutputUtil.readINT(in);
        List<Integer> result = new ArrayList<>(size);
        int prev = 0;
        while (size-- > 0) {
          int l = (int)(DataInputOutputUtil.readLONG(in) + prev);
          result.add(l);
          prev = l;
        }
        return result;
      }
    };
  }
}
