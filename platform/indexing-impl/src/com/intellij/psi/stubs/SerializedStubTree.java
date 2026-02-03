// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.stubs;

import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.util.ArrayUtil;
import com.intellij.util.io.DigestUtil;
import com.intellij.util.io.UnsyncByteArrayInputStream;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@ApiStatus.Internal
public final class SerializedStubTree {
  // serialized tree
  final byte[] myTreeBytes;
  final int myTreeByteLength;

  // stub forward indexes
  final byte[] myIndexedStubBytes;
  final int myIndexedStubByteLength;
  private final DeserializedIndexedStubs myDeserializedIndexedStubs;
  private final @NotNull StubTreeSerializer mySerializationManager;
  private final @NotNull StubForwardIndexExternalizer<?> myStubIndexesExternalizer;

  private static class DeserializedIndexedStubs {
    private enum RestoreState {
      NOT_RESTORED, INCOMPLETE, RESTORED
    }
    private volatile @NotNull RestoreState myState;
    private volatile @Nullable("nullable when myState == NOT_RESTORED") Map<StubIndexKey<?, ?>, Map<Object, StubIdList>> myMap;

    DeserializedIndexedStubs(@NotNull Map<StubIndexKey<?, ?>, Map<Object, StubIdList>> map) {
      myState = RestoreState.RESTORED;
      myMap = Collections.unmodifiableMap(map);
    }

    DeserializedIndexedStubs() {
      myState = RestoreState.NOT_RESTORED;
    }

    synchronized Map<Object, StubIdList> getIfRestored(StubIndexKey<?, ?> indexKey) {
      Map<StubIndexKey<?, ?>, Map<Object, StubIdList>> map = myMap;
      return map != null ? map.get(indexKey) : null;
    }

    synchronized void setRestoredMap(Map<StubIndexKey<?, ?>, Map<Object, StubIdList>> restoredMap) {
      if (myState == RestoreState.NOT_RESTORED || myState == RestoreState.INCOMPLETE) {
        myState = RestoreState.RESTORED;
        myMap = Collections.unmodifiableMap(restoredMap);
      }
    }

    synchronized void setPartialMap(Map<Object, StubIdList> partialMap, StubIndexKey<?, ?> stubIndexKey) {
      if (myState == RestoreState.RESTORED) {
        return;
      }
      if (myState == RestoreState.NOT_RESTORED) {
        myState = RestoreState.INCOMPLETE;
        myMap = new HashMap<>(1);
      }
      Objects.requireNonNull(myMap).put(stubIndexKey, partialMap);
    }

    @Override
    public String toString() {
      return "map=" + myMap + ",state=" + myState;
    }
  }

  public SerializedStubTree(byte @NotNull [] treeBytes,
                            int treeByteLength,
                            byte @NotNull [] indexedStubBytes,
                            int indexedStubByteLength,
                            @Nullable Map<StubIndexKey<?, ?>, Map<Object, StubIdList>> indexedStubs,
                            @NotNull StubForwardIndexExternalizer<?> stubIndexesExternalizer,
                            @NotNull StubTreeSerializer serializationManager) {
    myTreeBytes = treeBytes;
    myTreeByteLength = treeByteLength;
    myIndexedStubBytes = indexedStubBytes;
    myIndexedStubByteLength = indexedStubByteLength;
    myDeserializedIndexedStubs = indexedStubs == null ? new DeserializedIndexedStubs() : new DeserializedIndexedStubs(indexedStubs);
    myStubIndexesExternalizer = stubIndexesExternalizer;
    mySerializationManager = serializationManager;
  }

