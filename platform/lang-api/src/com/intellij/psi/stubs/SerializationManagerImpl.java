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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.PersistentStringEnumerator;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/*
 * @author max
 */
public class SerializationManagerImpl extends SerializationManagerBase implements ApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.stubs.SerializationManagerImpl");

  private final AtomicBoolean myNameStorageCrashed = new AtomicBoolean(false);
  private final File myFile = new File(PathManager.getIndexRoot(), "rep.names");
  private final AtomicBoolean myShutdownPerformed = new AtomicBoolean(false);

  public SerializationManagerImpl() {
    myFile.getParentFile().mkdirs();
    try {
      // we need to cache last id -> String mappings due to StringRefs and stubs indexing that initially creates stubs (doing enumerate on String)
      // and then index them (valueOf), also similar string items are expected to be enumerated during stubs processing
      myNameStorage = new PersistentStringEnumerator(myFile, true);
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

  @Override
  public boolean isNameStorageCorrupted() {
    return myNameStorageCrashed.get();
  }

  @Override
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
        mySerializerToId.clear();
        myIdToSerializer.clear();
        for (StubSerializer<? extends StubElement> serializer : myAllSerializers) {
          assignId(serializer);
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

  @Override
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
}
