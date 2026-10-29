// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl.storage.durablemap;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.hash.EqualityPolicy;
import com.intellij.util.indexing.IndexId;
import com.intellij.util.indexing.impl.storage.FileChannelKeyHashLogStorage;
import com.intellij.util.indexing.impl.storage.FileKeyHashLogCheckpointStore;
import com.intellij.util.indexing.impl.storage.KeyHashLog;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;

/// A [KeyHashLogAccessor] for a journal stored by [FileChannelKeyHashLogStorage].
/// A session marker identifies journals that need rebuilding after an interrupted session
@ApiStatus.Internal
public final class FileChannelKeyHashLogAccessor<Key> implements KeyHashLogAccessor<Key> {
  private final Path baseFile;
  private final Path dataFile;
  private final Path sessionMarker;

  private final EqualityPolicy<Key> equality;
  private final @Nullable Registry registry;
  private @Nullable FileChannelKeyHashLogStorage storage;
  private @Nullable KeyHashLog<Key> log;
  private boolean requiresRebuild;

  public FileChannelKeyHashLogAccessor(@NotNull Path baseFile,
                                       @NotNull EqualityPolicy<Key> equality) {
    this(baseFile, equality, null);
  }

  private FileChannelKeyHashLogAccessor(@NotNull Path baseFile,
                                        @NotNull EqualityPolicy<Key> equality,
                                        @Nullable Registry registry) {
    this.baseFile = baseFile.toAbsolutePath().normalize();
    this.equality = equality;
    this.registry = registry;
    dataFile = this.baseFile.resolveSibling(this.baseFile.getFileName() + ".project");
    sessionMarker = this.baseFile.resolveSibling(this.baseFile.getFileName() + ".session");
  }

  @Override
  public synchronized @NotNull KeyHashLog<Key> open() throws IOException {
    if (log != null) {
      throw new IOException("The key hash log is already open: " + baseFile);
    }
    if (registry != null) {
      registry.register(this);
    }
    try {
      requiresRebuild = Files.exists(sessionMarker) || !Files.exists(dataFile);
      Files.createDirectories(baseFile.toAbsolutePath().getParent());
      // Create/reuse the marker before compaction, or map updates can make the two stores disagree.
      Files.write(sessionMarker, new byte[0]);

      if (requiresRebuild) {
        deleteLogData(dataFile);
      }

      try {
        storage = new FileChannelKeyHashLogStorage(baseFile);
      }
      catch (EOFException e) {
        // rebuild from the index map
        requiresRebuild = true;
        deleteLogData(dataFile);
        storage = new FileChannelKeyHashLogStorage(baseFile);
      }

      log = new KeyHashLog<>(equality, storage, new FileKeyHashLogCheckpointStore(baseFile));
      return log;
    }
    catch (IOException | RuntimeException | Error e) {
      try {
        close(false);
      }
      catch (IOException closeError) {
        e.addSuppressed(closeError);
      }
      throw e;
    }
  }

  @Override
  public synchronized boolean requiresRebuild() {
    return requiresRebuild;
  }

  @Override
  public synchronized void close(boolean clean) throws IOException {
    var currentStorage = storage;
    var currentLog = log;
    try {
      if (currentStorage != null) {
        if (currentLog != null) {
          currentLog.close();
        }
        else {
          currentStorage.close();
        }
        if (clean) Files.deleteIfExists(sessionMarker);
      }
    }
    finally {
      storage = null;
      log = null;
      if (registry != null) registry.unregister(this);
    }
  }

  @Override
  public synchronized void cleanData() throws IOException {
    if (storage != null) throw new IOException("The key hash log must be closed before cleanup: " + baseFile);
    cleanData(baseFile);
  }

  public static @NotNull Path baseFileFor(@NotNull Path mapFile) {
    return mapFile.resolveSibling(mapFile.getFileName() + ".keyhash-channel");
  }

  static void cleanData(Path baseFile) throws IOException {
    var absoluteBaseFile = baseFile.toAbsolutePath().normalize();
    deleteLogData(absoluteBaseFile.resolveSibling(absoluteBaseFile.getFileName() + ".project"));
    Files.deleteIfExists(absoluteBaseFile.resolveSibling(absoluteBaseFile.getFileName() + ".session"));
  }

  private static void deleteLogData(Path dataFile) throws IOException {
    if (!IOUtil.deleteAllFilesStartingWith(dataFile)) {
      throw new IOException("Cannot delete key hash log: " + dataFile);
    }
  }

  /// A registry of journal accessors for one index storage provider.
  /// It closes open journals and removes all files of an index, including files for obsolete shards
  public static final class Registry implements Closeable {
    private final Path directory;
    private final Set<FileChannelKeyHashLogAccessor<?>> opened = new HashSet<>();
    private boolean closed;

    public Registry(@NotNull Path directory) {
      this.directory = directory;
    }

    public <Key> @NotNull KeyHashLogAccessor<Key> accessor(@NotNull IndexId<?, ?> indexId,
                                                           @NotNull EqualityPolicy<Key> descriptor) {
      return accessor(indexId, null, descriptor);
    }

    public <Key> @NotNull KeyHashLogAccessor<Key> accessor(@NotNull IndexId<?, ?> indexId,
                                                           @Nullable Integer shard,
                                                           @NotNull EqualityPolicy<Key> descriptor) {
      return new FileChannelKeyHashLogAccessor<>(indexDirectory(indexId).resolve(shard == null ? "unsharded" : "shard-" + shard),
                                                 descriptor, this);
    }

    public void cleanIndexData(@NotNull IndexId<?, ?> indexId) throws IOException {
      var indexDirectory = indexDirectory(indexId);
      if (Files.exists(indexDirectory) && !FileUtil.deleteWithRenaming(indexDirectory)) {
        throw new IOException("Cannot delete key hash logs: " + indexDirectory);
      }
    }

    private Path indexDirectory(IndexId<?, ?> indexId) {
      return directory.resolve(HexFormat.of().formatHex(indexId.getName().getBytes(StandardCharsets.UTF_8)));
    }

    private synchronized void register(FileChannelKeyHashLogAccessor<?> accessor) throws IOException {
      if (closed) throw new IOException("The key hash log registry is closed");
      opened.add(accessor);
    }

    private synchronized void unregister(FileChannelKeyHashLogAccessor<?> accessor) {
      opened.remove(accessor);
    }

    @Override
    public void close() throws IOException {
      Set<FileChannelKeyHashLogAccessor<?>> accessors;
      synchronized (this) {
        if (closed) return;
        closed = true;
        accessors = new HashSet<>(opened);
      }
      IOException failure = null;
      for (var accessor : accessors) {
        try {
          // The provider cannot commit caches owned by index storages. Preserve their session markers.
          accessor.close(false);
        }
        catch (IOException e) {
          if (failure == null) {
            failure = e;
          }
          else {
            failure.addSuppressed(e);
          }
        }
      }
      if (failure != null) throw failure;
    }
  }
}
