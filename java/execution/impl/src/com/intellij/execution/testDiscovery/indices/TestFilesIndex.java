// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testDiscovery.indices;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.IndexExtension;
import com.intellij.util.indexing.IndexId;
import com.intellij.util.indexing.impl.MapIndexStorage;
import com.intellij.util.indexing.impl.MapReduceIndex;
import com.intellij.util.indexing.impl.forward.ForwardIndex;
import com.intellij.util.indexing.impl.forward.KeyCollectionForwardIndexAccessor;
import com.intellij.util.indexing.impl.forward.PersistentMapBasedForwardIndex;
import com.intellij.util.io.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;

public class TestFilesIndex extends MapReduceIndex<Integer, Void, UsedSources> {
  protected TestFilesIndex(@NotNull Path file) throws IOException {
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

  @Nullable
  Collection<Integer> getTestDataFor(int testId) throws IOException {
    ForwardIndex forwardIndex = getForwardIndex();
    KeyCollectionForwardIndexAccessor<Integer, Void> forwardIndexAccessor = (KeyCollectionForwardIndexAccessor<Integer, Void>)getForwardIndexAccessor();
    return forwardIndexAccessor.deserializeData(forwardIndex.get(testId));
  }

  private static class MyIndexStorage extends MapIndexStorage<Integer, Void> {
    protected MyIndexStorage(@NotNull Path storageFile) throws IOException {
      super(storageFile, EnumeratorIntegerDescriptor.INSTANCE, VoidDataExternalizer.INSTANCE, 4 * 1024, false);
    }

    @Override
    protected void checkCanceled() {
      ProgressManager.checkCanceled();
    }
  }

  private static final IndexExtension<Integer, Void, UsedSources> INDEX_EXTENSION = new IndexExtension<>() {
    @NotNull
    @Override
    public IndexId<Integer, Void> getName() {
      return IndexId.create("jvm.discovered.test.files");
    }

    @NotNull
    @Override
    public DataIndexer<Integer, Void, UsedSources> getIndexer() {
      return inputData -> inputData.myUsedFiles;
    }

    @NotNull
    @Override
    public KeyDescriptor<Integer> getKeyDescriptor() {
      return EnumeratorIntegerDescriptor.INSTANCE;
    }

    @NotNull
    @Override
    public DataExternalizer<Void> getValueExternalizer() {
      return VoidDataExternalizer.INSTANCE;
    }

    @Override
    public int getVersion() {
      return DiscoveredTestDataHolder.VERSION;
    }
  };
}
