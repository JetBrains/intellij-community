// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jpsBootstrap;

import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.stream.Stream;

@SuppressWarnings("UnstableApiUsage")
final class BuildDependenciesUtil {
  static boolean isPosix = FileSystems.getDefault().supportedFileAttributeViews().contains("posix");

  static void extractZip(Path archiveFile, Path target) throws IOException {
    try (ZipFile zipFile = new ZipFile(archiveFile.toFile(), "UTF-8")) {
      for (final Enumeration<ZipArchiveEntry> en = zipFile.getEntries(); en.hasMoreElements(); ) {
        ZipArchiveEntry entry = en.nextElement();

        Path entryPath = entryFile(target, entry.getName());
        if (entry.isDirectory()) {
          Files.createDirectories(entryPath);
        }
        else {
          Files.createDirectories(entryPath.getParent());
          try (InputStream is = zipFile.getInputStream(entry)) {
            Files.copy(is, entryPath);
          }

          //noinspection OctalInteger
          if (isPosix && (entry.getUnixMode() & 0111) != 0) {
            //noinspection SpellCheckingInspection
            Files.setPosixFilePermissions(entryPath, PosixFilePermissions.fromString("rwxr-xr-x"));
          }
        }
      }
    }
  }

  static Path entryFile(Path outputDir, String entryName) throws IOException {
    ensureValidPath(entryName);
    return outputDir.resolve(trimStart(entryName, '/'));
  }

  private static void ensureValidPath(String entryName) throws IOException {
    if (entryName.contains("..") && Arrays.asList(entryName.split("[/\\\\]")).contains("..")) {
      throw new IOException("Invalid entry name: " + entryName);
    }
  }

  private static String trimStart(String s, char charToTrim) {
    int index = 0;
    while (s.charAt(index) == charToTrim) index++;
    return s.substring(index);
  }

  static void cleanDirectory(Path directory) throws IOException {
    Files.createDirectories(directory);
    try (Stream<Path> stream = Files.list(directory)) {
      stream.forEach(path -> {
        try {
          MoreFiles.deleteRecursively(path, RecursiveDeleteOption.ALLOW_INSECURE);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      });
    }
  }
}
