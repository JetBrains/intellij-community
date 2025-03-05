// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.incremental.fs;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.FileCollectionFactory;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.*;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.builders.BuildRootIndex;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.incremental.Utils;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

@SuppressWarnings("LoggingSimilarMessage")
@ApiStatus.Internal
public final class FilesDelta {
  private static final Logger LOG = Logger.getInstance(FilesDelta.class);
  private final ReentrantLock dataLock = new ReentrantLock();

  private final Set<String> deletedPaths = CollectionFactory.createFilePathLinkedSet();
  private final Map<BuildRootDescriptor, Set<Path>> filesToRecompile = new LinkedHashMap<>();

  public void lockData(){
    dataLock.lock();
  }

  public void unlockData(){
    dataLock.unlock();
  }

  public FilesDelta() {
  }

  FilesDelta(Collection<FilesDelta> deltas) {
    for (FilesDelta delta : deltas) {
      addAll(delta);
    }
  }

  private void addAll(FilesDelta other) {
    other.lockData();
    try {
      deletedPaths.addAll(other.deletedPaths);
      for (Map.Entry<BuildRootDescriptor, Set<Path>> entry : other.filesToRecompile.entrySet()) {
        _addToRecompiled(entry.getKey(), entry.getValue());
      }
    }
    finally {
      other.unlockData();
    }
  }

  public void save(DataOutput out) throws IOException {
    lockData();
    try {
      out.writeInt(deletedPaths.size());
      for (String path : deletedPaths) {
        IOUtil.writeString(path, out);
      }
      out.writeInt(filesToRecompile.size());
      for (Map.Entry<BuildRootDescriptor, Set<Path>> entry : filesToRecompile.entrySet()) {
        IOUtil.writeString(entry.getKey().getRootId(), out);
        Set<Path> files = entry.getValue();
        out.writeInt(files.size());
        for (Path file : files) {
          IOUtil.writeString(FileUtilRt.toSystemIndependentName(file.toString()), out);
        }
      }
    }
    finally {
      unlockData();
    }
  }

  public void load(@NotNull DataInput in, @NotNull BuildTarget<?> target, @NotNull BuildRootIndex buildRootIndex) throws IOException {
    lockData();
    try {
      deletedPaths.clear();
      int deletedCount = in.readInt();
      while (deletedCount-- > 0) {
        deletedPaths.add(IOUtil.readString(in));
      }
      filesToRecompile.clear();
      int recompileCount = in.readInt();
      while (recompileCount-- > 0) {
        String rootId = IOUtil.readString(in);
        BuildRootDescriptor descriptor = target.findRootDescriptor(Objects.requireNonNull(rootId), buildRootIndex);
        Set<Path> files;
        if (descriptor == null) {
          LOG.debug("Cannot find root by " + rootId + ", delta will be skipped");
          files = FileCollectionFactory.createCanonicalLinkedPathSet();
        }
        else {
          files = filesToRecompile.get(descriptor);
          if (files == null) {
            files = FileCollectionFactory.createCanonicalLinkedPathSet();
            filesToRecompile.put(descriptor, files);
          }
        }

        int filesCount = in.readInt();
        while (filesCount-- > 0) {
          Path file = Path.of(Objects.requireNonNull(IOUtil.readString(in)));
          if (Utils.IS_TEST_MODE) {
            LOG.info("Loaded " + file);
          }
          files.add(file);
        }
      }
    }
    finally {
      unlockData();
    }
  }

  public static void skip(@NotNull DataInput in) throws IOException {
    int deletedCount = in.readInt();
    while (deletedCount-- > 0) {
      IOUtil.readString(in);
    }
    int recompiledCount = in.readInt();
    while (recompiledCount-- > 0) {
      IOUtil.readString(in);
      int filesCount = in.readInt();
      while (filesCount-- > 0) {
        IOUtil.readString(in);
      }
    }
  }

  public boolean hasChanges() {
    lockData();
    try {
      if (!deletedPaths.isEmpty()) {
        return true;
      }
      if (!filesToRecompile.isEmpty()) {
        for (Set<Path> files : filesToRecompile.values()) {
          if (!files.isEmpty()) {
            return true;
          }
        }
      }
      return false;
    }
    finally {
      unlockData();
    }
  }


  public boolean markRecompile(@NotNull BuildRootDescriptor root, @NotNull Path file) {
    lockData();
    try {
      boolean added = _addToRecompiled(root, file);
      if (added) {
        // optimization
        if (!deletedPaths.isEmpty()) {
          deletedPaths.remove(file.toAbsolutePath().normalize().toString());
        }
      }
      return added;
    }
    finally {
      unlockData();
    }
  }

