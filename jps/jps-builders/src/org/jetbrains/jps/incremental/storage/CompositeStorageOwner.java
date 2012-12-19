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

import java.io.IOException;

/**
 * @author nik
 */
public abstract class CompositeStorageOwner implements StorageOwner {
  protected abstract Iterable<? extends StorageOwner> getChildStorages();

  @Override
  public void flush(boolean memoryCachesOnly) {
    for (StorageOwner child : getChildStorages()) {
      if (child != null) {
        child.flush(memoryCachesOnly);
      }
    }
  }

  @Override
  public void clean() throws IOException {
    IOException exc = null;
    for (StorageOwner child : getChildStorages()) {
      if (child != null) {
        try {
          child.clean();
        }
        catch (IOException e) {
          exc = e;
        }
      }
    }
    if (exc != null) {
      throw exc;
    }
  }

  @Override
  public void close() throws IOException {
    IOException exc = null;
    for (StorageOwner child : getChildStorages()) {
      if (child != null) {
        try {
          child.close();
        }
        catch (IOException e) {
          exc = e;
        }
      }
    }
    if (exc != null) {
      throw exc;
    }
  }
}
