// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.core.nio.fs;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
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
      this.root = sanitizeRoot(root, caseSensitive);
      this.prefix = prefix;
      this.caseSensitive = caseSensitive;
      this.fileSystem = fileSystem;
    }

    private static @NotNull String sanitizeRoot(@NotNull String root, boolean caseSensitive) {
      // On Unix, a file name may contain `\` but may not contain `/`.
      // On Windows, a file name may contain neither `\` nor `/`.
      // It happens sometimes that a Windows path uses `/` as a separator.
      // An assumption that all paths use `/` as a separator makes matching easier.
      root = root.replace(File.separatorChar, '/');
      root = root.substring(0, trimEndSlashes(root));
      if (!caseSensitive) {
        root = root.toLowerCase(Locale.ROOT);
      }
      return root;
    }

    private static int trimEndSlashes(final @NotNull String root) {
      int i = root.length() - 1;
      while (i >= 0 && root.charAt(i) == '/') {
        --i;
      }
      return i + 1;
    }

    boolean matchPath(@NotNull String candidate) {
      if (candidate.length() < root.length()) return false;

      for (int i = 0; i < root.length(); i++) {
        char candidateChar = candidate.charAt(i);
        char rootChar = root.charAt(i);

        if (!caseSensitive && candidateChar >= 'A' && candidateChar <= 'Z') {
          candidateChar -= 'A';
          candidateChar += 'a';
        }
        else if (candidateChar == '\\') {
          candidateChar = '/';
        }

        if (candidateChar != rootChar) {
          return false;
        }
      }

      return prefix ||
             candidate.length() == root.length() ||
             candidate.charAt(root.length()) == '/' ||
             candidate.charAt(root.length()) == '\\';
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
      String sanitizedRoot = Backend.sanitizeRoot(root, caseSensitive);
      List<@NotNull Backend> newList = new ArrayList<>(oldList);
      ListIterator<@NotNull Backend> iter = newList.listIterator();
      FileSystem newFs = null;
      while (iter.hasNext()) {
        Backend current = iter.next();
        if (current.root.equals(sanitizedRoot)) {
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

      iter.add(new Backend(sanitizedRoot, isPrefix, caseSensitive, newFs));

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
      rootDirectories.put(root.toString(), new MultiRoutingFsPath(this, root));
    }
    // Some of the backend file systems may override the roots.
    // However, it's important to check that they override only the registered paths.
    for (Backend backend : myBackends.get()) {
      for (Path candidate : backend.fileSystem.getRootDirectories()) {
        if (backend.matchPath(candidate.toString())) {
          rootDirectories.put(candidate.toString(), new MultiRoutingFsPath(this, candidate));
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
  FileSystem getBackend(@NotNull String path) {
    // It's important that the backends are sorted by the path length in the reverse order. Otherwise, prefixes won't work correctly.
    for (Backend backend : myBackends.get()) {
      if (backend.matchPath(path)) {
        return backend.fileSystem;
      }
    }
    return myLocalFS;
  }

  @Override
  public WatchService newWatchService() throws IOException {
    // TODO Move it to DelegatingFileSystem.
    return new MultiRoutingWatchServiceDelegate(super.newWatchService(), myProvider);
  }
}