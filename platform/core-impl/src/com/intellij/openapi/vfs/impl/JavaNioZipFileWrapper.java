// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@ApiStatus.Internal
public final class JavaNioZipFileWrapper implements GenericZipFile {
  private final FileSystem myZipFS;

  public JavaNioZipFileWrapper(Path file) throws IOException {
    myZipFS = FileSystems.newFileSystem(file, (ClassLoader)null);
  }

  @Override
  public GenericZipEntry getEntry(@NotNull String entryName) throws IOException {
    Path zipPath = myZipFS.getPath(entryName);
    return Files.exists(zipPath) ? new EntryWrapper(zipPath) : null;
  }

  @Override
  public @NotNull List<? extends GenericZipEntry> getEntries() throws IOException {
    List<EntryWrapper> result = new ArrayList<>();
    for (Path root : myZipFS.getRootDirectories()) {
      try (Stream<Path> stream = Files.walk(root)) {
        stream.forEach(path -> {
          if (path != root) {
            result.add(new EntryWrapper(path));
          }
        });
      }
    }
    return result;
  }

  @Override
  public void close() throws IOException {
    try {
      myZipFS.close();
    }
    catch (NoSuchFileException ignored) { }
  }

  private static class EntryWrapper implements GenericZipEntry {
    private final Path myPath;

    EntryWrapper(Path path) {
      myPath = path;
    }

    @Override
    public long getSize() {
      try {
        return Files.size(myPath);
      }
      catch (IOException e) {
        return 0;
      }
    }

    @Override
    public @NotNull String getName() {
      return myPath.toString();
    }

    @Override
    public long getCrc() {
      try {
        Object value = Files.readAttributes(myPath, "zip:crc").get("crc");
        return value != null ? ((Long)value).longValue() : 0;
      }
      catch (IOException e) {
        return 0;
      }
    }

    @Override
    public boolean isDirectory() {
      return Files.isDirectory(myPath);
    }

    @Override
    public @Nullable InputStream getInputStream() throws IOException {
      return Files.newInputStream(myPath);
    }
  }
}
