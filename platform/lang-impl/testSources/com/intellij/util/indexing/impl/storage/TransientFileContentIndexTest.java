// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.storage;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileBasedIndexExtension;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.indexing.ID;
import com.intellij.util.indexing.IdFilter;
import com.intellij.util.indexing.StorageException;
import com.intellij.util.indexing.impl.IndexStorage;
import com.intellij.util.indexing.impl.InputData;
import com.intellij.util.indexing.impl.UpdateData;
import com.intellij.util.indexing.impl.forward.ForwardIndex;
import com.intellij.util.indexing.impl.forward.ForwardIndexAccessor;
import com.intellij.util.indexing.impl.forward.MapForwardIndexAccessor;
import com.intellij.util.indexing.impl.forward.PersistentMapBasedForwardIndex;
import com.intellij.util.indexing.memory.InMemoryIndexStorage;
import com.intellij.util.indexing.storage.VfsAwareIndexStorageLayout;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.EnumeratorIntegerDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
class TransientFileContentIndexTest {
  private static final TestExtension EXTENSION = new TestExtension();
  private static final IdFilter FILTER = new IdFilter() {
    @Override
    public boolean containsFileId(int id) {
      return id <= 3;
    }
  };

  @Test
  void transientDataReplacesPersistentDataAndAddsNewFiles(@TempDir Path directory) throws Exception {
    try (var index = new TestIndex(directory)) {
      index.update(1, Map.of(), Map.of(10, 1, 20, 1));
      index.update(2, Map.of(), Map.of(20, 2, 30, 2));
      index.update(4, Map.of(), Map.of(60, 4));

      index.setBufferingEnabled(true);
      index.update(1, Map.of(10, 1, 20, 1), Map.of(20, 1, 40, 1));
      index.update(3, Map.of(), Map.of(50, 3));
      index.update(4, Map.of(60, 4), Map.of(70, 4));

      int serializedMapsBefore = MapExternalizer.SERIALIZATION_COUNT.get();
      int deserializedMapsBefore = MapExternalizer.DESERIALIZATION_COUNT.get();
      assertEquals(Set.of(20, 30, 40, 50), keys(index));
      assertEquals(serializedMapsBefore, MapExternalizer.SERIALIZATION_COUNT.get(), "Key traversal must not serialize transient maps");
      assertEquals(deserializedMapsBefore + 1, MapExternalizer.DESERIALIZATION_COUNT.get(),
                   "Key traversal must decode only the accepted persistent file without a transient map");
      int[] processed = {0};
      assertFalse(index.processAllKeys(_ -> {
        processed[0]++;
        return false;
      }, GlobalSearchScope.EMPTY_SCOPE, new IdFilter() {
        @Override
        public boolean containsFileId(int id) {
          return id == 3;
        }
      }));
      assertEquals(1, processed[0]);

      index.setBufferingEnabled(false);
      assertEquals(Map.of(10, 1, 20, 1), index.getIndexedFileData(1));
      assertEquals(Set.of(20, 30, 40, 50), keys(index));
    }
  }

  @Test
  void emptyTransientDataHidesPersistentKeysUntilCleanup(@TempDir Path directory) throws Exception {
    try (var index = new TestIndex(directory)) {
      index.update(1, Map.of(), Map.of(10, 1));
      index.update(2, Map.of(), Map.of(20, 2));

      index.setBufferingEnabled(true);
      index.update(1, Map.of(10, 1), Map.of());
      assertEquals(Set.of(20), keys(index));

      ProgressManager.getInstance().executeNonCancelableSection(() -> index.removeTransientDataForFile(1));
      assertEquals(Set.of(10, 20), keys(index));

      index.update(1, Map.of(10, 1), Map.of());
      index.cleanupMemoryStorage();
      assertEquals(Set.of(10, 20), keys(index));

    }
  }

