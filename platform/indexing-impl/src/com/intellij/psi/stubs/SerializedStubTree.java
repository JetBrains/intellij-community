// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
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
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@ApiStatus.Internal
public final class SerializedStubTree {
  private static final MessageDigest HASHER = DigestUtil.sha256();

  // serialized tree
  final byte[] myTreeBytes;
  final int myTreeByteLength;

  // stub forward indexes
  final byte[] myIndexedStubBytes;
  final int myIndexedStubByteLength;
  private Map<StubIndexKey<?, ?>, Map<Object, StubIdList>> myIndexedStubs;

  private final @NotNull SerializationManagerEx mySerializationManager;
  private final @NotNull StubForwardIndexExternalizer<?> myStubIndexesExternalizer;

  public SerializedStubTree(byte @NotNull [] treeBytes,
                            int treeByteLength,
                            byte @NotNull [] indexedStubBytes,
                            int indexedStubByteLength,
                            @Nullable Map<StubIndexKey<?, ?>, Map<Object, StubIdList>> indexedStubs,
                            @NotNull StubForwardIndexExternalizer<?> stubIndexesExternalizer,
                            @NotNull SerializationManagerEx serializationManager) {
    myTreeBytes = treeBytes;
    myTreeByteLength = treeByteLength;
    myIndexedStubBytes = indexedStubBytes;
    myIndexedStubByteLength = indexedStubByteLength;
    myIndexedStubs = indexedStubs;
    myStubIndexesExternalizer = stubIndexesExternalizer;
    mySerializationManager = serializationManager;
  }

  public static @NotNull SerializedStubTree serializeStub(@NotNull Stub rootStub,
                                                          @NotNull SerializationManagerEx serializationManager,
                                                          @NotNull StubForwardIndexExternalizer<?> forwardIndexExternalizer) throws IOException {
    final BufferExposingByteArrayOutputStream bytes = new BufferExposingByteArrayOutputStream();
    serializationManager.serialize(rootStub, bytes);
    byte[] treeBytes = bytes.getInternalBuffer();
    int treeByteLength = bytes.size();
    ObjectStubBase<?> root = (ObjectStubBase)rootStub;
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

  public @NotNull SerializedStubTree reSerialize(@NotNull SerializationManagerEx newSerializationManager,
                                                 @NotNull StubForwardIndexExternalizer<?> newForwardIndexSerializer) throws IOException {
    BufferExposingByteArrayOutputStream outStub = new BufferExposingByteArrayOutputStream();
    mySerializationManager.reSerialize(new ByteArrayInputStream(myTreeBytes, 0, myTreeByteLength), outStub, newSerializationManager);

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
      myIndexedStubs,
      newForwardIndexSerializer,
      newSerializationManager
    );
  }

  @ApiStatus.Internal
  @NotNull
  public StubForwardIndexExternalizer<?> getStubIndexesExternalizer() {
    return myStubIndexesExternalizer;
  }

  void restoreIndexedStubs() throws IOException {
    if (myIndexedStubs == null) {
      myIndexedStubs = myStubIndexesExternalizer.read(new DataInputStream(new ByteArrayInputStream(myIndexedStubBytes, 0, myIndexedStubByteLength)));
    }
  }

  <K> StubIdList restoreIndexedStubs(@NotNull StubIndexKey<K, ?> indexKey, @NotNull K key) throws IOException {
    Map<StubIndexKey<?, ?>, Map<Object, StubIdList>> incompleteMap = myStubIndexesExternalizer.doRead(new DataInputStream(new ByteArrayInputStream(myIndexedStubBytes, 0, myIndexedStubByteLength)), indexKey, key);
    if (incompleteMap == null) return null;
    Map<Object, StubIdList> map = incompleteMap.get(indexKey);
    return map == null ? null : map.get(key);
  }

  public @NotNull Map<StubIndexKey<?, ?>, Map<Object, StubIdList>> getStubIndicesValueMap() {
    try {
      restoreIndexedStubs();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    return myIndexedStubs;
  }

  public @NotNull Stub getStub() throws SerializerNotFoundException {
    if (myTreeByteLength == 0) {
      return NO_STUB;
    }
    return mySerializationManager.deserialize(new UnsyncByteArrayInputStream(myTreeBytes, 0, myTreeByteLength));
  }

  public @NotNull SerializedStubTree withoutStub() {
    return new SerializedStubTree(ArrayUtil.EMPTY_BYTE_ARRAY,
                                  0,
                                  myIndexedStubBytes,
                                  myIndexedStubByteLength,
                                  myIndexedStubs,
                                  myStubIndexesExternalizer,
                                  mySerializationManager);
  }

  @Override
  public boolean equals(final Object that) {
    if (this == that) {
      return true;
    }
    if (!(that instanceof SerializedStubTree)) {
      return false;
    }
    final SerializedStubTree thatTree = (SerializedStubTree)that;

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
                                       ? new StubTree((PsiFileStub)root, false)
                                       : new ObjectStubTree<>((ObjectStubBase<?>)root, false);
    Map<StubIndexKey<?, ?>, Map<Object, int[]>> map = objectStubTree.indexStubTree(k -> {
      //noinspection unchecked
      return StubIndexKeyDescriptorCache.INSTANCE.getKeyHashingStrategy((StubIndexKey<Object, ?>)k);
    });

    // xxx:fix refs inplace
    for (StubIndexKey key : map.keySet()) {
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
      myTreeHash = DigestUtil.calculateContentHash(HASHER, myTreeBytes, 0, myTreeByteLength);
    }
    return myTreeHash;
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
    public ObjectStubSerializer getStubType() {
      return null;
    }

    @Override
    public String toString() {
      return "<no stub>";
    }
  };
}
