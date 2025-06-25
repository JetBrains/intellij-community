// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.core.nio.fs;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.WatchService;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * @see MultiRoutingFileSystemProvider
 */
public final class MultiRoutingFileSystem extends DelegatingFileSystem<MultiRoutingFileSystemProvider> {
  private final MultiRoutingFileSystemProvider myProvider;
  private final FileSystem myLocalFS;

  private static final BiFunction<@NotNull FileSystem, @NotNull String, @NotNull FileSystem>
    NO_OP_COMPUTE_FN = (localFS, path) -> localFS;

  private volatile @NotNull BiFunction<@NotNull FileSystem, @NotNull String, @NotNull FileSystem> myComputeFn = NO_OP_COMPUTE_FN;

  public static final Function<@NotNull FileSystem, @NotNull Collection<@NotNull Path>>
    NO_OP_GET_CUSTOM_ROOT_PATH = (ignored) -> Collections.emptyList();

  private volatile @NotNull Function<@NotNull FileSystem, @NotNull Collection<@NotNull Path>> myGetCustomRootsFn =
    NO_OP_GET_CUSTOM_ROOT_PATH;

  public static final Function<@NotNull FileSystem, @NotNull Collection<@NotNull FileStore>>
    NO_OP_GET_CUSTOM_FILE_STORES_FN = (ignored) -> Collections.emptyList();

  private volatile @NotNull Function<@NotNull FileSystem, @NotNull Collection<@NotNull FileStore>> myGetCustomFileStoresFn =
    NO_OP_GET_CUSTOM_FILE_STORES_FN;

  public MultiRoutingFileSystem(MultiRoutingFileSystemProvider provider, FileSystem localFS) {
    myProvider = provider;
    myLocalFS = localFS;
  }

  /**
   * Sets up the behavior of this file system.
   * <p>
   * This function can't accept any types not defined in JDK because of IJPL-158098.
   * </p>
   *
   * @param computeFn             A function that returns a custom file system for handing a specific path.
   *                              <p>
   *                              <b>WARNING:</b> this is a <b>frequently invoked function</b>,
   *                              and it's a rare exclusion when preliminary optimizations are worth it.
   *                              </p>
   *                              <p>
   *                              Accepts two arguments:
   *                              <ol>
   *                              <li>The original local file system, f.i., {@code sun.nio.fs.WindowsFileSystem} or {@code sun.nio.fs.MacOSXFileSystem}.</li>
   *                              <li>{@link #sanitizeRoot(String)} applied to A string returned by {@link Path#toString()} for some path.</li>
   *                              </ol>
   *                              </p>
   *                              <p>
   *                              Returns a custom file system or the first argument of the function.
   *                              </p>
   * @param getCustomRootsFn      A function that returns some specific paths that will be added to the result of {@link #getRootDirectories()}.
   *                              <p>
   *                              Accepts a single argument: the original local file system, f.i., {@code sun.nio.fs.WindowsFileSystem} or {@code sun.nio.fs.MacOSXFileSystem}.
   *                              </p>
   * @param getCustomFileStoresFn A function that returns additional file stores that will be added to the result of {@link #getFileStores()}.
   *                              <p>
   *                              Accepts a single argument: the original local file system, f.i., {@code sun.nio.fs.WindowsFileSystem} or {@code sun.nio.fs.MacOSXFileSystem}.
   *                              </p>
   */
  public void setBackendProvider(
    @Nullable BiFunction<@NotNull FileSystem, @NotNull String, @NotNull FileSystem> computeFn,
    @Nullable Function<@NotNull FileSystem, @NotNull Collection<@NotNull Path>> getCustomRootsFn,
    @Nullable Function<@NotNull FileSystem, @NotNull Collection<@NotNull FileStore>> getCustomFileStoresFn
  ) {
    myComputeFn = computeFn != null ? computeFn : NO_OP_COMPUTE_FN;
    myGetCustomRootsFn = getCustomRootsFn != null ? getCustomRootsFn : NO_OP_GET_CUSTOM_ROOT_PATH;
    myGetCustomFileStoresFn = getCustomFileStoresFn != null ? getCustomFileStoresFn : NO_OP_GET_CUSTOM_FILE_STORES_FN;
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
    FileSystem result = getBackend(root);
    return result;
  }

  @Override
  public Iterable<Path> getRootDirectories() {
    Map<String, Path> rootDirectories = new LinkedHashMap<>();
    for (Path root : myLocalFS.getRootDirectories()) {
      rootDirectories.put(root.toString(), new MultiRoutingFsPath(this, root));
    }
    for (Path root : myGetCustomRootsFn.apply(myLocalFS)) {
      rootDirectories.put(root.toString(), new MultiRoutingFsPath(this, root));
    }
    return rootDirectories.values();
  }

  @Override
  public Iterable<FileStore> getFileStores() {
    Collection<FileStore> result = new LinkedHashSet<>();
    for (FileStore fileStore : myLocalFS.getFileStores()) {
      result.add(fileStore);
    }
    result.addAll(myGetCustomFileStoresFn.apply(myLocalFS));
    return result;
  }

  @Override
  public Set<String> supportedFileAttributeViews() {
    return myLocalFS.supportedFileAttributeViews();
  }

  public @NotNull FileSystem getBackend(@NotNull String path) {
    return myComputeFn.apply(myLocalFS, sanitizeRoot(path));
  }

  /**
   * Returns {@code true} if this path will be handled by a registered backend.
   * It is reasonable to assume that if this method returns {@code false}, then the path will be handled by the local NIO file system.
   * In some sense, this method is an approximation of a predicate "is this path remote?"
   */
  public boolean isRoutable(@NotNull Path path) {
    Path root = path.getRoot();
    if (root == null) {
      return false;
    }
    return getBackend(root.toString()) != myLocalFS;
  }

  /**
   * Removes trailing slashes and converts {@code '\'} to {@code '/'} on Windows.
   */
  public static @NotNull String sanitizeRoot(@NotNull String root) {
    // On Unix, a file name may contain `\` but may not contain `/`.
    // On Windows, a file name may contain neither `\` nor `/`.
    // It happens sometimes that a Windows path uses `/` as a separator.
    // An assumption that all paths use `/` as a separator makes matching easier.
    root = root.replace(File.separatorChar, '/');
    root = root.substring(0, trimEndSlashes(root));
    return root;
  }

  private static int trimEndSlashes(@NotNull String root) {
    int i = root.length() - 1;
    while (i >= 0 && root.charAt(i) == '/') {
      --i;
    }
    return i + 1;
  }

  @Override
  public WatchService newWatchService() throws IOException {
    // TODO Move it to DelegatingFileSystem.
    return new MultiRoutingWatchServiceDelegate(super.newWatchService(), myProvider);
  }
}
