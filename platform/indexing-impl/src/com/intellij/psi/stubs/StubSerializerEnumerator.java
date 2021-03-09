// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.stubs;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.openapi.Forceable;
import com.intellij.openapi.diagnostic.LogUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ConcurrentIntObjectMap;
import com.intellij.util.io.DataEnumeratorEx;
import com.intellij.util.io.PersistentStringEnumerator;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

final class StubSerializerEnumerator implements Flushable, Closeable {
  private static final Logger LOG = Logger.getInstance(StubSerializerEnumerator.class);

  private final DataEnumeratorEx<String> myNameStorage;

  private final Int2ObjectMap<String> myIdToName = new Int2ObjectOpenHashMap<>();
  private final Object2IntMap<String> myNameToId = new Object2IntOpenHashMap<>();
  private final Map<String, Supplier<ObjectStubSerializer<?, ? extends Stub>>> myNameToLazySerializer = CollectionFactory.createSmallMemoryFootprintMap();

  private final ConcurrentIntObjectMap<ObjectStubSerializer<?, ? extends Stub>> myIdToSerializer =
    ConcurrentCollectionFactory.createConcurrentIntObjectMap();
  private final Map<ObjectStubSerializer<?, ? extends Stub>, Integer> mySerializerToId = new ConcurrentHashMap<>();

  private final boolean myUnmodifiable;

  StubSerializerEnumerator(@NotNull DataEnumeratorEx<String> nameStorage, boolean unmodifiable) {
    myNameStorage = nameStorage;
    myUnmodifiable = unmodifiable;
  }

  void dropRegisteredSerializers() {
    myIdToName.clear();
    myNameToId.clear();
    myNameToLazySerializer.clear();

    myIdToSerializer.clear();
    mySerializerToId.clear();
  }

  @NotNull ObjectStubSerializer<?, Stub> getClassById(@NotNull MissingSerializerReporter reporter, int id) throws SerializerNotFoundException {
    ObjectStubSerializer<?, ? extends Stub> serializer = myIdToSerializer.get(id);
    if (serializer == null) {
      serializer = instantiateSerializer(id, reporter);
      myIdToSerializer.put(id, serializer);
    }
    //noinspection unchecked
    return (ObjectStubSerializer<?, Stub>)serializer;
  }

  int getClassId(final @NotNull ObjectStubSerializer<?, ? extends Stub> serializer) {
    Integer idValue = mySerializerToId.get(serializer);
    if (idValue == null) {
      String name = serializer.getExternalId();
      idValue = myNameToId.getInt(name);
      assert idValue > 0 : "No ID found for serializer " + LogUtil.objectAndClass(serializer) +
                           ", external id:" + name +
                           (serializer instanceof IElementType
                            ? ", language:" + ((IElementType)serializer).getLanguage() + ", " + serializer : "");
      mySerializerToId.put(serializer, idValue);
    }
    return idValue;
  }

  void assignId(@NotNull Supplier<ObjectStubSerializer<?, ? extends Stub>> serializer, String name) throws IOException {
    Supplier<ObjectStubSerializer<?, ? extends Stub>> old = myNameToLazySerializer.put(name, serializer);
    if (old != null) {
      ObjectStubSerializer<?, ? extends Stub> existing = old.get();
      ObjectStubSerializer<?, ? extends Stub> computed = serializer.get();
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

  @Nullable
  String getSerializerName(int id) {
    return myIdToName.get(id);
  }

  int getSerializerId(@NotNull String name) {
    return myNameToId.getInt(name);
  }

  @NotNull
  ObjectStubSerializer<?, ? extends Stub> getSerializer(@NotNull String name) throws SerializerNotFoundException {
    int id = myNameToId.getInt(name);
    return getClassById((id1, name1, externalId) -> {
      return "Missed stub serializer for " + name;
    }, id);
  }

  @Nullable
  String getSerializerName(@NotNull ObjectStubSerializer<?, ? extends Stub> serializer) {
    return myIdToName.get(getClassId(serializer));
  }

  void copyFrom(@Nullable StubSerializerEnumerator helper) throws IOException {
    if (helper == null) {
      return;
    }

    for (Map.Entry<String, Supplier<ObjectStubSerializer<?, ? extends Stub>>> entry : helper.myNameToLazySerializer.entrySet()) {
      assignId(entry.getValue(), entry.getKey());
    }
  }

  private @NotNull ObjectStubSerializer<?, ? extends Stub> instantiateSerializer(int id,
                                                                                 @NotNull MissingSerializerReporter reporter) throws SerializerNotFoundException {
    String name = myIdToName.get(id);
    Supplier<ObjectStubSerializer<?, ? extends Stub>> lazy = name == null ? null : myNameToLazySerializer.get(name);
    ObjectStubSerializer<?, ? extends Stub> serializer = lazy == null ? null : lazy.get();
    if (serializer == null) {
      throw reportMissingSerializer(id, name, reporter);
    }
    return serializer;
  }

  private SerializerNotFoundException reportMissingSerializer(int id, @Nullable String name, @NotNull MissingSerializerReporter reporter) {
    String externalId = null;
    Throwable storageException = null;
    try {
      externalId = myNameStorage.valueOf(id);
    } catch (Throwable e) {
      LOG.info(e);
      storageException = e;
    }
    SerializerNotFoundException exception = new SerializerNotFoundException(reporter.report(id, name, externalId));
    StubIndex.getInstance().forceRebuild(storageException != null ? storageException : exception);
    return exception;
  }

  @Override
  public void flush() throws IOException {
    if (myNameStorage instanceof Forceable) {
      if (((Forceable)myNameStorage).isDirty()) {
        ((Forceable)myNameStorage).force();
      }
    }
  }

  @Override
  public void close() throws IOException {
    if (myNameStorage instanceof Closeable) {
      ((Closeable)myNameStorage).close();
    }
  }

  @ApiStatus.Internal
  Map<String, Integer> dump() {
    assert myUnmodifiable;
    assert myNameStorage instanceof PersistentStringEnumerator;
    try {
      Collection<String> stubNames = ((PersistentStringEnumerator)myNameStorage).getAllDataObjects(null);
      Map<String, Integer> dump = new HashMap<>();
      for (String name : stubNames) {
        dump.put(name, myNameStorage.tryEnumerate(name));
      }
      return dump;
    }
    catch (IOException e) {
      LOG.error(e);
    }
    return Collections.emptyMap();
  }

  @FunctionalInterface
  interface MissingSerializerReporter {
    @NotNull
    String report(int id, @Nullable String name, @Nullable String externalId);
  }
}
