// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.stubs;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.LogUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.ConcurrentIntObjectMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.RecentStringInterner;
import com.intellij.util.io.AbstractStringEnumerator;
import com.intellij.util.io.DataEnumeratorEx;
import com.intellij.util.io.DataInputOutputUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

final class StubSerializationHelper {
  private static final Logger LOG = Logger.getInstance(StubSerializationHelper.class);

  private final DataEnumeratorEx<String> myNameStorage;

  private final Int2ObjectOpenHashMap<String> myIdToName = new Int2ObjectOpenHashMap<>();
  private final Object2IntOpenHashMap<String> myNameToId = new Object2IntOpenHashMap<>();
  private final Object2ObjectOpenHashMap<String, Supplier<ObjectStubSerializer<?, Stub>>> myNameToLazySerializer = new Object2ObjectOpenHashMap<>();

  private final ConcurrentIntObjectMap<ObjectStubSerializer<?, Stub>> myIdToSerializer = ContainerUtil.createConcurrentIntObjectMap();
  private final Map<ObjectStubSerializer<?, Stub>, Integer> mySerializerToId = new ConcurrentHashMap<>();

  private final boolean myUnmodifiable;
  private final RecentStringInterner myStringInterner;

  void dropRegisteredSerializers() {
    myIdToName.clear();
    myNameToId.clear();
    myNameToLazySerializer.clear();

    myIdToSerializer.clear();
    mySerializerToId.clear();
  }

  StubSerializationHelper(@NotNull DataEnumeratorEx<String> nameStorage, boolean unmodifiable, @NotNull Disposable parentDisposable) {
    myNameStorage = nameStorage;
    myUnmodifiable = unmodifiable;
    myStringInterner = new RecentStringInterner(parentDisposable);
  }

  void assignId(@NotNull Supplier<ObjectStubSerializer<?, Stub>> serializer, String name) throws IOException {
    Supplier<ObjectStubSerializer<?, Stub>> old = myNameToLazySerializer.put(name, serializer);
    if (old != null) {
      ObjectStubSerializer<?, Stub> existing = old.get();
      ObjectStubSerializer<?, Stub> computed = serializer.get();
      if (existing != computed) {
        throw new AssertionError("ID: " + name + " is not unique, but found in both " +
                                 existing.getClass().getName() + " and " + computed.getClass().getName());
      }
      return;
    }

    int id;
    if (myUnmodifiable) {
      id = myNameStorage.tryEnumerate(name);
      if (id == 0) {
        LOG.debug("serialized " + name + " is ignored in unmodifiable stub serialization manager");
        return;
      }
    }
    else {
      id = myNameStorage.enumerate(name);
    }
    myIdToName.put(id, name);
    myNameToId.put(name, id);
  }

  void copyFrom(@Nullable StubSerializationHelper helper) throws IOException {
    if (helper == null) {
      return;
    }

    for (Iterator<Object2ObjectMap.Entry<String, Supplier<ObjectStubSerializer<?, Stub>>>> iterator = helper.myNameToLazySerializer.object2ObjectEntrySet().fastIterator(); iterator.hasNext(); ) {
      Object2ObjectMap.Entry<String, Supplier<ObjectStubSerializer<?, Stub>>> entry = iterator.next();
      assignId(entry.getValue(), entry.getKey());
    }
  }

  private @NotNull ObjectStubSerializer<Stub, Stub> writeSerializerId(Stub stub, @NotNull DataOutput stream, IntEnumerator serializerLocalEnumerator)
    throws IOException {
    ObjectStubSerializer<Stub, Stub> serializer = StubSerializationUtil.getSerializer(stub);
    if (serializer == null) {
      throw new Error("No serializer was returned for " + stub);
    }
    DataInputOutputUtil.writeINT(stream, serializerLocalEnumerator.enumerate(getClassId(serializer)));
    return serializer;
  }

  private void serializeSelf(Stub stub,
                             @NotNull StubOutputStream stream,
                             IntEnumerator serializerLocalEnumerator) throws IOException {
    if (((ObjectStubBase<?>)stub).isDangling()) {
      stream.writeByte(0);
    }
    writeSerializerId(stub, stream, serializerLocalEnumerator).serialize(stub, stream);
  }

