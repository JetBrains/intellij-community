// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.storage;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.DataInputOutputUtil;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

/// A file storage for checkpoints of the cache of project key hashes.
/// It stores temporary checkpoints in the supplied directory or a session directory and uses format version 2
@ApiStatus.Internal
public final class FileKeyHashLogCheckpointStore implements KeyHashLogCheckpointStore {
  private static final int STORE_FORMAT_VERSION = 2;
  private static final AtomicInteger CACHE_FILE_IDS = new AtomicInteger();
  private static volatile Path sessionDirectory;
  private static final Object SESSION_DIRECTORY_LOCK = new Object();

  private final @NotNull String storageName;
  private final @Nullable Path directory;
  private final int cacheFileId = CACHE_FILE_IDS.incrementAndGet();

  public FileKeyHashLogCheckpointStore(@NotNull String storageName, @Nullable Path directory) {
    this.storageName = storageName;
    this.directory = directory;
  }

  public FileKeyHashLogCheckpointStore(@NotNull Path baseStorageFile) {
    this(baseStorageFile.getFileName() + ".project", null);
  }

  @Override
  public @NotNull Checkpoint load(int checkpointId) throws IOException {
    try (var input = new DataInputStream(new BufferedInputStream(Files.newInputStream(pathFor(checkpointId))))) {
      int formatVersion = DataInputOutputUtil.readINT(input);
      if (formatVersion != STORE_FORMAT_VERSION) {
        throw new IOException("Unsupported project key hashes cache version: " + formatVersion);
      }

      long scannedUpTo = DataInputOutputUtil.readLONG(input);
      if (scannedUpTo < 0) {
        throw new IOException("Invalid scanned position: " + scannedUpTo);
      }

      long modificationCount = DataInputOutputUtil.readLONG(input);
      int removesUnaccountedFor = DataInputOutputUtil.readINT(input);
      int hashesCount = DataInputOutputUtil.readINT(input);
      if (removesUnaccountedFor < 0 || hashesCount < 0 || hashesCount > input.available()) {
        throw new IOException("Invalid project key hashes cache counters");
      }

      var hashes = new IntOpenHashSet(hashesCount);
      for (int i = 0; i < hashesCount; i++) {
        hashes.add(DataInputOutputUtil.readINT(input));
      }
      return new Checkpoint(new KeyHashLogStorage.Position(scannedUpTo), modificationCount, removesUnaccountedFor, hashes);
    }
  }

  @Override
  public void save(int checkpointId, @NotNull Checkpoint checkpoint) throws IOException {
    try (var output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(pathFor(checkpointId))))) {
      DataInputOutputUtil.writeINT(output, STORE_FORMAT_VERSION);
      DataInputOutputUtil.writeLONG(output, checkpoint.scannedUpTo().value());
      DataInputOutputUtil.writeLONG(output, checkpoint.modificationCount());
      DataInputOutputUtil.writeINT(output, checkpoint.removesUnaccountedFor());
      DataInputOutputUtil.writeINT(output, checkpoint.keyHashes().size());
      var iterator = checkpoint.keyHashes().iterator();
      while (iterator.hasNext()) {
        DataInputOutputUtil.writeINT(output, iterator.nextInt());
      }
    }
  }

  private @NotNull Path pathFor(int checkpointId) {
    return (directory != null ? directory : getSessionDir()).resolve(storageName + "." + cacheFileId + "." + checkpointId);
  }

  @SuppressWarnings("SSBasedInspection") // Keep the existing temporary directory cleanup.
  private static @NotNull Path getSessionDir() {
    var currentDirectory = sessionDirectory;
    if (currentDirectory == null) {
      synchronized (SESSION_DIRECTORY_LOCK) {
        currentDirectory = sessionDirectory;
        if (currentDirectory == null) {
          try {
            currentDirectory = FileUtil.createTempDirectory(
              Path.of(PathManager.getTempPath()).toFile(), Long.toString(System.currentTimeMillis()), "", true
            ).toPath();
            sessionDirectory = currentDirectory;
          }
          catch (IOException e) {
            throw new RuntimeException("Can not create temp directory", e);
          }
        }
      }
    }
    return currentDirectory;
  }
}
