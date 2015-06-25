package org.jetbrains.platform.loader.impl.repository;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author nik
 */
abstract class ResourceRoot {
  public abstract InputStream readFile(@NotNull String relativePath) throws IOException;

  public abstract File getRootFile();
}