  private void serializeChildren(@NotNull Stub parent,
                                 @NotNull StubOutputStream stream,
                                 IntEnumerator serializerLocalEnumerator) throws IOException {
    final List<? extends Stub> children = parent.getChildrenStubs();
    DataInputOutputUtil.writeINT(stream, children.size());
    for (Stub child : children) {
      serializeSelf(child, stream, serializerLocalEnumerator);
      serializeChildren(child, stream, serializerLocalEnumerator);
    }
  }

  void serialize(@NotNull Stub rootStub, @NotNull OutputStream stream) throws IOException {
    BufferExposingByteArrayOutputStream out = new BufferExposingByteArrayOutputStream();
    FileLocalStringEnumerator storage = new FileLocalStringEnumerator(true);
    IntEnumerator serializerIdLocalEnumerator = new IntEnumerator();
    StubOutputStream stubOutputStream = new StubOutputStream(out, storage);
    boolean doDefaultSerialization = true;

    if (rootStub instanceof PsiFileStubImpl) {
      PsiFileStub<?>[] roots = ((PsiFileStubImpl<?>)rootStub).getStubRoots();
      if (roots.length == 0) {
        Logger.getInstance(getClass()).error("Incorrect stub files count during serialization:" + rootStub + "," + rootStub.getStubType());
      }
      else {
        doDefaultSerialization = false;
        DataInputOutputUtil.writeINT(stubOutputStream, roots.length);
        for (PsiFileStub<?> root : roots) {
          serializeRoot(stubOutputStream, root, storage, serializerIdLocalEnumerator);
        }
      }
    }

    if (doDefaultSerialization) {
      DataInputOutputUtil.writeINT(stubOutputStream, 1);
      serializeRoot(stubOutputStream, rootStub, storage, serializerIdLocalEnumerator);
    }
    DataOutputStream resultStream = new DataOutputStream(stream);
    serializerIdLocalEnumerator.dump(resultStream);
    storage.write(resultStream);
    resultStream.write(out.getInternalBuffer(), 0, out.size());
  }

  private int getClassId(final @NotNull ObjectStubSerializer<Stub, Stub> serializer) {
    Integer idValue = mySerializerToId.get(serializer);
    if (idValue == null) {
      String name = serializer.getExternalId();
      idValue = myNameToId.getInt(name);
      assert idValue > 0 : "No ID found for serializer " + LogUtil.objectAndClass(serializer) +
                           ", external id:" + name +
                           (serializer instanceof IElementType ? ", language:" + ((IElementType)serializer).getLanguage() + ", " + serializer : "");
      mySerializerToId.put(serializer, idValue);
    }
    return idValue;
  }

  private static final ThreadLocal<ObjectStubSerializer<?, Stub>> ourRootStubSerializer = new ThreadLocal<>();

  @NotNull
  Stub deserialize(@NotNull InputStream stream) throws IOException, SerializerNotFoundException {
    FileLocalStringEnumerator storage = new FileLocalStringEnumerator(false);
    StubInputStream inputStream = new StubInputStream(stream, storage);
    IntEnumerator serializerLocalEnumerator = IntEnumerator.read(inputStream);
    storage.read(inputStream, this::intern);

    final int stubFilesCount = DataInputOutputUtil.readINT(inputStream);
    if (stubFilesCount <= 0) {
      Logger.getInstance(getClass()).error("Incorrect stub files count during deserialization:"+stubFilesCount);
    }

    Stub baseStub = deserializeRoot(inputStream, storage, serializerLocalEnumerator);
    List<PsiFileStub<?>> stubs = new ArrayList<>(stubFilesCount);
    if (baseStub instanceof PsiFileStub) {
      stubs.add((PsiFileStub<?>)baseStub);
    }
    for (int j = 1; j < stubFilesCount; j++) {
      Stub deserialize = deserializeRoot(inputStream, storage, serializerLocalEnumerator);
      if (deserialize instanceof PsiFileStub) {
        stubs.add((PsiFileStub<?>)deserialize);
      }
      else {
        Logger.getInstance(getClass()).error("Stub root must be PsiFileStub for files with several stub roots");
      }
    }
    PsiFileStub<?>[] stubsArray = stubs.toArray(PsiFileStub.EMPTY_ARRAY);
    for (PsiFileStub<?> stub : stubsArray) {
      if (stub instanceof PsiFileStubImpl) {
        ((PsiFileStubImpl<?>)stub).setStubRoots(stubsArray);
      }
    }
    return baseStub;
  }

