// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.stubs;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.StubFileElementType;
import com.intellij.util.io.IOUtil;
import com.intellij.util.io.PersistentStringEnumerator;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SerializationManagerImpl extends SerializationManagerEx implements Disposable {
  private static final Logger LOG = Logger.getInstance(SerializationManagerImpl.class);

  private final AtomicBoolean myNameStorageCrashed = new AtomicBoolean(false);
  private final Path myFile;
  private final boolean myUnmodifiable;
  private final AtomicBoolean myShutdownPerformed = new AtomicBoolean(false);
  private PersistentStringEnumerator myNameStorage;
  private volatile StubSerializationHelper myStubSerializationHelper;

  private volatile boolean mySerializersLoaded;

  @SuppressWarnings("unused") // used from componentSets/Lang.xml:14
  public SerializationManagerImpl() {
    this(new File(PathManager.getIndexRoot(), "rep.names").toPath(), false);
  }

  public SerializationManagerImpl(@NotNull Path nameStorageFile, boolean unmodifiable) {
    myFile = nameStorageFile;
    myUnmodifiable = unmodifiable;
    try {
      // we need to cache last id -> String mappings due to StringRefs and stubs indexing that initially creates stubs (doing enumerate on String)
      // and then index them (valueOf), also similar string items are expected to be enumerated during stubs processing
      myNameStorage = new PersistentStringEnumerator(myFile, true);
      myStubSerializationHelper = new StubSerializationHelper(myNameStorage, unmodifiable, this);
    }
    catch (IOException e) {
      nameStorageCrashed();
      LOG.info(e);
      repairNameStorage(); // need this in order for myNameStorage not to be null
      nameStorageCrashed();
    }
    finally {
      ShutDownTracker.getInstance().registerShutdownTask(this::performShutdown);
    }

    StubElementTypeHolderEP.EP_NAME.addExtensionPointListener(this::dropSerializerData, this);
  }

  @Override
  public boolean isNameStorageCorrupted() {
    return myNameStorageCrashed.get();
  }

  @Override
  public void repairNameStorage() {
    if (myNameStorageCrashed.getAndSet(false)) {
      try {
        LOG.info("Name storage is repaired");
        if (myNameStorage != null) {
          myNameStorage.close();
        }

        StubSerializationHelper prevHelper = myStubSerializationHelper;
        if (myUnmodifiable) {
          LOG.error("Data provided by unmodifiable serialization manager can be invalid after repair");
        }

        IOUtil.deleteAllFilesStartingWith(myFile.toFile());
        myNameStorage = new PersistentStringEnumerator(myFile, true);
        myStubSerializationHelper = new StubSerializationHelper(myNameStorage, myUnmodifiable, this);
        myStubSerializationHelper.copyFrom(prevHelper);
      }
      catch (IOException e) {
        LOG.info(e);
        nameStorageCrashed();
      }
    }
  }

  @Override
  public void flushNameStorage() {
    if (myNameStorage.isDirty()) {
      myNameStorage.force();
    }
  }

  @Override
  public String internString(String string) {
    return myStubSerializationHelper.intern(string);
  }

  @Override
  public void reinitializeNameStorage() {
    nameStorageCrashed();
    repairNameStorage();
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
    LOG.info("START StubSerializationManager SHUTDOWN");
    try {
      myNameStorage.close();
      LOG.info("END StubSerializationManager SHUTDOWN");
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  @Override
  protected void registerSerializer(String externalId, Computable<ObjectStubSerializer> lazySerializer) {
    try {
      myStubSerializationHelper.assignId(lazySerializer, externalId);
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
                          @NotNull SerializationManagerEx newSerializationManager) throws IOException {
    initSerializers();
    newSerializationManager.initSerializers();
    myStubSerializationHelper.reSerializeStub(new DataInputStream(inStub),
                                              new DataOutputStream(outStub),
                                              ((SerializationManagerImpl)newSerializationManager).myStubSerializationHelper);
  }

  @Override
  protected void initSerializers() {
    //noinspection SynchronizeOnThis
    synchronized (this) {
      if (mySerializersLoaded) return;
      registerSerializer(PsiFileStubImpl.TYPE);
      List<StubFieldAccessor> lazySerializers = IStubElementType.loadRegisteredStubElementTypes();
      final IElementType[] stubElementTypes = IElementType.enumerate(type -> type instanceof StubSerializer);
      for (IElementType type : stubElementTypes) {
        if (type instanceof StubFileElementType &&
            StubFileElementType.DEFAULT_EXTERNAL_ID.equals(((StubFileElementType)type).getExternalId())) {
          continue;
        }

        registerSerializer((StubSerializer)type);
      }
      for (StubFieldAccessor lazySerializer : lazySerializers) {
        registerSerializer(lazySerializer.externalId, lazySerializer);
      }
      mySerializersLoaded = true;
    }
  }

  private void dropSerializerData() {
    //noinspection SynchronizeOnThis
    synchronized (this) {
      IStubElementType.dropRegisteredTypes();
      myStubSerializationHelper.dropRegisteredSerializers();
      mySerializersLoaded = false;
    }
  }
}