  public static @NotNull SerializedStubTree serializeStub(@NotNull Stub rootStub,
                                                          @NotNull StubTreeSerializer serializationManager,
                                                          @NotNull StubForwardIndexExternalizer<?> forwardIndexExternalizer) throws IOException {
    final BufferExposingByteArrayOutputStream bytes = new BufferExposingByteArrayOutputStream();
    serializationManager.serialize(rootStub, bytes);
    byte[] treeBytes = bytes.getInternalBuffer();
    int treeByteLength = bytes.size();
    ObjectStubBase<?> root = (ObjectStubBase<?>)rootStub;
    Map<StubIndexKey<?, ?>, Map<Object, StubIdList>> indexedStubs = indexTree(root);
    final BufferExposingByteArrayOutputStream indexBytes = new BufferExposingByteArrayOutputStream();
    forwardIndexExternalizer.save(new DataOutputStream(indexBytes), indexedStubs);
    byte[] indexedStubBytes = indexBytes.getInternalBuffer();
    int indexedStubByteLength = indexBytes.size();
    return new SerializedStubTree(
      treeBytes,
      treeByteLength,
      indexedStubBytes,
      indexedStubByteLength,
      indexedStubs,
      forwardIndexExternalizer,
      serializationManager
    );
  }

  public @NotNull SerializedStubTree reSerialize(@NotNull StubTreeSerializer newSerializationManager,
                                                 @NotNull StubForwardIndexExternalizer<?> newForwardIndexSerializer) throws IOException {
    BufferExposingByteArrayOutputStream outStub = new BufferExposingByteArrayOutputStream();
    ((SerializationManagerEx)mySerializationManager).reSerialize(new ByteArrayInputStream(myTreeBytes, 0, myTreeByteLength), outStub, newSerializationManager);

    byte[] reSerializedIndexBytes;
    int reSerializedIndexByteLength;

    if (myStubIndexesExternalizer == newForwardIndexSerializer) {
      reSerializedIndexBytes = myIndexedStubBytes;
      reSerializedIndexByteLength = myIndexedStubByteLength;
    }
    else {
      BufferExposingByteArrayOutputStream reSerializedStubIndices = new BufferExposingByteArrayOutputStream();
      newForwardIndexSerializer.save(new DataOutputStream(reSerializedStubIndices), getStubIndicesValueMap());
      reSerializedIndexBytes = reSerializedStubIndices.getInternalBuffer();
      reSerializedIndexByteLength = reSerializedStubIndices.size();
    }

    return new SerializedStubTree(
      outStub.getInternalBuffer(),
      outStub.size(),
      reSerializedIndexBytes,
      reSerializedIndexByteLength,
      myDeserializedIndexedStubs.myMap,
      newForwardIndexSerializer,
      newSerializationManager
    );
  }

  void restoreIndexedStubs() throws IOException {
    if (myDeserializedIndexedStubs.myState != DeserializedIndexedStubs.RestoreState.RESTORED) {
      DataInputStream in = new DataInputStream(new ByteArrayInputStream(myIndexedStubBytes, 0, myIndexedStubByteLength));
      Map<StubIndexKey<?, ?>, Map<Object, StubIdList>> restoredMap = myStubIndexesExternalizer.read(in);
      myDeserializedIndexedStubs.setRestoredMap(restoredMap);
    }
  }

  <K> StubIdList restoreIndexedStubs(@NotNull StubIndexKey<K, ?> indexKey, @NotNull K key) throws IOException {
    Map<Object, StubIdList> incompleteMap = myDeserializedIndexedStubs.getIfRestored(indexKey);
    if (incompleteMap == null) {
      DataInputStream dataInputStream = new DataInputStream(new ByteArrayInputStream(myIndexedStubBytes, 0, myIndexedStubByteLength));
      Map<StubIndexKey<?, ?>, Map<Object, StubIdList>> readData = myStubIndexesExternalizer.doRead(dataInputStream, indexKey, null);
      if (readData == null) {
        readData = Collections.emptyMap();
      }
      incompleteMap = readData.get(indexKey);
      if (incompleteMap == null) {
        incompleteMap = Collections.emptyMap();
      }
      myDeserializedIndexedStubs.setPartialMap(incompleteMap, indexKey);
    }
    return incompleteMap.get(key);
  }

