package org.jetbrains.jps.incremental.storage;

import java.io.IOException;

/**
 * @author nik
 */
public interface StorageOwner {
  void flush(boolean memoryCachesOnly);
  void clean() throws IOException;
  void close() throws IOException;
}
