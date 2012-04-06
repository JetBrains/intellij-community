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
