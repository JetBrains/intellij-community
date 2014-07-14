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

import com.intellij.openapi.util.AtomicNotNullLazyValue;
import com.intellij.openapi.util.NotNullLazyValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.storage.BuildDataCorruptedException;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.builders.storage.StorageProvider;
import org.jetbrains.jps.incremental.storage.CompositeStorageOwner;
import org.jetbrains.jps.incremental.storage.StorageOwner;

import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author nik
 */
public class BuildTargetStorages extends CompositeStorageOwner {
  private final BuildTarget<?> myTarget;
  private final BuildDataPaths myPaths;
  private final ConcurrentMap<StorageProvider<?>, AtomicNotNullLazyValue<? extends StorageOwner>> myStorages 
    = new ConcurrentHashMap<StorageProvider<?>, AtomicNotNullLazyValue<? extends StorageOwner>>(16, 0.75f, 1);

  public BuildTargetStorages(BuildTarget<?> target, BuildDataPaths paths) {
    myTarget = target;
    myPaths = paths;
  }

  @NotNull 
  public <S extends StorageOwner> S getOrCreateStorage(@NotNull final StorageProvider<S> provider) throws IOException {
    NotNullLazyValue<? extends StorageOwner> lazyValue = myStorages.get(provider);
    if (lazyValue == null) {
      AtomicNotNullLazyValue<S> newValue = new AtomicNotNullLazyValue<S>() {
        @NotNull
        @Override
        protected S compute() {
          try {
            return provider.createStorage(myPaths.getTargetDataRoot(myTarget));
          }
          catch (IOException e) {
            throw new BuildDataCorruptedException(e);
          }
        }
      };
      lazyValue = myStorages.putIfAbsent(provider, newValue);
      if (lazyValue == null) {
        lazyValue = newValue; // just initialized
      }
    }
    //noinspection unchecked
    try {
      return (S)lazyValue.getValue();
    }
    catch (BuildDataCorruptedException e) {
      throw e.getCause();
    }
  } 
  
  @Override
  protected Iterable<? extends StorageOwner> getChildStorages() {
    return new Iterable<StorageOwner>() {
      @Override
      public Iterator<StorageOwner> iterator() {
        final Iterator<AtomicNotNullLazyValue<? extends StorageOwner>> iterator = myStorages.values().iterator();
        return new Iterator<StorageOwner>() {
          @Override
          public boolean hasNext() {
            return iterator.hasNext();
          }

          @Override
          public StorageOwner next() {
            return iterator.next().getValue();
          }

          @Override
          public void remove() {
            iterator.remove();
          }
        };
      }
    };
  }
}
