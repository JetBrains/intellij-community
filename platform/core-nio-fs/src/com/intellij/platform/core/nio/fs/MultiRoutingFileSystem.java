// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.core.nio.fs;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.spi.FileSystemProvider;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

/**
 * @see MultiRoutingFileSystemProvider
 */
public class MultiRoutingFileSystem extends DelegatingFileSystem<MultiRoutingFileSystemProvider> {
  private final MultiRoutingFileSystemProvider myProvider;
  private final FileSystem myLocalFS;

  private static class Backend {
    @NotNull final String root;
    final boolean prefix;
    final boolean caseSensitive;
    @NotNull final FileSystem fileSystem;

    Backend(@NotNull String root, boolean prefix, boolean caseSensitive, @NotNull FileSystem fileSystem) {
      this.root = root;
      this.prefix = prefix;
      this.caseSensitive = caseSensitive;
      this.fileSystem = fileSystem;
    }

    boolean matchRoot(@NotNull String candidate) {
      if (!prefix && candidate.length() != root.length()) {
        return false;
      }
      return candidate.regionMatches(!caseSensitive, 0, root, 0, root.length());
    }
  }

  private final AtomicReference<@NotNull List<@NotNull Backend>> myBackends = new AtomicReference<>(Collections.emptyList());

  public MultiRoutingFileSystem(MultiRoutingFileSystemProvider provider, FileSystem localFS) {
    myProvider = provider;
    myLocalFS = localFS;
  }

  /**
   * @see MultiRoutingFileSystemProvider#computeBackend(FileSystemProvider, String, boolean, boolean, BiFunction)
   */
  void computeBackend(
    @NotNull String root,
    boolean isPrefix,
    boolean caseSensitive,
    BiFunction<? super @NotNull FileSystemProvider, ? super @Nullable FileSystem, @Nullable FileSystem> compute
  ) {
    myBackends.updateAndGet(oldList -> {
      List<@NotNull Backend> newList = new ArrayList<>(oldList);
      ListIterator<@NotNull Backend> iter = newList.listIterator();
      FileSystem newFs = null;
      while (iter.hasNext()) {
        Backend current = iter.next();
        if (current.root.equals(root)) {
          iter.remove();
          newFs = compute.apply(myProvider.myLocalProvider, current.fileSystem);
          if (newFs == null) {
            return newList;
          }
          break;
        }
      }

      if (newFs == null) {
        newFs = compute.apply(myProvider.myLocalProvider, null);
        if (newFs == null) {
          return newList;
        }
      }

      iter.add(new Backend(root, isPrefix, caseSensitive, newFs));

      // To ease finding the appropriate backend for a specific root, the roots should be ordered by their lengths in the descending order.
      // This operation is quite rare and the list is quite small. There's no reason to deal with error-prone bisecting.
      newList.sort((r1, r2) -> r2.root.length() - r1.root.length());

      return newList;
    });
  }

  @Override
  public @NotNull MultiRoutingFileSystemProvider provider() {
    return myProvider;
  }

  @Override
  protected @NotNull FileSystem getDelegate() {
    return myLocalFS;
  }

  @Override
  protected @NotNull FileSystem getDelegate(@NotNull String root) {
    if (MultiRoutingFileSystemProvider.ourForceDefaultFs) {
      return myLocalFS;
    }
    return getBackend(root);
  }

  @Override
  public Iterable<Path> getRootDirectories() {
    Map<String, Path> rootDirectories = new LinkedHashMap<>();
    for (Path root : myLocalFS.getRootDirectories()) {
      rootDirectories.put(root.toString(), root);
    }
    // Some of the backend file systems may override the roots.
    // However, it's important to check that they override only the registered paths.
    for (Backend backend : myBackends.get()) {
      for (Path candidate : backend.fileSystem.getRootDirectories()) {
        if (backend.matchRoot(candidate.toString())) {
          rootDirectories.put(candidate.toString(), candidate);
          break;
        }
      }
    }
    return rootDirectories.values();
  }

  @Override
  public Iterable<FileStore> getFileStores() {
    Collection<FileStore> result = new LinkedHashSet<>();
    for (FileStore fileStore : myLocalFS.getFileStores()) {
      result.add(fileStore);
    }
    for (Backend backend : myBackends.get()) {
      for (FileStore fileStore : backend.fileSystem.getFileStores()) {
        result.add(fileStore);
      }
    }
    return result;
  }

  @Override
  public Set<String> supportedFileAttributeViews() {
    return myLocalFS.supportedFileAttributeViews();
  }

  @NotNull
  FileSystem getBackend(@NotNull String root) {
    // It's important that the backends are sorted by the root length in the reverse order. Otherwise, prefixes won't work correctly.
    for (Backend backend : myBackends.get()) {
      if (backend.matchRoot(root)) {
        return backend.fileSystem;
      }
    }
    return myLocalFS;
  }
}