  // used by Bazel
  @SuppressWarnings("unused")
  public void initRecompile(@NotNull Map<BuildRootDescriptor, Set<Path>> filesToRecompile) {
    lockData();
    try {
      assert this.filesToRecompile.isEmpty();
      assert this.deletedPaths.isEmpty();
      this.filesToRecompile.putAll(filesToRecompile);
    }
    finally {
      unlockData();
    }
  }

  public boolean markRecompileIfNotDeleted(@NotNull BuildRootDescriptor root, @NotNull Path file) {
    lockData();
    try {
      String path = null;
      boolean isMarkedDeleted = !deletedPaths.isEmpty() && deletedPaths.contains(path = file.toAbsolutePath().normalize().toString());
      if (isMarkedDeleted) {
        return false;
      }

      if (Files.notExists(file)) {
        // incorrect paths data recovery, so that the next make should not contain non-existing sources in 'recompile' list
        if (path == null) {
          path = file.toAbsolutePath().normalize().toString();
        }
        if (Utils.IS_TEST_MODE) {
          LOG.info("Marking deleted: " + path);
        }
        deletedPaths.add(path);
        return false;
      }
      _addToRecompiled(root, file);
      return true;
    }
    finally {
      unlockData();
    }
  }

  private boolean _addToRecompiled(@NotNull BuildRootDescriptor root, @NotNull Path file) {
    if (Utils.IS_TEST_MODE) {
      LOG.info("Marking dirty: " + file);
    }
    return filesToRecompile.computeIfAbsent(root, __ -> FileCollectionFactory.createCanonicalPathSet()).add(file);
  }

  private void _addToRecompiled(@NotNull BuildRootDescriptor root, @NotNull Collection<Path> filesToAdd) {
    filesToRecompile.computeIfAbsent(root, __ -> FileCollectionFactory.createCanonicalPathSet()).addAll(filesToAdd);
  }

  public void addDeleted(@NotNull Path file) {
    String path = file.toAbsolutePath().normalize().toString();
    lockData();
    try {
      // ensure the file is not marked to recompilation anymore
      for (Set<Path> files : filesToRecompile.values()) {
        files.remove(file);
      }
      deletedPaths.add(path);
      if (Utils.IS_TEST_MODE) {
        LOG.info("Marking deleted: " + path);
      }
    }
    finally {
      unlockData();
    }
  }

  public void clearDeletedPaths() {
    lockData();
    try {
      deletedPaths.clear();
    }
    finally {
      unlockData();
    }
  }

  public @NotNull Set<String> getAndClearDeletedPaths() {
    lockData();
    try {
      if (deletedPaths.isEmpty()) {
        return Set.of();
      }

      try {
        return CollectionFactory.createFilePathLinkedSet(deletedPaths);
      }
      finally {
        deletedPaths.clear();
      }
    }
    finally {
      unlockData();
    }
  }

  /**
   * @deprecated Use {@link #getSourceMapToRecompile()}
   */
  @SuppressWarnings("IO_FILE_USAGE")
  @Deprecated
  public @NotNull @Unmodifiable Map<BuildRootDescriptor, Set<File>> getSourcesToRecompile() {
    LOG.assertTrue(dataLock.isHeldByCurrentThread(), "FilesDelta data must be locked by querying thread");
    Map<BuildRootDescriptor, Set<File>> map = new LinkedHashMap<>(filesToRecompile.size());
    for (Map.Entry<BuildRootDescriptor, Set<Path>> entry : filesToRecompile.entrySet()) {
      Set<Path> value = entry.getValue();
      Set<File> set = new LinkedHashSet<>(value.size());
      for (Path path : value) {
        set.add(path.toFile());
      }
      map.put(entry.getKey(), set);
    }
    return map;
  }

  public @NotNull Map<BuildRootDescriptor, Set<Path>> getSourceMapToRecompile() {
    LOG.assertTrue(dataLock.isHeldByCurrentThread(), "FilesDelta data must be locked by querying thread");
    return filesToRecompile;
  }

  public @NotNull @UnmodifiableView Collection<Set<Path>> getSourceSetsToRecompile() {
    LOG.assertTrue(dataLock.isHeldByCurrentThread(), "FilesDelta data must be locked by querying thread");
    return filesToRecompile.values();
  }

  public boolean isMarkedRecompile(@NotNull BuildRootDescriptor rootDescriptor, @NotNull Path file) {
    lockData();
    try {
      Set<Path> files = filesToRecompile.get(rootDescriptor);
      return files != null && files.contains(file);
    }
    finally {
      unlockData();
    }
  }

  public @Nullable Set<Path> clearRecompile(@NotNull BuildRootDescriptor root) {
    lockData();
    try {
      return filesToRecompile.remove(root);
    }
    finally {
      unlockData();
    }
  }
}
