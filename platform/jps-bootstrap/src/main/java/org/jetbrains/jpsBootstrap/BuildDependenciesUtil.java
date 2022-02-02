// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jpsBootstrap;

import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings("UnstableApiUsage")
final class BuildDependenciesUtil {
  static final boolean isPosix = FileSystems.getDefault().supportedFileAttributeViews().contains("posix");

  public static void extractZip(Path archiveFile, Path target, boolean stripRoot) throws Exception {
    try (ZipFile zipFile = new ZipFile(archiveFile.toFile(), "UTF-8")) {
      EntryNameConverter converter = new EntryNameConverter(target, stripRoot);

      for (ZipArchiveEntry entry : Collections.list(zipFile.getEntries())) {
        Path entryPath = converter.getOutputPath(entry.getName(), entry.isDirectory());
        if (entryPath == null) return;

        if (entry.isDirectory()) {
          Files.createDirectories(entryPath);
        }
        else {
          Files.createDirectories(entryPath.getParent());

          try (InputStream is = zipFile.getInputStream(entry)) {
            Files.copy(is, entryPath);
          }

          // 73 == 0111
          if (isPosix && (entry.getUnixMode() & 73) != 0) {
            //noinspection SpellCheckingInspection
            Files.setPosixFilePermissions(entryPath, PosixFilePermissions.fromString("rwxr-xr-x"));
          }
        }
      }
    }
  }

  static void extractTarGz(Path archiveFile, Path target, boolean stripRoot) throws Exception {
    try (TarArchiveInputStream archive = new TarArchiveInputStream(new GzipCompressorInputStream(new BufferedInputStream(Files.newInputStream(archiveFile))))) {
      final EntryNameConverter converter = new EntryNameConverter(target, stripRoot);

      while (true) {
        TarArchiveEntry entry = (TarArchiveEntry) archive.getNextEntry();
        if (Objects.isNull(entry)) break;

        Path entryPath = converter.getOutputPath(entry.getName(), entry.isDirectory());
        if (entryPath == null) continue;

        if (entry.isDirectory()) {
          Files.createDirectories(entryPath);
        }
        else {
          Files.createDirectories(entryPath.getParent());

          Files.copy(archive, entryPath);

          // 73 == 0111
          if (isPosix && (entry.getMode() & 73) != 0) {
            //noinspection SpellCheckingInspection
            Files.setPosixFilePermissions(entryPath, PosixFilePermissions.fromString("rwxr-xr-x"));
          }
        }
      }
    }
  }

  private static class EntryNameConverter {
    private final Path target;
    private final boolean stripRoot;

    private String leadingComponentPrefix = null;

    EntryNameConverter(Path target, boolean stripRoot) {
      this.stripRoot = stripRoot;
      this.target = target;
    }

    @Nullable
    Path getOutputPath(String entryName, boolean isDirectory) {
      String normalizedName = normalizeEntryName(entryName);
      if (!stripRoot) {
        return target.resolve(normalizedName);
      }

      if (leadingComponentPrefix == null) {
        String[] split = normalizedName.split(Character.toString(forwardSlash), 2);
        leadingComponentPrefix = split[0] + forwardSlash;

        if (split.length < 2) {
          if (!isDirectory) {
            throw new IllegalStateException("$archiveFile: first top-level entry must be a directory if strip root is enabled");
          }

          return null;
        }
        else {
          return target.resolve(split[1]);
        }
      }

      if (!normalizedName.startsWith(leadingComponentPrefix)) {
        throw new IllegalStateException(
          "$archiveFile: entry name '" + normalizedName + "' should start with previously found prefix '" + leadingComponentPrefix + "'");
      }

      return target.resolve(normalizedName.substring(leadingComponentPrefix.length()));
    }
  }

  static String normalizeEntryName(String name) {
    String withForwardSlashes = name.replace(backwardSlash, forwardSlash);
    String trimmed = trim(withForwardSlashes, forwardSlash);
    assertValidEntryName(trimmed);
    return trimmed;
  }

  private static final char backwardSlash = '\\';
  private static final String backwardSlashString = Character.toString(backwardSlash);
  private static final char forwardSlash = '/';
  private static final String forwardSlashString = Character.toString(forwardSlash);
  private static final String doubleForwardSlashString = forwardSlashString + forwardSlashString;

  private static void assertValidEntryName(String normalizedEntryName) {
    if (normalizedEntryName.isBlank()) {
      throw new IllegalStateException("Entry names should not be blank: '" + normalizedEntryName + "'");
    }
    if (normalizedEntryName.contains(backwardSlashString)) {
      throw new IllegalStateException("Normalized entry names should not contain '" + backwardSlashString + "'");
    }
    if (normalizedEntryName.startsWith(forwardSlashString)) {
      throw new IllegalStateException("Normalized entry names should not start with forward slash: " + normalizedEntryName);
    }
    if (normalizedEntryName.endsWith(forwardSlashString)) {
      throw new IllegalStateException("Normalized entry names should not end with forward slash: " + normalizedEntryName);
    }
    if (normalizedEntryName.contains(doubleForwardSlashString)) {
      throw new IllegalStateException("Normalized entry name should not contain '" + doubleForwardSlashString + "': " + normalizedEntryName);
    }
    if (normalizedEntryName.contains("..") && Arrays.stream(normalizedEntryName.split(forwardSlashString)).collect(Collectors.toList()).contains("..")) {
      throw new IllegalStateException("Invalid entry name: " + normalizedEntryName);
    }
  }

  static String trim(String s, char charToTrim) {
    int len = s.length();
    int start = 0;
    while (start < len && s.charAt(start) == charToTrim) start++;
    int end = len;
    while (end > 0 && start < end && s.charAt(end - 1) == charToTrim) end--;
    return s.substring(start, end);
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
