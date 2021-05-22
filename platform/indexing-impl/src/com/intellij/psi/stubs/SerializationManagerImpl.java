// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.stubs;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.psi.tree.StubFileElementType;
import com.intellij.serviceContainer.NonInjectable;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.io.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataOutputStream;
import java.io.*;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

@ApiStatus.Internal
public final class SerializationManagerImpl extends SerializationManagerEx implements Disposable {
  private static final Logger LOG = Logger.getInstance(SerializationManagerImpl.class);

  private final AtomicBoolean myNameStorageCrashed = new AtomicBoolean(false);
  private final Path myFile;
  private final boolean myUnmodifiable;
  private final AtomicBoolean myShutdownPerformed = new AtomicBoolean(false);

  private volatile StubSerializationHelper myStubSerializationHelper;
  private volatile StubSerializerEnumerator mySerializerEnumerator;
  private volatile boolean mySerializersLoaded;

  public SerializationManagerImpl() {
    this(FileBasedIndex.USE_IN_MEMORY_INDEX ? null : PathManager.getIndexRoot().resolve("rep.names"), false);
  }

  @NonInjectable
  public SerializationManagerImpl(@Nullable Path nameStorageFile, boolean unmodifiable) {
    myFile = nameStorageFile;
    myUnmodifiable = unmodifiable;
    try {
      // we need to cache last id -> String mappings due to StringRefs and stubs indexing that initially creates stubs (doing enumerate on String)
      // and then index them (valueOf), also similar string items are expected to be enumerated during stubs processing
      mySerializerEnumerator = new StubSerializerEnumerator(openNameStorage(), unmodifiable);
      myStubSerializationHelper = new StubSerializationHelper(mySerializerEnumerator);
    }
    catch (IOException e) {
      nameStorageCrashed();
      LOG.info(e);
      repairNameStorage(e); // need this in order for myNameStorage not to be null
      nameStorageCrashed();
    }
    finally {
      ShutDownTracker.getInstance().registerShutdownTask(this::performShutdown, this);
    }

    StubElementTypeHolderEP.EP_NAME.addChangeListener(this::dropSerializerData, this);
  }

  @NotNull
  private DataEnumeratorEx<String> openNameStorage() throws IOException {
    if (myFile == null) {
      return new InMemoryDataEnumerator<>();
    }
    Boolean lastValue = null;
    if (myUnmodifiable) {
      lastValue = PersistentHashMapValueStorage.CreationTimeOptions.READONLY.get();
      PersistentHashMapValueStorage.CreationTimeOptions.READONLY.set(Boolean.TRUE);
    }
    try {
      return new PersistentStringEnumerator(myFile, true);
    } finally {
      if (myUnmodifiable) {
        PersistentHashMapValueStorage.CreationTimeOptions.READONLY.set(lastValue);
      }
    }
  }

  @ApiStatus.Internal
  public Map<String, Integer> dumpNameStorage() {
    return mySerializerEnumerator.dump();
  }

  @Override
  public boolean isNameStorageCorrupted() {
    return myNameStorageCrashed.get();
  }

  @Override
  public void repairNameStorage(@NotNull Exception corruptionCause) {
    if (myNameStorageCrashed.getAndSet(false)) {
      try {
        LOG.info("Name storage is repaired");
        mySerializerEnumerator.close();

        StubSerializerEnumerator prevEnum = mySerializerEnumerator;
        if (myUnmodifiable) {
          LOG.error("Data provided by unmodifiable serialization manager can be invalid after repair");
        }

        if (myFile != null) {
          IOUtil.deleteAllFilesStartingWith(myFile.toFile());
        }
        mySerializerEnumerator = new StubSerializerEnumerator(openNameStorage(), myUnmodifiable);
        mySerializerEnumerator.copyFrom(prevEnum);
        myStubSerializationHelper = new StubSerializationHelper(mySerializerEnumerator);
      }
      catch (IOException e) {
        LOG.info(e);
        nameStorageCrashed();
      }
    }
  }

  @Override
  public void flushNameStorage() throws IOException {
    mySerializerEnumerator.flush();
  }

