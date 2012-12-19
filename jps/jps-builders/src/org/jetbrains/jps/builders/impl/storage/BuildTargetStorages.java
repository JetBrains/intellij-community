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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.builders.storage.StorageProvider;
import org.jetbrains.jps.incremental.storage.CompositeStorageOwner;
import org.jetbrains.jps.incremental.storage.StorageOwner;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author nik
 */
public class BuildTargetStorages extends CompositeStorageOwner {
  private final BuildTarget<?> myTarget;
  private final BuildDataPaths myPaths;
  private Map<StorageProvider<?>, StorageOwner> myStorages = new HashMap<StorageProvider<?>, StorageOwner>();

  public BuildTargetStorages(BuildTarget<?> target, BuildDataPaths paths) {
    myTarget = target;
    myPaths = paths;
  }

  @NotNull 
  public <S extends StorageOwner> S getOrCreateStorage(@NotNull StorageProvider<S> provider) throws IOException {
    //noinspection unchecked
    S storage = (S)myStorages.get(provider);
    if (storage == null) {
      storage = provider.createStorage(myPaths.getTargetDataRoot(myTarget));
      myStorages.put(provider, storage);
    }
    return (S)storage;
  } 
  
  @Override
  protected Iterable<? extends StorageOwner> getChildStorages() {
    return myStorages.values();
  }
}
