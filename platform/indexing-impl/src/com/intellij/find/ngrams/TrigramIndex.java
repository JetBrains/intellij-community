// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.ngrams;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ThreadLocalCachedIntArray;
import com.intellij.openapi.util.text.TrigramBuilder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.indexing.*;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.EnumeratorIntegerDescriptor;
import com.intellij.util.io.KeyDescriptor;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.*;

/**
 * Implementation of <a href="https://en.wikipedia.org/wiki/Trigram">trigram index</a> for fast text search.
 * <p>
 * Should not be used directly, please consider {@link com.intellij.find.TextSearchService}
 */
public final class TrigramIndex extends ScalarIndexExtension<Integer> implements CustomInputsIndexFileBasedIndexExtension<Integer> {
  public static final ID<Integer, Void> INDEX_ID = ID.create("Trigram.Index");

  @Internal
  public TrigramIndex() {
  }

  @Internal
  public static boolean isEnabled() {
    return TrigramTextSearchService.useIndexingSearchExtensions();
  }

  @Override
  public int getCacheSize() {
    return 64 * super.getCacheSize();
  }

  @Internal
  public static boolean isIndexable(@NotNull VirtualFile file, @NotNull Project project) {
    IndexedFileImpl indexedFile = new IndexedFileImpl(file, project);
    TrigramIndexFilter trigramIndexFilter = ApplicationManager.getApplication().getService(TrigramIndexFilter.class);
    return trigramIndexFilter.acceptInput(indexedFile);
  }

  @Override
  public @NotNull ID<Integer, Void> getName() {
    return INDEX_ID;
  }

  @Override
  public @NotNull DataIndexer<Integer, Void, FileContent> getIndexer() {
    return new DataIndexer<>() {
      @Override
      public @NotNull Map<Integer, Void> map(@NotNull FileContent inputData) {
        return TrigramBuilder.getTrigramsAsMap(inputData.getContentAsText());
      }
    };
  }

  @Override
  public @NotNull KeyDescriptor<Integer> getKeyDescriptor() {
    return EnumeratorIntegerDescriptor.INSTANCE;
  }

  @Override
  public @NotNull FileBasedIndex.InputFilter getInputFilter() {
    return ApplicationManager.getApplication().getService(TrigramIndexFilter.class);
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  @Override
  public int getVersion() {
    return 4;
  }

  @Override
  public boolean needsForwardIndexWhenSharing() {
    return false;
  }

  private static final ThreadLocalCachedIntArray SPARE_BUFFER_LOCAL = new ThreadLocalCachedIntArray();

  @Override
  @Internal
  public @NotNull DataExternalizer<Collection<Integer>> createExternalizer() {
    return new DataExternalizer<>() {
      @Override
      public void save(@NotNull DataOutput out, @NotNull Collection<Integer> value) throws IOException {
        int numberOfValues = value.size();

        int[] buffer = SPARE_BUFFER_LOCAL.getBuffer(numberOfValues);
        int ptr = 0;
        if (value instanceof IntCollection intCollection) {
          buffer = intCollection.toArray(buffer);
        }
        else {
          for (Integer i : value) {
            buffer[ptr++] = i;
          }
        }
        Arrays.sort(buffer, 0, numberOfValues);

        DataInputOutputUtil.writeINT(out, numberOfValues);
        int prev = 0;
        for (ptr = 0; ptr < numberOfValues; ++ptr) {
          int cur = buffer[ptr];
          DataInputOutputUtil.writeLONG(out, (long)cur - prev);
          prev = cur;
        }
      }

      @Override
      public @NotNull Collection<Integer> read(@NotNull DataInput in) throws IOException {
        int size = DataInputOutputUtil.readINT(in);
        IntList result = new IntArrayList(size);
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