  private void registerSerializer(ObjectStubSerializer<?, ? extends Stub> serializer) {
    registerSerializer(serializer.getExternalId(), () -> serializer);
  }

  @Override
  public void reinitializeNameStorage() {
    nameStorageCrashed();
    repairNameStorage(new Exception("Indexes are requested to rebuild"));
  }

  private void nameStorageCrashed() {
    myNameStorageCrashed.set(true);
  }

  @Override
  public void dispose() {
    performShutdown();
  }

  private void performShutdown() {
    if (!myShutdownPerformed.compareAndSet(false, true)) {
      return; // already shut down
    }
    String name = myFile != null ? myFile.toString() : "in-memory storage";
    LOG.info("Start shutting down " + name);
    try {
      mySerializerEnumerator.close();
      LOG.info("Finished shutting down " + name);
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  private void registerSerializer(@NotNull String externalId, @NotNull Supplier<ObjectStubSerializer<?, ? extends Stub>> lazySerializer) {
    try {
      mySerializerEnumerator.assignId(lazySerializer, externalId);
    }
    catch (IOException e) {
      LOG.info(e);
      nameStorageCrashed();
    }
  }

  @Override
  public void serialize(@NotNull Stub rootStub, @NotNull OutputStream stream) {
    initSerializers();
    try {
      myStubSerializationHelper.serialize(rootStub, stream);
    }
    catch (IOException e) {
      LOG.info(e);
      nameStorageCrashed();
    }
  }

  @NotNull
  @Override
  public Stub deserialize(@NotNull InputStream stream) throws SerializerNotFoundException {
    initSerializers();

    try {
      return myStubSerializationHelper.deserialize(stream);
    }
    catch (IOException e) {
      nameStorageCrashed();
      LOG.info(e);
      throw new RuntimeException(e);
    }
  }

  @Override
  public void reSerialize(@NotNull InputStream inStub,
                          @NotNull OutputStream outStub,
                          @NotNull StubTreeSerializer newSerializationManager) throws IOException {
    initSerializers();
    ((SerializationManagerEx)newSerializationManager).initSerializers();
    myStubSerializationHelper.reSerializeStub(new DataInputStream(inStub),
                                              new DataOutputStream(outStub),
                                              ((SerializationManagerImpl)newSerializationManager).myStubSerializationHelper);
  }

  @Override
  protected void initSerializers() {
    //noinspection SynchronizeOnThis
    synchronized (this) {
      if (mySerializersLoaded) {
        return;
      }

      registerSerializer(PsiFileStubImpl.TYPE);
      List<StubFieldAccessor> lazySerializers = IStubElementType.loadRegisteredStubElementTypes();
      final IElementType[] stubElementTypes = IElementType.enumerate(type -> type instanceof StubSerializer);
      for (IElementType type : stubElementTypes) {
        if (type instanceof StubFileElementType &&
            StubFileElementType.DEFAULT_EXTERNAL_ID.equals(((StubFileElementType<?>)type).getExternalId())) {
          continue;
        }

        registerSerializer((StubSerializer<?>)type);
      }
      for (StubFieldAccessor lazySerializer : lazySerializers) {
        registerSerializer(lazySerializer.externalId, lazySerializer);
      }
      mySerializersLoaded = true;
    }
  }

  @NotNull ObjectStubSerializer<?, ? extends Stub> getSerializer(@NotNull String name) throws SerializerNotFoundException {
    return mySerializerEnumerator.getSerializer(name);
  }

  @Nullable
  public String getSerializerName(@NotNull ObjectStubSerializer<?, ? extends Stub> serializer) {
    return mySerializerEnumerator.getSerializerName(serializer);
  }

  public void dropSerializerData() {
    //noinspection SynchronizeOnThis
    synchronized (this) {
      IStubElementType.dropRegisteredTypes();
      IStubFileElementType.dropTemplateStubBaseVersion();
      StubSerializerEnumerator enumerator = mySerializerEnumerator;
      if (enumerator != null) {
        enumerator.dropRegisteredSerializers();
      }
      else {
        // has been corrupted previously
        nameStorageCrashed();
      }
      mySerializersLoaded = false;
    }
  }
}
