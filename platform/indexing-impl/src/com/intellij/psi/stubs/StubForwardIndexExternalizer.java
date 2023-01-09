// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.stubs;

import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.HashingStrategy;
import com.intellij.util.indexing.ID;
import com.intellij.util.io.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.IOException;
import java.util.*;
import java.util.function.UnaryOperator;

public abstract class StubForwardIndexExternalizer<StubKeySerializationState>
  implements DataExternalizer<Map<StubIndexKey<?, ?>, Map<Object, StubIdList>>> {

  @ApiStatus.Internal
  public static final String USE_SHAREABLE_STUBS_PROP = "idea.uses.shareable.serialized.stubs";

  @ApiStatus.Internal
  public static final boolean USE_SHAREABLE_STUBS = Boolean.getBoolean(USE_SHAREABLE_STUBS_PROP);


  /**
   * If true -> serialization methods store values in a deterministic, stable order -- but at slightly
   * higher runtime cost (i.e. additional sorting). Useful e.g. for reproducible shared indexes.
   * <br/>
   * If false (default) -> serialization methods store values in arbitrary order, but slightly faster.
   */
  private final boolean useStableBinaryFormat;

  protected StubForwardIndexExternalizer() { this(false); }

  protected StubForwardIndexExternalizer(final boolean useStableBinaryFormat) { this.useStableBinaryFormat = useStableBinaryFormat; }

  @NotNull
  public static StubForwardIndexExternalizer<?> getIdeUsedExternalizer() {
    if (!USE_SHAREABLE_STUBS) {
      return new StubForwardIndexExternalizer.IdeStubForwardIndexesExternalizer();
    }
    return new FileLocalStubForwardIndexExternalizer();
  }

  @NotNull
  public static StubForwardIndexExternalizer<?> createFileLocalExternalizer() {
    return new FileLocalStubForwardIndexExternalizer();
  }

  /* ====================== abstract factory methods to override ====================================== */

  protected abstract StubKeySerializationState createStubIndexKeySerializationState(@NotNull DataOutput out, @NotNull Set<StubIndexKey<?, ?>> set) throws IOException;

  protected abstract void writeStubIndexKey(@NotNull DataOutput out, @NotNull StubIndexKey key, StubKeySerializationState state) throws IOException;

  protected abstract StubKeySerializationState createStubIndexKeySerializationState(@NotNull DataInput input, int stubIndexKeyCount) throws IOException;

  protected abstract ID<?, ?> readStubIndexKey(@NotNull DataInput input, StubKeySerializationState stubKeySerializationState) throws IOException;

  /* ====================== public API methods ======================================================== */
  @Override
  public final void save(@NotNull DataOutput out,
                         @NotNull Map<StubIndexKey<?, ?>, Map<Object, StubIdList>> indexedStubs) throws IOException {
    DataInputOutputUtil.writeINT(out, indexedStubs.size());
    if (!indexedStubs.isEmpty()) {
      final StubKeySerializationState stubKeySerializationState = createStubIndexKeySerializationState(out, indexedStubs.keySet());

      final Iterable<StubIndexKey<?, ?>> keysToStore = useStableBinaryFormat ? indexedStubs.keySet().stream().sorted(Comparator.comparing(StubIndexKey::getName)).toList()
                                                                             : indexedStubs.keySet();
      for (StubIndexKey stubIndexKey : keysToStore) {
        writeStubIndexKey(out, stubIndexKey, stubKeySerializationState);
        Map<Object, StubIdList> map = indexedStubs.get(stubIndexKey);
        serializeIndexValue(out, stubIndexKey, map);
      }
    }
  }

  @Override
  public final Map<StubIndexKey<?, ?>, Map<Object, StubIdList>> read(@NotNull DataInput in) throws IOException {
    return doRead(in, null, null);
  }

  /* ====================== implementation  =========================================================== */

  protected <K> Map<StubIndexKey<?, ?>, Map<Object, StubIdList>> doRead(@NotNull DataInput in,
                                                                        @Nullable StubIndexKey<K, ?> requestedIndex,
                                                                        @Nullable K requestedKey) throws IOException {
    int stubIndicesValueMapSize = DataInputOutputUtil.readINT(in);
    if (stubIndicesValueMapSize > 0) {
      Map<StubIndexKey<?, ?>, Map<Object, StubIdList>> stubIndicesValueMap =
        requestedIndex != null ? null : new HashMap<>(stubIndicesValueMapSize);
      StubKeySerializationState stubKeySerializationState = createStubIndexKeySerializationState(in, stubIndicesValueMapSize);
      for (int i = 0; i < stubIndicesValueMapSize; ++i) {
        ID<Object, ?> indexKey = (ID<Object, ?>)readStubIndexKey(in, stubKeySerializationState);
        if (indexKey instanceof StubIndexKey) { // indexKey can be ID in case of removed index
          StubIndexKey<Object, ?> stubIndexKey = (StubIndexKey<Object, ?>)indexKey;
          boolean deserialize = requestedIndex == null || requestedIndex.equals(stubIndexKey);
          if (deserialize) {
            Map<Object, StubIdList> value = deserializeIndexValue(in, stubIndexKey, requestedKey);
            if (requestedIndex != null) {
              return Collections.singletonMap(requestedIndex, value);
            }
            stubIndicesValueMap.put(stubIndexKey, value);
          }
          else {
            skipIndexValue(in);
          }
        }
        else {
          // key is deleted, just properly skip bytes (used while index update)
          assert indexKey == null : "indexKey '" + indexKey + "' is not a StubIndexKey";
          skipIndexValue(in);
        }
      }
      return stubIndicesValueMap;
    }
    return Collections.emptyMap();
  }

  private <K> void serializeIndexValue(final @NotNull DataOutput out,
                                       final @NotNull StubIndexKey<K, ?> stubIndexKey,
                                       final @NotNull Map<K, StubIdList> map) throws IOException {
    final KeyDescriptor<K> keyDescriptor = StubIndexKeyDescriptorCache.INSTANCE.getKeyDescriptor(stubIndexKey);

    final BufferExposingByteArrayOutputStream indexOs = new BufferExposingByteArrayOutputStream();
    try (DataOutputStream indexDos = new DataOutputStream(indexOs)) {
      //TODO RC: what if keys (type K) are not comparable?
      final Iterable<K> keysToStore = useStableBinaryFormat ? map.keySet().stream().sorted().toList()
                                                            : map.keySet();
      for (K key : keysToStore) {
        keyDescriptor.save(indexDos, key);
        final StubIdList idsList = map.get(key);
        //TODO RC: check idsList really sorted (as its javadoc suggests)?
        StubIdExternalizer.INSTANCE.save(indexDos, idsList);
      }
      DataInputOutputUtil.writeINT(out, indexDos.size());
    }
    out.write(indexOs.getInternalBuffer(), 0, indexOs.size());
  }

  @NotNull
  private <K> Map<K, StubIdList> deserializeIndexValue(@NotNull DataInput in, @NotNull StubIndexKey<K, ?> stubIndexKey, @Nullable K requestedKey) throws IOException {
    KeyDescriptor<K> keyDescriptor = StubIndexKeyDescriptorCache.INSTANCE.getKeyDescriptor(stubIndexKey);

    int bufferSize = DataInputOutputUtil.readINT(in);
    byte[] buffer = new byte[bufferSize];
    in.readFully(buffer);
    UnsyncByteArrayInputStream indexIs = new UnsyncByteArrayInputStream(buffer);
    DataInputStream indexDis = new DataInputStream(indexIs);
    HashingStrategy<K> hashingStrategy = StubIndexKeyDescriptorCache.INSTANCE.getKeyHashingStrategy(stubIndexKey);
    Map<K, StubIdList> result = CollectionFactory.createCustomHashingStrategyMap(hashingStrategy);
    while (indexDis.available() > 0) {
      K key = keyDescriptor.read(indexDis);
      StubIdList read = StubIdExternalizer.INSTANCE.read(indexDis);
      if (requestedKey == null) {
        result.put(key, read);
      }
      else if (hashingStrategy.equals(requestedKey, key)) {
        result.put(key, read);
        return result;
      }
    }
    return result;
  }

  private void skipIndexValue(@NotNull DataInput in) throws IOException {
    int bufferSize = DataInputOutputUtil.readINT(in);
    in.skipBytes(bufferSize);
  }

  private static final class IdeStubForwardIndexesExternalizer extends StubForwardIndexExternalizer<Void> {
    private IdeStubForwardIndexesExternalizer() { }

    @Override
    protected void writeStubIndexKey(@NotNull DataOutput out, @NotNull StubIndexKey key, Void aVoid) throws IOException {
      DataInputOutputUtil.writeINT(out, key.getUniqueId());
    }

    @Override
    protected Void createStubIndexKeySerializationState(@NotNull DataOutput out, @NotNull Set<StubIndexKey<?, ?>> set) {
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
    private FileLocalStubForwardIndexExternalizer() { }

    @Override
    protected FileLocalStringEnumerator createStubIndexKeySerializationState(@NotNull DataOutput out, @NotNull Set<StubIndexKey<?, ?>> set) throws IOException {
      FileLocalStringEnumerator enumerator = new FileLocalStringEnumerator(true);
      for (StubIndexKey<?, ?> key : set) {
        enumerator.enumerate(key.getName());
      }
      enumerator.write(out);
      return enumerator;
    }

    @Override
    protected void writeStubIndexKey(@NotNull DataOutput out, @NotNull StubIndexKey key, FileLocalStringEnumerator enumerator) throws IOException {
      DataInputOutputUtil.writeINT(out, enumerator.enumerate(key.getName()));
    }

    @Override
    protected FileLocalStringEnumerator createStubIndexKeySerializationState(@NotNull DataInput input, int stubIndexKeyCount)  throws IOException {
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
