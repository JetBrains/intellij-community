// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.stubs;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.util.indexing.ID;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

public abstract class StubForwardIndexExternalizer<StubKeySerializationState> implements DataExternalizer<Map<StubIndexKey, Map<Object, StubIdList>>> {

  private final SerializationManagerEx myManagerToInitialize;

  protected StubForwardIndexExternalizer(SerializationManagerEx managerToInitialize) {
    myManagerToInitialize = managerToInitialize;
  }

  @NotNull
  public static StubForwardIndexExternalizer<?> getIdeUsedExternalizer(@NotNull SerializationManagerEx managerToInitialize) {
    if (System.getProperty("idea.uses.shareable.serialized.stubs") == null) {
      return new StubForwardIndexExternalizer.IdeStubForwardIndexesExternalizer(managerToInitialize);
    }
    return new FileLocalStubForwardIndexExternalizer(managerToInitialize);
  }

  @NotNull
  public static StubForwardIndexExternalizer<?> createFileLocalExternalizer(@NotNull SerializationManagerEx serializationManager) {
    return new FileLocalStubForwardIndexExternalizer(serializationManager);
  }

  private static void initializeSerializationManager(@NotNull SerializationManagerEx serializationManager) {
    //TODO: consider initializing the serialization manager in another place.
    ProgressManager.getInstance().executeNonCancelableSection(() -> {
      serializationManager.initSerializers();
      StubIndexEx.initExtensions();
    });
    ((StubIndexEx)StubIndex.getInstance()).ensureLoaded();
  }

  protected abstract StubKeySerializationState createStubIndexKeySerializationState(@NotNull DataOutput out, @NotNull Set<StubIndexKey> set) throws IOException;

  protected abstract void writeStubIndexKey(@NotNull DataOutput out, @NotNull StubIndexKey key, StubKeySerializationState state) throws IOException;

  protected abstract StubKeySerializationState createStubIndexKeySerializationState(@NotNull DataInput input, int stubIndexKeyCount) throws IOException;

  protected abstract ID<?, ?> readStubIndexKey(@NotNull DataInput input, StubKeySerializationState stubKeySerializationState) throws IOException;

  @Override
  public final void save(@NotNull DataOutput out, Map<StubIndexKey, Map<Object, StubIdList>> indexedStubs) throws IOException {

    DataInputOutputUtil.writeINT(out, indexedStubs.size());
    if (!indexedStubs.isEmpty()) {
      StubKeySerializationState stubKeySerializationState = createStubIndexKeySerializationState(out, indexedStubs.keySet());

      StubIndexEx stubIndex = (StubIndexEx)StubIndex.getInstance();
      for (StubIndexKey stubIndexKey : indexedStubs.keySet()) {
        writeStubIndexKey(out, stubIndexKey, stubKeySerializationState);
        Map<Object, StubIdList> map = indexedStubs.get(stubIndexKey);
        stubIndex.serializeIndexValue(out, stubIndexKey, map);
      }
    }
  }

  @Override
  public final Map<StubIndexKey, Map<Object, StubIdList>> read(@NotNull DataInput in) throws IOException {
    return doRead(in, null, null);
  }

