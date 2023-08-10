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
package org.jetbrains.jps.builders.impl.storage;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.builders.storage.StorageProvider;
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService;
import org.jetbrains.jps.incremental.storage.BuildDataManager;
import org.jetbrains.jps.incremental.storage.CompositeStorageOwner;
import org.jetbrains.jps.incremental.storage.StorageOwner;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class BuildTargetStorages extends CompositeStorageOwner {
  private static final Logger LOG = Logger.getInstance(BuildTargetStorages.class);
  private final BuildTarget<?> myTarget;
  private final BuildDataPaths myPaths;
  private final ConcurrentMap<StorageProvider<? extends StorageOwner>, StorageOwner> myStorages = new ConcurrentHashMap<>(16, 0.75f,
                                                                                                                          BuildDataManager.getConcurrencyLevel());

  public BuildTargetStorages(BuildTarget<?> target, BuildDataPaths paths) {
    myTarget = target;
    myPaths = paths;
  }

  @NotNull 
  public <S extends StorageOwner> S getOrCreateStorage(@NotNull final StorageProvider<S> provider, PathRelativizerService relativizer) throws IOException {
    try {
      return (S)myStorages.computeIfAbsent(provider, _provider -> {
        try {
          return _provider.createStorage(myPaths.getTargetDataRoot(myTarget), relativizer);
        }
        catch (IOException e) {
          throw new BuildDataCorruptedException(e);
        }
      });
    }
    catch (BuildDataCorruptedException e) {
      LOG.info(e);
      throw e.getCause();
    }
  } 

  public void close(@NotNull final StorageProvider<? extends StorageOwner> provider) throws IOException {
    final StorageOwner storage = myStorages.remove(provider);
    if (storage != null) {
      storage.close();
    }
  }

  @Override
  protected Iterable<StorageOwner> getChildStorages() {
    return () -> myStorages.values().iterator();
  }
}
