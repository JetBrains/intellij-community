package org.jetbrains.jps.builders.storage;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.storage.StorageOwner;

import java.io.File;
import java.io.IOException;

/**
 * @author nik
 */
public abstract class StorageProvider<S extends StorageOwner> {
  @NotNull
  public abstract S createStorage(File targetDataDir) throws IOException;
}
