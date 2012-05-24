/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.stubs;

import com.intellij.openapi.diagnostic.LogUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.util.io.AbstractStringEnumerator;
import com.intellij.util.io.DataInputOutputUtil;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Author: dmitrylomov
 */
public abstract class SerializationManagerBase extends SerializationManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.stubs.SerializationManagerBase");

  protected AbstractStringEnumerator myNameStorage;
  protected final TIntObjectHashMap<StubSerializer<? extends StubElement>> myIdToSerializer = new TIntObjectHashMap<StubSerializer<? extends StubElement>>();
  protected final TObjectIntHashMap<StubSerializer<? extends StubElement>> mySerializerToId = new TObjectIntHashMap<StubSerializer<? extends StubElement>>();
  protected final List<StubSerializer<? extends StubElement>> myAllSerializers = new ArrayList<StubSerializer<? extends StubElement>>();
  private volatile boolean mySerializersLoaded = false;

  public abstract boolean isNameStorageCorrupted();

  public abstract void repairNameStorage();

  public void registerSerializer(@NotNull StubSerializer<? extends StubElement> serializer) {
    myAllSerializers.add(serializer);
    try {
      assignId(serializer);
    }
    catch (IOException e) {
      LOG.info(e);
      nameStorageCrashed();
    }
  }

  protected void assignId(@NotNull final StubSerializer<? extends StubElement> serializer) throws IOException {
    final int id = persistentId(serializer);
    final StubSerializer old = myIdToSerializer.put(id, serializer);
    assert old == null : "ID: " + serializer.getExternalId() + " is not unique; Already registered serializer with this ID: " + old.getClass().getName();

    final int oldId = mySerializerToId.put(serializer, id);
    assert oldId == 0 : "Serializer " + serializer + " is already registered; Old ID:" + oldId;
  }

  private int persistentId(@NotNull final StubSerializer<? extends StubElement> serializer) throws IOException {
    if (myNameStorage == null) {
      throw new IOException("SerializationManager's name storage failed to initialize");
    }
    return myNameStorage.enumerate(serializer.getExternalId());
  }

  private synchronized void initSerializers() {
    if (mySerializersLoaded) return;
    for(StubElementTypeHolderEP holderEP: Extensions.getExtensions(StubElementTypeHolderEP.EP_NAME)) {
      holderEP.initialize();
    }
    final IElementType[] stubElementTypes = IElementType.enumerate(new IElementType.Predicate() {
      public boolean matches(final IElementType type) {
        return type instanceof StubSerializer;
      }
    });
    for(IElementType type: stubElementTypes) {
      if (type instanceof IStubFileElementType && ((IStubFileElementType) type).getExternalId().equals(PsiFileStubImpl.TYPE.getExternalId())) {
        continue;
      }
      StubSerializer stubSerializer = (StubSerializer) type;

      if (!myAllSerializers.contains(stubSerializer)) {
        registerSerializer(stubSerializer);
      }
    }
    mySerializersLoaded = true;
  }

  public void serialize(StubElement rootStub, OutputStream stream) {
    if (!mySerializersLoaded) initSerializers();
    StubOutputStream stubOutputStream = new StubOutputStream(stream, myNameStorage);
    doSerialize(rootStub, stubOutputStream);
  }

  private void doSerialize(final StubElement rootStub, final StubOutputStream stream) {
    try {
      final StubSerializer serializer = getSerializer(rootStub);

      DataInputOutputUtil.writeINT(stream, getClassId(serializer));
      serializer.serialize(rootStub, stream);

      final List<StubElement> children = rootStub.getChildrenStubs();
      final int childrenSize = children.size();
      DataInputOutputUtil.writeINT(stream, childrenSize);
      for (int i = 0; i < childrenSize; ++i) {
        doSerialize(children.get(i), stream);
      }
    }
    catch (IOException e) {
      LOG.info(e);
      nameStorageCrashed();
    }
  }

  public StubSerializer getSerializer(final StubElement rootStub) {
    if (rootStub instanceof PsiFileStub) {
      final PsiFileStub fileStub = (PsiFileStub)rootStub;
      return fileStub.getType();
    }

    return rootStub.getStubType();
  }

  public StubElement deserialize(InputStream stream) {
    StubInputStream inputStream = new StubInputStream(stream, myNameStorage);
    if (!mySerializersLoaded) initSerializers();
    try {
      return deserialize(inputStream, null);
    }
    catch (IOException e) {
      nameStorageCrashed();
      LOG.info(e);
      throw new RuntimeException(e);
    }
  }

  protected abstract void nameStorageCrashed();

  private StubElement deserialize(StubInputStream stream, StubElement parentStub) throws IOException {
    final int id = DataInputOutputUtil.readINT(stream);
    final StubSerializer serializer = getClassById(id);

    assert serializer != null : "No serializer registered for stub: ID=" + id + "; parent stub class=" + (parentStub != null? parentStub.getClass().getName() : "null");

    StubElement stub = serializer.deserialize(stream, parentStub);
    int childCount = DataInputOutputUtil.readINT(stream);
    for (int i = 0; i < childCount; i++) {
      deserialize(stream, stub);
    }
    return stub;
  }

  private int getClassId(final StubSerializer serializer) {
    final int idValue = mySerializerToId.get(serializer);
    assert idValue != 0: "No ID found for serializer " + LogUtil.objectAndClass(serializer);
    return idValue;
  }

  private StubSerializer getClassById(int id) {
    return myIdToSerializer.get(id);
  }
}
