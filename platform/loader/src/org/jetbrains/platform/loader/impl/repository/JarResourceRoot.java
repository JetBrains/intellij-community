package org.jetbrains.platform.loader.impl.repository;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author nik
 */
class JarResourceRoot extends ResourceRoot {
  private File myJarFile;

  public JarResourceRoot(File jarFile) {
    myJarFile = jarFile;
  }

  @Override
  public InputStream readFile(@NotNull String relativePath) throws IOException {
    final ZipFile jarFile = new ZipFile(myJarFile.getAbsolutePath());
    try {
      ZipEntry entry = jarFile.getEntry(relativePath);
      if (entry != null) {
        return new FilterInputStream(jarFile.getInputStream(entry)) {
          @Override
          public void close() throws IOException {
            super.close();
            jarFile.close();
          }
        };
      }
      return null;
    }
    catch (IOException e) {
      jarFile.close();
      throw e;
    }
  }

  @Override
  public File getRootFile() {
    return myJarFile;
  }
}
