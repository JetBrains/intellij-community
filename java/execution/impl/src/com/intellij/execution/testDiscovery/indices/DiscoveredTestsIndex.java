// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testDiscovery.indices;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.IndexExtension;
import com.intellij.util.indexing.IndexId;
import com.intellij.util.indexing.impl.MapIndexStorage;
import com.intellij.util.indexing.impl.MapReduceIndex;
import com.intellij.util.indexing.impl.forward.KeyCollectionForwardIndexAccessor;
import com.intellij.util.indexing.impl.forward.PersistentMapBasedForwardIndex;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorIntegerDescriptor;
import com.intellij.util.io.IntCollectionDataExternalizer;
import com.intellij.util.io.KeyDescriptor;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

public final class DiscoveredTestsIndex extends MapReduceIndex<Integer, IntList, UsedSources> {
  DiscoveredTestsIndex(@NotNull Path file) throws IOException {
    super(INDEX_EXTENSION,
          new MyIndexStorage(file),
          new PersistentMapBasedForwardIndex(file.resolve("forward.idx"), false),
          new KeyCollectionForwardIndexAccessor<>(new IntCollectionDataExternalizer()));
  }

  @Override
  public void checkCanceled() {
    ProgressManager.checkCanceled();
  }

  @Override
  protected void requestRebuild(@NotNull Throwable e) {
    //TODO index corrupted
  }

  public boolean containsDataFrom(int testId) throws IOException {
    return getForwardIndex().get(testId) != null;
  }

  private static class MyIndexStorage extends MapIndexStorage<Integer, IntList> {
    protected MyIndexStorage(@NotNull Path storageFile) throws IOException {
      super(storageFile, EnumeratorIntegerDescriptor.INSTANCE, IntArrayExternalizer.INSTANCE, 4 * 1024, false);
    }

    @Override
    protected void checkCanceled() {
      ProgressManager.checkCanceled();
    }
  }

  private static final IndexExtension<Integer, IntList, UsedSources> INDEX_EXTENSION = new IndexExtension<>() {
    @NotNull
    @Override
    public IndexId<Integer, IntList> getName() {
      return IndexId.create("jvm.discovered.tests");
    }

    @NotNull
    @Override
    public DataIndexer<Integer, IntList, UsedSources> getIndexer() {
      return inputData -> inputData.myUsedMethods;
    }

    @NotNull
    @Override
    public KeyDescriptor<Integer> getKeyDescriptor() {
      return EnumeratorIntegerDescriptor.INSTANCE;
    }

    @NotNull
    @Override
    public DataExternalizer<IntList> getValueExternalizer() {
      return IntArrayExternalizer.INSTANCE;
    }

    @Override
    public int getVersion() {
      return DiscoveredTestDataHolder.VERSION;
    }
  };
}
