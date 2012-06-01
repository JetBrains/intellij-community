/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.LogUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.util.io.AbstractStringEnumerator;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.PersistentStringEnumerator;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/*
 * @author max
 */
public class SerializationManagerImpl extends SerializationManagerEx implements ApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.stubs.SerializationManagerImpl");

  private final AtomicBoolean myNameStorageCrashed = new AtomicBoolean(false);
  private final File myFile = new File(PathManager.getIndexRoot(), "rep.names");
  private final AtomicBoolean myShutdownPerformed = new AtomicBoolean(false);
  private AbstractStringEnumerator myNameStorage;
  private final List<StubSerializer<? extends StubElement>> myAllSerializers = new ArrayList<StubSerializer<? extends StubElement>>();
  private StubSerializationHelper myStubSerializationHelper;
  private volatile boolean mySerializersLoaded = false;

  public SerializationManagerImpl() {
    myFile.getParentFile().mkdirs();
    try {
      // we need to cache last id -> String mappings due to StringRefs and stubs indexing that initially creates stubs (doing enumerate on String)
      // and then index them (valueOf), also similar string items are expected to be enumerated during stubs processing
      myNameStorage = new PersistentStringEnumerator(myFile, true);
      myStubSerializationHelper = new StubSerializationHelper(myNameStorage);
    }
    catch (IOException e) {
      nameStorageCrashed();
      LOG.info(e);
      repairNameStorage(); // need this in order for myNameStorage not to be null
      nameStorageCrashed();
    }
    finally {
      registerSerializer(PsiFileStubImpl.TYPE);
      ShutDownTracker.getInstance().registerShutdownTask(new Runnable() {
        public void run() {
          performShutdown();
        }
      });
    }
  }

  public boolean isNameStorageCorrupted() {
    return myNameStorageCrashed.get();
  }

  public void repairNameStorage() {
    if (myNameStorageCrashed.getAndSet(false)) {
      try {
        if (myNameStorage != null) {
          myNameStorage.close();
        }

        final File[] files = myFile.getParentFile().listFiles();
        if (files != null) {
          for (File file : files) {
            if (file.getName().startsWith(myFile.getName())) {
              FileUtil.delete(file);
            }
          }
        }
        myNameStorage = new PersistentStringEnumerator(myFile, true);
        myStubSerializationHelper = new StubSerializationHelper(myNameStorage);
        for (StubSerializer<? extends StubElement> serializer : myAllSerializers) {
          myStubSerializationHelper.assignId(serializer);
        }
      }
      catch (IOException e) {
        LOG.info(e);
        nameStorageCrashed();
      }
    }
  }

  @Override
  public void flushNameStorage() {
    myNameStorage.force();
  }

  protected void nameStorageCrashed() {
    myNameStorageCrashed.set(true);
  }

  @NotNull
  public String getComponentName() {
    return "PSI.SerializationManager";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
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

  public void registerSerializer(@NotNull StubSerializer<? extends StubElement> serializer) {
    myAllSerializers.add(serializer);
    try {
      myStubSerializationHelper.assignId(serializer);
    }
    catch (IOException e) {
      LOG.info(e);
      nameStorageCrashed();
    }
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
    try {
      myStubSerializationHelper.serialize(rootStub, stream);
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
    if (!mySerializersLoaded) initSerializers();

    try {
      return myStubSerializationHelper.deserialize(stream);
    }
    catch (IOException e) {
      nameStorageCrashed();
      LOG.info(e);
      throw new RuntimeException(e);
    }
  }
}
