package org.jetbrains.jps.util

import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * @author nik
 */
class ZipUtil {
  static File extractToTempDir(File file) throws IOException {
    File output = FileUtil.createTempDirectory("extracted")
    extract(file, output, null)
    return output
  }

  static def extract(final File file, File outputDir, FilenameFilter filenameFilter) throws IOException {
    ZipFile zipFile = new ZipFile(file)
    try {
      final Enumeration entries = zipFile.entries()
      while (entries.hasMoreElements()) {
        ZipEntry entry = (ZipEntry) entries.nextElement()
        final File entryFile = new File(outputDir, entry.getName())
        if (filenameFilter == null || filenameFilter.accept(entryFile.getParentFile(), entryFile.getName())) {
          extractEntry(entry, zipFile.getInputStream(entry), outputDir)
        }
      }
    }
    finally {
      zipFile.close();
    }
  }

  static def extractEntry(ZipEntry entry, final InputStream inputStream, File outputDir) throws IOException {
    final boolean isDirectory = entry.isDirectory()
    final String relativeName = entry.getName()
    final File file = new File(outputDir, relativeName)
    FileUtil.createParentDirs(file)
    if (isDirectory) {
      file.mkdir()
    }
    else {
      final BufferedInputStream input = new BufferedInputStream(inputStream)
      final BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(file))
      try {
        final byte[] buffer = new byte[1024*20]
        int len
        while ((len = input.read(buffer)) >= 0) {
          output.write(buffer, 0, len)
        }
      }
      finally {
        output.close()
        input.close()
      }
    }
  }
}