  public @NotNull Map<StubIndexKey<?, ?>, Map<Object, StubIdList>> getStubIndicesValueMap() {
    try {
      restoreIndexedStubs();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    return myDeserializedIndexedStubs.myMap;
  }

  public @NotNull Stub getStub() throws SerializerNotFoundException {
    if (myTreeByteLength == 0) {
      return NO_STUB;
    }
    return mySerializationManager.deserialize(new UnsyncByteArrayInputStream(myTreeBytes, 0, myTreeByteLength));
  }

  public @NotNull SerializedStubTree withoutStub() {
    try {
      restoreIndexedStubs();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    return new SerializedStubTree(ArrayUtil.EMPTY_BYTE_ARRAY,
                                  0,
                                  myIndexedStubBytes,
                                  myIndexedStubByteLength,
                                  myDeserializedIndexedStubs.myMap,
                                  myStubIndexesExternalizer,
                                  mySerializationManager);
  }

  @Override
  public boolean equals(final Object that) {
    if (this == that) {
      return true;
    }
    if (!(that instanceof SerializedStubTree thatTree)) {
      return false;
    }

    final int length = myTreeByteLength;
    if (length != thatTree.myTreeByteLength) {
      return false;
    }

    for (int i = 0; i < length; i++) {
      if (myTreeBytes[i] != thatTree.myTreeBytes[i]) {
        return false;
      }
    }

    return true;
  }

  @Override
  public int hashCode() {

    int result = 1;
    for (int i = 0; i < myTreeByteLength; i++) {
      result = 31 * result + myTreeBytes[i];
    }

    return result;
  }

  static @NotNull Map<StubIndexKey<?, ?>, Map<Object, StubIdList>> indexTree(@NotNull Stub root) {
    ObjectStubTree<?> objectStubTree = root instanceof PsiFileStub
                                       ? new StubTree((PsiFileStub<?>)root, false)
                                       : new ObjectStubTree<>((ObjectStubBase<?>)root, false);
    Map<StubIndexKey<?, ?>, Map<Object, int[]>> map = objectStubTree.indexStubTree(k -> {
      //noinspection unchecked
      return StubIndexKeyDescriptorCache.INSTANCE.getKeyHashingStrategy((StubIndexKey<Object, ?>)k);
    });

    // xxx:fix refs inplace
    for (StubIndexKey<?, ?> key : map.keySet()) {
      Map<Object, int[]> value = map.get(key);
      for (Object k : value.keySet()) {
        int[] ints = value.get(k);
        StubIdList stubList = ints.length == 1 ? new StubIdList(ints[0]) : new StubIdList(ints, ints.length);
        ((Map<Object, StubIdList>)(Map)value).put(k, stubList);
      }
    }
    return (Map<StubIndexKey<?, ?>, Map<Object, StubIdList>>)(Map)map;
  }

  private byte[] myTreeHash;
  public synchronized byte @NotNull [] getTreeHash() {
    if (myTreeHash == null) {
      // Probably we don't need to hash the length and "\0000".
      MessageDigest digest = DigestUtil.sha256();
      digest.update(String.valueOf(myTreeByteLength).getBytes(StandardCharsets.UTF_8));
      digest.update("\u0000".getBytes(StandardCharsets.UTF_8));
      digest.update(myTreeBytes, 0, myTreeByteLength);
      myTreeHash = digest.digest();
    }
    return myTreeHash;
  }

  @Override
  public String toString() {
    return "Stub[" + myDeserializedIndexedStubs + "]";
  }

  // TODO replace it with separate StubTreeLoader implementation
  public static final Stub NO_STUB = new Stub() {
    @Override
    public Stub getParentStub() {
      return null;
    }

    @Override
    public @NotNull List<? extends Stub> getChildrenStubs() {
      return Collections.emptyList();
    }

    @Override
    public ObjectStubSerializer<?, ?> getStubType() {
      return null;
    }

    @Override
    public String toString() {
      return "<no stub>";
    }
  };
}
