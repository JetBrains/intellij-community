package org.jetbrains.jps.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * @author Pavel.Sher
 *         Date: 05.03.2008
 */
public class TempFiles {
  private static final File ourCurrentTempDir = new File(System.getProperty("java.io.tmpdir"));
  private final File myCurrentTempDir;

  private static Random ourRandom;

  static {
    ourRandom = new Random();
    ourRandom.setSeed(System.currentTimeMillis());
  }

  private final List<File> myFilesToDelete = new ArrayList<File>();

  public TempFiles() {
    myCurrentTempDir = ourCurrentTempDir;
    if (!myCurrentTempDir.isDirectory()) {
      throw new IllegalStateException("Temp directory is not a directory, was deleted by some process: " + myCurrentTempDir.getAbsolutePath());
    }
  }

  private File doCreateTempDir(String prefix, String suffix) throws IOException {
    prefix = prefix == null ? "" : prefix;
    suffix = suffix == null ? ".tmp" : suffix;

    do {
      int count = ourRandom.nextInt();
      final File f = new File(myCurrentTempDir, prefix + count + suffix);
      if (!f.exists() && f.mkdirs()) {
        return f.getCanonicalFile();
      }
    } while (true);
  }

  private File doCreateTempFile(String prefix, String suffix) throws IOException {
    final File file = doCreateTempDir(prefix, suffix);
    file.delete();
    file.createNewFile();
    return file;
  }

  public final File createTempFile() throws IOException {
    File tempFile = doCreateTempFile("test", null);
    registerAsTempFile(tempFile);
    return tempFile;
  }

  private void registerAsTempFile(final File tempFile) {
    myFilesToDelete.add(tempFile);
  }

  /**
   * Returns a File object for created temp directory.
   *
   * @return a File object for created temp directory
   * @throws IOException if directory creation fails.
   */
  public final File createTempDir() throws IOException {
    File f = doCreateTempDir("test", "");
    registerAsTempFile(f);
    return f;
  }

  public void cleanup() {
    for (File file : myFilesToDelete) {
      if (file.exists()) {
        FileUtil.delete(file);
      }
    }
    myFilesToDelete.clear();
  }
}
