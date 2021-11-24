// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.stubs;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
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

// todo rewrite: it's an app service for now but its lifecycle should be synchronized with stub index.
@ApiStatus.Internal
public final class SerializationManagerImpl extends SerializationManagerEx implements Disposable {
  private static final Logger LOG = Logger.getInstance(SerializationManagerImpl.class);

  private final AtomicBoolean myNameStorageCrashed = new AtomicBoolean();
  private final @NotNull Supplier<Path> myFile;
  private final boolean myUnmodifiable;
  private final AtomicBoolean myInitialized = new AtomicBoolean();

  private volatile Path myOpenFile;
  private volatile StubSerializationHelper myStubSerializationHelper;
  private volatile StubSerializerEnumerator mySerializerEnumerator;
  private volatile boolean mySerializersLoaded;

  public SerializationManagerImpl() {
    this(() -> FileBasedIndex.USE_IN_MEMORY_INDEX ? null : PathManager.getIndexRoot().resolve("rep.names"), false);
  }

  @NonInjectable
  public SerializationManagerImpl(@NotNull Path nameStorageFile, boolean unmodifiable) {
    this(() -> nameStorageFile, unmodifiable);
  }

  @NonInjectable
  public SerializationManagerImpl(@NotNull Supplier<Path> nameStorageFile, boolean unmodifiable) {
    myFile = nameStorageFile;
    myUnmodifiable = unmodifiable;
    try {
      initialize();
    }
    finally {
      if (!unmodifiable) {
        ShutDownTracker.getInstance().registerShutdownTask(this::performShutdown, this);
      }
    }

    StubElementTypeHolderEP.EP_NAME.addChangeListener(this::dropSerializerData, this);
  }

  @Override
  public void initialize() {
    if (myInitialized.get()) return;
    doInitialize();
  }

  private void doInitialize() {
    try {
      // we need to cache last id -> String mappings due to StringRefs and stubs indexing that initially creates stubs (doing enumerate on String)
      // and then index them (valueOf), also similar string items are expected to be enumerated during stubs processing
      StubSerializerEnumerator enumerator = new StubSerializerEnumerator(openNameStorage(), myUnmodifiable);
      mySerializerEnumerator = enumerator;
      myStubSerializationHelper = new StubSerializationHelper(enumerator);
    }
    catch (IOException e) {
      nameStorageCrashed();
      LOG.info(e);
    }
    finally {
      myInitialized.set(true);
    }
  }

  @NotNull
  private DataEnumeratorEx<String> openNameStorage() throws IOException {
    myOpenFile = myFile.get();
    if (myOpenFile == null) {
      return new InMemoryDataEnumerator<>();
    }
    Boolean lastValue = null;
    if (myUnmodifiable) {
      lastValue = PersistentHashMapValueStorage.CreationTimeOptions.READONLY.get();
      PersistentHashMapValueStorage.CreationTimeOptions.READONLY.set(Boolean.TRUE);
    }
    try {
      return new PersistentStringEnumerator(myOpenFile, true);
    }
    finally {
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
      if (myUnmodifiable) {
        LOG.error("Data provided by unmodifiable serialization manager can be invalid after repair");
      }
      LOG.info("Name storage is repaired");

      StubSerializerEnumerator enumerator = mySerializerEnumerator;
      if (enumerator != null) {
        try {
          enumerator.close();
        }
        catch (Exception ignored) {}
      }
      if (myOpenFile != null) {
        IOUtil.deleteAllFilesStartingWith(myOpenFile.toFile());
      }
      doInitialize();
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

  @Override
  public void performShutdown() {
    if (!myInitialized.compareAndSet(true, false)) {
      return; // already shut down
    }
    String name = myOpenFile != null ? myOpenFile.toString() : "in-memory storage";
    if (!myUnmodifiable) {
      LOG.info("Start shutting down " + name);
    }
    try {
      mySerializerEnumerator.close();
      if (!myUnmodifiable) {
        LOG.info("Finished shutting down " + name);
      }
    }
    catch (IOException e) {
      nameStorageCrashed();
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
