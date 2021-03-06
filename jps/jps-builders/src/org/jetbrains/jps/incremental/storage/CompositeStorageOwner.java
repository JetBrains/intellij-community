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
package org.jetbrains.jps.incremental.storage;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ThrowableConsumer;

import java.io.IOException;

public abstract class CompositeStorageOwner implements StorageOwner {
  private static final Logger LOG = Logger.getInstance(CompositeStorageOwner.class);

  protected abstract Iterable<? extends StorageOwner> getChildStorages();

  @Override
  public void flush(boolean memoryCachesOnly) {
    try {
      applyBulkOperation(getChildStorages(), storageOwner -> storageOwner.flush(memoryCachesOnly));
    }
    catch (IOException ignored) { // handled 
    }
  }

  @Override
  public void clean() throws IOException {
    applyBulkOperation(getChildStorages(), StorageOwner::clean);
  }

  @Override
  public void close() throws IOException {
    applyBulkOperation(getChildStorages(), StorageOwner::close);
  }

  protected <T extends StorageOwner> void applyBulkOperation(Iterable<? extends T> storages, ThrowableConsumer<? super T, IOException> action) throws IOException{
    IOException exc = null;
    for (T child : storages) {
      if (child != null) {
        try {
          action.consume(child);
        }
        catch (IOException e) {
          LOG.info(e);
          if (exc == null) {
            exc = e;
          }
        }
        catch (Throwable e) {
          LOG.info(e);
        }
      }
    }
    if (exc != null) {
      throw exc;
    }
  }

}
