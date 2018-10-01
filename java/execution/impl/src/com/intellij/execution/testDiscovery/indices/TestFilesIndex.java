// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testDiscovery.indices;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.IndexExtension;
import com.intellij.util.indexing.IndexId;
import com.intellij.util.indexing.impl.*;
import com.intellij.util.io.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

public class TestFilesIndex extends MapReduceIndex<Integer, Void, UsedSources> {
  protected TestFilesIndex(@NotNull File file) throws IOException {
    super(INDEX_EXTENSION, new MyIndexStorage(file), new MyForwardIndex() {
      @NotNull
      @Override
      public PersistentHashMap<Integer, Collection<Integer>> createMap() throws IOException {
        return new PersistentHashMap<>(new File(file, "forward.idx"), EnumeratorIntegerDescriptor.INSTANCE,
                                       new IntCollectionDataExternalizer());
      }
    });
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
    return ((MyForwardIndex)myForwardIndex).containsDataFrom(testId);
  }

  private static class MyIndexStorage extends MapIndexStorage<Integer, Void> {
    protected MyIndexStorage(@NotNull File storageFile) throws IOException {
      super(storageFile, EnumeratorIntegerDescriptor.INSTANCE, VoidDataExternalizer.INSTANCE, 4 * 1024, false);
    }

    @Override
    protected void checkCanceled() {
      ProgressManager.checkCanceled();
    }
  }

  private static final IndexExtension<Integer, Void, UsedSources> INDEX_EXTENSION = new IndexExtension<Integer, Void, UsedSources>() {
    @NotNull
    @Override
    public IndexId<Integer, Void> getName() {
      return IndexId.create("jvm.discovered.test.files");
    }

    @NotNull
    @Override
    public DataIndexer<Integer, Void, UsedSources> getIndexer() {return inputData -> inputData.myUsedFiles;}

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


  private abstract static class MyForwardIndex extends KeyCollectionBasedForwardIndex<Integer, Void> {
    protected MyForwardIndex() throws IOException {
      super(INDEX_EXTENSION);
    }

    @Nullable
    public Collection<Integer> containsDataFrom(int testId) throws IOException {
      return getInput(testId);
    }
  }
}