  private static Set<Integer> keys(TestIndex index) throws StorageException {
    Set<Integer> keys = new HashSet<>();
    assertTrue(index.processAllKeys(key -> {
      assertTrue(keys.add(key), "A key must be processed once");
      return true;
    }, GlobalSearchScope.EMPTY_SCOPE, FILTER));
    return keys;
  }

  private static final class TestIndex extends TransientFileContentIndex<Integer, Integer, VfsAwareMapReduceIndex.IndexerIdHolder>
    implements AutoCloseable {
    TestIndex(Path directory) throws IOException {
      super(EXTENSION, new VfsAwareIndexStorageLayout<>() {
        private final ForwardIndexAccessor<Integer, Integer> accessor = new MapForwardIndexAccessor<>(new MapExternalizer());

        @Override
        public @NotNull IndexStorage<Integer, Integer> openIndexStorage() {
          return new InMemoryIndexStorage<>(EnumeratorIntegerDescriptor.INSTANCE);
        }

        @Override
        public void clearIndexData() { }

        @Override
        public ForwardIndex openForwardIndex() throws IOException {
          return new PersistentMapBasedForwardIndex(directory.resolve("forward"), false);
        }

        @Override
        public ForwardIndexAccessor<Integer, Integer> getForwardIndexAccessor() {
          return accessor;
        }
      });
    }

    void update(int fileId, Map<Integer, Integer> oldData, Map<Integer, Integer> newData) throws StorageException {
      updateWith(new UpdateData<>(fileId, indexId(), processor -> {
        if (oldData.equals(newData)) return false;
        for (Integer key : oldData.keySet()) {
          if (!newData.containsKey(key)) processor.removed(key, fileId);
        }
        for (var entry : newData.entrySet()) {
          if (!oldData.containsKey(entry.getKey())) processor.added(entry.getKey(), entry.getValue(), fileId);
        }
        return true;
      }, () -> updateForwardIndex(fileId, new InputData<>(newData))));
    }

    @Override
    public void close() {
      dispose();
    }
  }

  private static final class TestExtension extends FileBasedIndexExtension<Integer, Integer> {
    private static final ID<Integer, Integer> ID = com.intellij.util.indexing.ID.create("TransientFileContentIndexTest");

    @Override
    public @NotNull ID<Integer, Integer> getName() {
      return ID;
    }

    @Override
    public @NotNull DataIndexer<Integer, Integer, FileContent> getIndexer() {
      return _ -> Map.of();
    }

    @Override
    public @NotNull KeyDescriptor<Integer> getKeyDescriptor() {
      return EnumeratorIntegerDescriptor.INSTANCE;
    }

    @Override
    public @NotNull DataExternalizer<Integer> getValueExternalizer() {
      return EnumeratorIntegerDescriptor.INSTANCE;
    }

    @Override
    public @NotNull FileBasedIndex.InputFilter getInputFilter() {
      return _ -> true;
    }

    @Override
    public int getVersion() {
      return 1;
    }

    @Override
    public boolean dependsOnFileContent() {
      return true;
    }
  }

  private static final class MapExternalizer implements DataExternalizer<Map<Integer, Integer>> {
    private static final AtomicInteger SERIALIZATION_COUNT = new AtomicInteger();
    private static final AtomicInteger DESERIALIZATION_COUNT = new AtomicInteger();

    @Override
    public void save(@NotNull DataOutput out, Map<Integer, Integer> data) throws IOException {
      SERIALIZATION_COUNT.incrementAndGet();
      out.writeInt(data.size());
      for (var entry : data.entrySet()) {
        out.writeInt(entry.getKey());
        out.writeInt(entry.getValue());
      }
    }

    @Override
    public Map<Integer, Integer> read(@NotNull DataInput in) throws IOException {
      DESERIALIZATION_COUNT.incrementAndGet();
      int size = in.readInt();
      Map<Integer, Integer> data = new HashMap<>();
      for (int i = 0; i < size; i++) {
        data.put(in.readInt(), in.readInt());
      }
      return data;
    }
  }
}