  private Stub deserializeRoot(StubInputStream inputStream,
                               FileLocalStringEnumerator storage,
                               IntEnumerator serializerLocalEnumerator) throws IOException, SerializerNotFoundException {
    ObjectStubSerializer<?, Stub> serializer = getClassById(DataInputOutputUtil.readINT(inputStream), null, serializerLocalEnumerator);
    ourRootStubSerializer.set(serializer);
    try {
      Stub stub = serializer.deserialize(inputStream, null);
      if (stub instanceof StubBase) {
        deserializeStubList((StubBase<?>)stub, serializer, inputStream, storage, serializerLocalEnumerator);
      } else {
        deserializeChildren(inputStream, stub, serializerLocalEnumerator);
      }
      return stub;
    }
    finally {
      ourRootStubSerializer.set(null);
    }
  }

  private void serializeRoot(StubOutputStream out,
                             Stub root,
                             AbstractStringEnumerator storage,
                             IntEnumerator serializerLocalEnumerator) throws IOException {
    serializeSelf(root, out, serializerLocalEnumerator);
    if (root instanceof StubBase) {
      StubList stubList = ((StubBase<?>)root).myStubList;
      if (root != stubList.get(0)) {
        throw new IllegalArgumentException("Serialization is supported only for root stubs");
      }
      serializeStubList(stubList, out, storage, serializerLocalEnumerator);
    } else {
      serializeChildren(root, out, serializerLocalEnumerator);
    }
  }

  private void deserializeStubList(StubBase<?> root,
                                   ObjectStubSerializer<?, Stub> rootType,
                                   StubInputStream inputStream,
                                   FileLocalStringEnumerator storage,
                                   IntEnumerator serializerLocalEnumerator)
    throws IOException, SerializerNotFoundException {
    int stubCount = DataInputOutputUtil.readINT(inputStream);
    LazyStubList stubList = new LazyStubList(stubCount, root, rootType);

    MostlyUShortIntList parentsAndStarts = new MostlyUShortIntList(stubCount * 2);
    BitSet allStarts = new BitSet();

    new Object() {
      int currentIndex = 1;
      private void deserializeStub(int parentIndex) throws IOException, SerializerNotFoundException {
        int index = currentIndex;
        currentIndex++;

        int serializerId = DataInputOutputUtil.readINT(inputStream);
        int start = DataInputOutputUtil.readINT(inputStream);

        allStarts.set(start);

        addStub(parentIndex, index, start, (IElementType)getClassById(serializerId, null, serializerLocalEnumerator));
        deserializeChildren(index);
      }

      private void addStub(int parentIndex, int index, int start, IElementType type) {
        parentsAndStarts.add(parentIndex);
        parentsAndStarts.add(start);
        stubList.addLazyStub(type, index, parentIndex);
      }

      private void deserializeChildren(int parentIndex) throws IOException, SerializerNotFoundException {
        int childrenCount = DataInputOutputUtil.readINT(inputStream);
        stubList.prepareForChildren(parentIndex, childrenCount);
        for (int i = 0; i < childrenCount; i++) {
          deserializeStub(parentIndex);
        }
      }

      void deserializeRoot() throws IOException, SerializerNotFoundException {
        addStub(0, 0, 0, (IElementType)rootType);
        deserializeChildren(0);
      }
    }.deserializeRoot();
    byte[] serializedStubs = readByteArray(inputStream);
    stubList.setStubData(new LazyStubData(storage, parentsAndStarts, serializedStubs, allStarts));
  }

  private void serializeStubList(StubList stubList,
                                 DataOutput out,
                                 AbstractStringEnumerator storage,
                                 IntEnumerator serializerLocalEnumerator) throws IOException {
    if (!stubList.isChildrenLayoutOptimal()) {
      throw new IllegalArgumentException("Manually assembled stubs should be normalized before serialization, consider wrapping them into StubTree");
    }

    DataInputOutputUtil.writeINT(out, stubList.size());
    DataInputOutputUtil.writeINT(out, stubList.getChildrenCount(0));

    BufferExposingByteArrayOutputStream tempBuffer = new BufferExposingByteArrayOutputStream();
    ByteArrayInterner interner = new ByteArrayInterner();

    for (int i = 1; i < stubList.size(); i++) {
      StubBase<?> stub = stubList.get(i);
      ObjectStubSerializer<Stub, Stub> serializer = writeSerializerId(stub, out, serializerLocalEnumerator);
      DataInputOutputUtil.writeINT(out, interner.internBytes(serializeStub(serializer, storage, stub, tempBuffer)));
      DataInputOutputUtil.writeINT(out, stubList.getChildrenCount(stub.id));
    }

    writeByteArray(out, interner.joinedBuffer.getInternalBuffer(), interner.joinedBuffer.size());
  }