  <K> Map<StubIndexKey, Map<Object, StubIdList>> doRead(@NotNull DataInput in, @Nullable StubIndexKey<K, ?> requestedIndex, @Nullable K requestedKey) throws IOException {
    initializeSerializationManager(myManagerToInitialize);
    int stubIndicesValueMapSize = DataInputOutputUtil.readINT(in);
    if (stubIndicesValueMapSize > 0) {
      THashMap<StubIndexKey, Map<Object, StubIdList>> stubIndicesValueMap = requestedIndex != null ? null : new THashMap<>(stubIndicesValueMapSize);
      StubIndexEx stubIndex = (StubIndexEx)StubIndex.getInstance();
      StubKeySerializationState stubKeySerializationState = createStubIndexKeySerializationState(in, stubIndicesValueMapSize);
      for (int i = 0; i < stubIndicesValueMapSize; ++i) {
        ID<Object, ?> indexKey = (ID<Object, ?>)readStubIndexKey(in, stubKeySerializationState);
        if (indexKey instanceof StubIndexKey) { // indexKey can be ID in case of removed index
          StubIndexKey<Object, ?> stubIndexKey = (StubIndexKey<Object, ?>)indexKey;
          boolean deserialize = requestedIndex == null || requestedIndex.equals(stubIndexKey);
          if (deserialize) {
            Map<Object, StubIdList> value = stubIndex.deserializeIndexValue(in, stubIndexKey, requestedKey);
            if (requestedIndex != null) {
              return Collections.singletonMap(requestedIndex, value);
            }
            stubIndicesValueMap.put(stubIndexKey, value);
          } else {
            stubIndex.skipIndexValue(in);
          }
        } else {
          // key is deleted, just properly skip bytes (used while index update)
          assert indexKey == null;
          stubIndex.skipIndexValue(in);
        }
      }
      return stubIndicesValueMap;
    }
    return Collections.emptyMap();
  }

  private static final class IdeStubForwardIndexesExternalizer extends StubForwardIndexExternalizer<Void> {
    private IdeStubForwardIndexesExternalizer(@NotNull SerializationManagerEx managerToInitialize) {
      super(managerToInitialize);
    }

    @Override
    protected void writeStubIndexKey(@NotNull DataOutput out, @NotNull StubIndexKey key, Void aVoid) throws IOException {
      DataInputOutputUtil.writeINT(out, key.getUniqueId());
    }

    @Override
    protected Void createStubIndexKeySerializationState(@NotNull DataOutput out, @NotNull Set<StubIndexKey> set) {
      return null;
    }

    @Override
    protected ID<?, ?> readStubIndexKey(@NotNull DataInput input, Void aVoid) throws IOException {
      return ID.findById(DataInputOutputUtil.readINT(input));
    }

    @Override
    protected Void createStubIndexKeySerializationState(@NotNull DataInput input, int stubIndexKeyCount) {
      return null;
    }
  }

  private static final class FileLocalStubForwardIndexExternalizer extends StubForwardIndexExternalizer<FileLocalStringEnumerator> {
    private FileLocalStubForwardIndexExternalizer(SerializationManagerEx managerToInitialize) {
      super(managerToInitialize);
    }

    @Override
    protected FileLocalStringEnumerator createStubIndexKeySerializationState(@NotNull DataOutput out, @NotNull Set<StubIndexKey> set) throws IOException {
      FileLocalStringEnumerator enumerator = new FileLocalStringEnumerator(true);
      for (StubIndexKey<?, ?> key : set) {
        enumerator.enumerate(key.getName());
      }
      enumerator.write(out);
      return enumerator;
    }

    @Override
    protected void writeStubIndexKey(@NotNull DataOutput out, @NotNull StubIndexKey key, FileLocalStringEnumerator enumerator)
      throws IOException {
      DataInputOutputUtil.writeINT(out, enumerator.enumerate(key.getName()));

    }

    @Override
    protected FileLocalStringEnumerator createStubIndexKeySerializationState(@NotNull DataInput input, int stubIndexKeyCount)
      throws IOException {
      FileLocalStringEnumerator enumerator = new FileLocalStringEnumerator(false);
      enumerator.read(input, UnaryOperator.identity());
      return enumerator;
    }

    @Override
    protected ID<?, ?> readStubIndexKey(@NotNull DataInput input, FileLocalStringEnumerator enumerator) throws IOException {
      int idx = DataInputOutputUtil.readINT(input);
      String name = enumerator.valueOf(idx);
      if (name == null) {
        throw new IOException("corrupted data: no value for idx = " + idx + " in local enumerator");
      }
      return ID.findByName(name);
    }
  }
}
