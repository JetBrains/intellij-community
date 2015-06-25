package org.jetbrains.platform.loader.impl.repository;

import org.jetbrains.annotations.NotNull;

import java.io.*;

/**
 * @author nik
 */
public class DirectoryResourceRoot extends ResourceRoot {
  private final File myDirectory;

  public DirectoryResourceRoot(File directory) {
    myDirectory = directory;
  }

  @Override
  public InputStream readFile(@NotNull String relativePath) throws IOException {
    try {
      return new BufferedInputStream(new FileInputStream(new File(myDirectory, relativePath)));
    }
    catch (FileNotFoundException e) {
      return null;
    }
  }

  @Override
  public File getRootFile() {
    return myDirectory;
  }
}