  private static byte[] serializeStub(ObjectStubSerializer<Stub, Stub> serializer,
                                      AbstractStringEnumerator storage,
                                      StubBase<?> stub, BufferExposingByteArrayOutputStream tempBuffer) throws IOException {
    tempBuffer.reset();
    StubOutputStream stubOut = new StubOutputStream(tempBuffer, storage);
    serializer.serialize(stub, stubOut);
    if (stub.isDangling()) {
      stubOut.writeByte(0);
    }
    return tempBuffer.size() == 0 ? ArrayUtilRt.EMPTY_BYTE_ARRAY : tempBuffer.toByteArray();
  }

  private byte[] readByteArray(StubInputStream inputStream) throws IOException {
    int length = DataInputOutputUtil.readINT(inputStream);
    if (length == 0) return ArrayUtilRt.EMPTY_BYTE_ARRAY;

    byte[] array = new byte[length];
    int read = inputStream.read(array);
    if (read != array.length) {
      Logger.getInstance(getClass()).error("Serialized array length mismatch");
    }
    return array;
  }

  private static void writeByteArray(DataOutput out, byte[] array, int len) throws IOException {
    DataInputOutputUtil.writeINT(out, len);
    out.write(array, 0, len);
  }

  @Nullable
  @Contract("null -> null")
  String intern(@Nullable String str) {
    return myStringInterner.get(str);
  }

  void reSerializeStub(@NotNull DataInputStream inStub,
                       @NotNull DataOutputStream outStub,
                       @NotNull StubSerializationHelper newSerializationHelper) throws IOException {
    IntEnumerator currentSerializerEnumerator = IntEnumerator.read(inStub);
    currentSerializerEnumerator.dump(outStub, id -> {
      String name = myIdToName.get(id);
      return name == null ? 0 : newSerializationHelper.myNameToId.getInt(name);
    });
    StreamUtil.copyStreamContent(inStub, outStub);
  }

  private ObjectStubSerializer<?, Stub> getClassById(int localId, @Nullable Stub parentStub, IntEnumerator enumerator) throws SerializerNotFoundException {
    int id = enumerator.valueOf(localId);
    ObjectStubSerializer<?, Stub> serializer = myIdToSerializer.get(id);
    if (serializer == null) {
      serializer = instantiateSerializer(id, parentStub);
      myIdToSerializer.put(id, serializer);
    }
    return serializer;
  }

  private @NotNull ObjectStubSerializer<?, Stub> instantiateSerializer(int id, @Nullable Stub parentStub) throws SerializerNotFoundException {
    String name = myIdToName.get(id);
    Supplier<ObjectStubSerializer<?, Stub>> lazy = name == null ? null : myNameToLazySerializer.get(name);
    ObjectStubSerializer<?, Stub> serializer = lazy == null ? null : lazy.get();
    if (serializer == null) {
      throw reportMissingSerializer(id, parentStub);
    }
    return serializer;
  }

  private SerializerNotFoundException reportMissingSerializer(int id, @Nullable Stub parentStub) {
    String externalId = null;
    try {
      externalId = myNameStorage.valueOf(id);
    } catch (Throwable ignore) {}
    return new SerializerNotFoundException(
      StubSerializationUtil.brokenStubFormat(ourRootStubSerializer.get()) +
      "Internal details, no serializer registered for stub: ID=" + id + ", externalId:" + externalId +
      "; parent stub class=" + (parentStub != null? parentStub.getClass().getName() +", parent stub type:" + parentStub.getStubType() : "null"));
  }

  private void deserializeChildren(StubInputStream stream,
                                   Stub parent,
                                   IntEnumerator serializerLocalEnumerator) throws IOException, SerializerNotFoundException {
    int childCount = DataInputOutputUtil.readINT(stream);
    for (int i = 0; i < childCount; i++) {
      boolean dangling = false;
      int id = DataInputOutputUtil.readINT(stream);
      if (id == 0) {
        dangling = true;
        id = DataInputOutputUtil.readINT(stream);
      }

      Stub child = getClassById(id, parent, serializerLocalEnumerator).deserialize(stream, parent);
      if (dangling) {
        ((ObjectStubBase<?>) child).markDangling();
      }
      deserializeChildren(stream, child, serializerLocalEnumerator);
    }
  }
}
