// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testDiscovery.indices;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.IndexExtension;
import com.intellij.util.indexing.IndexId;
import com.intellij.util.indexing.StorageException;
import com.intellij.util.indexing.impl.*;
import com.intellij.util.io.*;
import gnu.trove.TLongHashSet;
import gnu.trove.TLongIterator;
import gnu.trove.TLongProcedure;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class DiscoveredTestsIndex extends MapReduceIndex<Long, Void, DiscoveredTestsIndex.UsedMethods> {
  protected DiscoveredTestsIndex(@NotNull File file) throws IOException {
    super(INDEX_EXTENSION, new MyIndexStorage(file), new MyForwardIndex() {
      @NotNull
      @Override
      public PersistentHashMap<Integer, UsedMethods> createMap() throws IOException {
        return new PersistentHashMap<>(new File(file, "forward.idx"), EnumeratorIntegerDescriptor.INSTANCE, UsedMethods.USED_METHODS_DATA_EXTERNALIZER);
      }
    });
  }

  @Override
  public void checkCanceled() {
    ProgressManager.checkCanceled();
  }

  @Override
  protected void requestRebuild(Throwable e) {
    //TODO index corrupted
  }

  public boolean containsDataFrom(int testId) throws IOException {
    return ((MyForwardIndex)myForwardIndex).containsDataFrom(testId);
  }

  private static class MyIndexStorage extends MapIndexStorage<Long, Void> {
    protected MyIndexStorage(@NotNull File storageFile) throws IOException {
      super(storageFile, MethodQNameSerializer.INSTANCE, VoidDataExternalizer.INSTANCE, 4 * 1024, false);
    }

    @Override
    protected void checkCanceled() {
      ProgressManager.checkCanceled();
    }
  }

  private static final IndexExtension<Long, Void, UsedMethods> INDEX_EXTENSION = new IndexExtension<Long, Void, UsedMethods>() {
    @NotNull
    @Override
    public IndexId<Long, Void> getName() {
      return IndexId.create("jvm.discovered.tests");
    }

    @NotNull
    @Override
    public DataIndexer<Long, Void, UsedMethods> getIndexer() {return inputData -> inputData;}

    @NotNull
    @Override
    public KeyDescriptor<Long> getKeyDescriptor() {
      return MethodQNameSerializer.INSTANCE;
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


  private abstract static class MyForwardIndex extends MapBasedForwardIndex<Long, Void, UsedMethods> {
    protected MyForwardIndex() throws IOException {
      super(INDEX_EXTENSION);
    }

      @Override
    protected InputDataDiffBuilder<Long, Void> getDiffBuilder(int inputId, UsedMethods oldData) {
      return new InputDataDiffBuilder<Long, Void>(inputId) {
        @Override
        public boolean differentiate(@NotNull Map<Long, Void> newData,
                                     @NotNull KeyValueUpdateProcessor<Long, Void> addProcessor,
                                     @NotNull KeyValueUpdateProcessor<Long, Void> updateProcessor,
                                     @NotNull RemovedKeyProcessor<Long> removeProcessor) throws StorageException {
          boolean[] updated = {false};
          StorageException[] exception = {null};
          if (oldData != null) {
            oldData.forEach(m -> {
              if (!newData.containsKey(m)) {
                try {
                  removeProcessor.process(m, myInputId);
                  if (!updated[0]) {
                    updated[0] = true;
                  }
                }
                catch (StorageException e) {
                  exception[0] = e;
                  return false;
                }
              }
              return true;
            });
          }
          if (exception[0] != null) {
            throw exception[0];
          }

          ((UsedMethods)newData).forEach(m -> {
            if (oldData == null || !oldData.containsKey(m)) {
              try {
                addProcessor.process(m, null, myInputId);
                if (!updated[0]) {
                  updated[0] = true;
                }
              }
              catch (StorageException e) {
                exception[0] = e;
                return false;
              }
            }
            return true;
          });
          if (exception[0] != null) {
            throw exception[0];
          }
          return updated[0];
        }
      };
    }

    @Override
    protected UsedMethods convertToMapValueType(int inputId, Map<Long, Void> map) {
      return (UsedMethods) map;
    }

    public boolean containsDataFrom(int testId) throws IOException {
      return getInput(testId) != null;
    }
  }

  static class UsedMethods extends AbstractMap<Long, Void> {
    private final TLongHashSet myTestUsedMethods;

    UsedMethods(TLongHashSet methods) {myTestUsedMethods = methods;}

    public boolean contains(long method) {
      return myTestUsedMethods.contains(method);
    }

    public boolean forEach(@NotNull TLongProcedure methodProcedure) {
      return myTestUsedMethods.forEach(methodProcedure);
    }

    @Override
    public Set<Entry<Long, Void>> entrySet() {
      return new AbstractSet<Entry<Long, Void>>() {
        @Override
        public Iterator<Entry<Long, Void>> iterator() {
          TLongIterator methodIterator = myTestUsedMethods.iterator();
          return new Iterator<Entry<Long, Void>>() {
            @Override
            public boolean hasNext() {
              return methodIterator.hasNext();
            }

            @Override
            public Entry<Long, Void> next() {
              return new SimpleEntry<>(methodIterator.next(), null);
            }
          };

        }

        @Override
        public int size() {
          return myTestUsedMethods.size();
        }
      };
    }

    private static final DataExternalizer<UsedMethods> USED_METHODS_DATA_EXTERNALIZER = new DataExternalizer<UsedMethods>() {
      @Override
      public void save(@NotNull DataOutput out, UsedMethods value) throws IOException {
        DataInputOutputUtil.writeINT(out, value.size());
        IOException[] exception = {null};
        value.forEach(m -> {
          try {
            MethodQNameSerializer.INSTANCE.save(out, m);
          }
          catch (IOException e) {
            exception[0] = null;
            return false;
          }
          return true;
        });
        if (exception[0] != null) {
          throw exception[0];
        }
      }

      @Override
      public UsedMethods read(@NotNull DataInput in) throws IOException {
        int size = DataInputOutputUtil.readINT(in);
        TLongHashSet result = new TLongHashSet();
        for (int i = 0; i < size; i++) {
          result.add(MethodQNameSerializer.INSTANCE.read(in));
        }
        return new UsedMethods(result);
      }
    };
  }
}
