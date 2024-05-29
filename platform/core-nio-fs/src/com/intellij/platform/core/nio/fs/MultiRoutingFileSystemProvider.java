// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.core.nio.fs;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.spi.FileSystemProvider;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * A file system that can delegate specific paths to other file systems.
 * <p>
 * Although this filesystem routes requests to different filesystems, it doesn't mangle paths.
 * It's the responsibility of the backend filesystem to convert a path passed to {@link MultiRoutingFileSystem}
 * into some other specific path.
 *
 * @see #computeBackend(FileSystemProvider, String, boolean, boolean, BiFunction)
 * @see RoutingAwareFileSystemProvider
 */
public final class MultiRoutingFileSystemProvider
  extends DelegatingFileSystemProvider<MultiRoutingFileSystemProvider, MultiRoutingFileSystem> {
  /**
   * The fallback provider for requests to roots
   * not registered via {@link #computeBackend(FileSystemProvider, String, boolean, boolean, BiFunction)}.
   * <p>
   * Used in tests via reflection.
   */
  @VisibleForTesting
  public final FileSystemProvider myLocalProvider;

  private final MultiRoutingFileSystem myFileSystem;

  /**
   * Adds a new backend filesystem that handles requests to specific roots.
   * <p>
   * If there's already a file system assigned to the specified root, it will be replaced with the new one. Otherwise, the new will be
   * just added.
   * <p>
   * The function is defined as static and requires an instance of {@link MultiRoutingFileSystem}
   * because there may be more than one class {@link MultiRoutingFileSystem} loaded different classloaders.
   *
   * @param provider      Provider <b>must</b> be an instance of {@link MultiRoutingFileSystemProvider}.
   * @param root          The first directory of an absolute path.
   *                      On Windows, it can be {@code C:}, {@code \\wsl.localhost\Ubuntu-22.04}, etc.
   * @param isPrefix      If true, {@code root} will be matched not exactly, but as a prefix for queried root paths.
   * @param caseSensitive Defines if the whole filesystem is case-sensitive. This flag is used for finding a specific root.
   * @param function      A function that either defines a new backend, or deletes an existing one by returning {@code null}.
   *                      The function gets as the first argument {@link #myLocalProvider} and gets as the second the previous filesystem
   *                      assigned to the root, if it has been assigned.
   *                      <b>Note:</b> the function may be called more than once.
   */
  public static void computeBackend(
    @NotNull FileSystemProvider provider,
    @NotNull String root,
    boolean isPrefix,
    boolean caseSensitive,
    @NotNull BiFunction<@NotNull FileSystemProvider, @Nullable FileSystem, @Nullable FileSystem> function
  ) {
    if (provider.getClass().getName().equals(MultiRoutingFileSystemProvider.class.getName())) {
      Map<String, Object> arguments = new HashMap<>();
      arguments.put(KEY_ROOT, root);
      arguments.put(KEY_PREFIX, isPrefix);
      arguments.put(KEY_CASE_SENSITIVE, caseSensitive);
      arguments.put(KEY_FUNCTION, function);

      try {
        //noinspection resource
        provider.newFileSystem(URI.create("file:/"), arguments);
      }
      catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
    else {
      throw new IllegalArgumentException(String.format("%s is not an instance of %s", provider, MultiRoutingFileSystemProvider.class));
    }
  }

  public MultiRoutingFileSystemProvider(FileSystemProvider localFSProvider) {
    // TODO I wouldn't force using CorePosixFilteringFileSystemProvider at the top level.
    myLocalProvider = localFSProvider.getFileSystem(URI.create("file:///")).supportedFileAttributeViews().contains("posix")
                      ? localFSProvider
                      : new CorePosixFilteringFileSystemProvider(localFSProvider);
    myFileSystem = new MultiRoutingFileSystem(this, myLocalProvider.getFileSystem(URI.create("file:///")));
  }

  @Override
  protected @NotNull MultiRoutingFileSystem wrapDelegateFileSystem(@NotNull FileSystem delegateFs) {
    return new MultiRoutingFileSystem(this, delegateFs);
  }

  @Override
  public @NotNull MultiRoutingFileSystem newFileSystem(Path path, Map<String, ?> env) {
    return getFileSystem(path.toUri());
  }

  @Override
  public @Nullable MultiRoutingFileSystem newFileSystem(URI uri, @Nullable Map<String, ?> env) {
    if (env == null) {
      return getFileSystem(uri);
    }

    String root = Objects.requireNonNull((String)env.get(KEY_ROOT));
    Boolean isPrefix = Objects.requireNonNull((Boolean)env.get(KEY_PREFIX));
    Boolean caseSensitive = Objects.requireNonNull((Boolean)env.get(KEY_CASE_SENSITIVE));

    @SuppressWarnings({"unchecked", "rawtypes"})
    BiFunction<@NotNull FileSystemProvider, @Nullable FileSystem, @Nullable FileSystem> function =
      Objects.requireNonNull((BiFunction)env.get(KEY_FUNCTION));

    myFileSystem.computeBackend(root, isPrefix, caseSensitive, function);
    return null;
  }

  private static final String KEY_ROOT = "KEY_ROOT";
  private static final String KEY_PREFIX = "KEY_PREFIX";
  private static final String KEY_CASE_SENSITIVE = "KEY_CASE_SENSITIVE";
  private static final String KEY_FUNCTION = "KEY_FUNCTION";

  @Override
  public @NotNull MultiRoutingFileSystem getFileSystem(@NotNull URI uri) {
    if (!uri.getScheme().equals("file") || uri.getAuthority() != null && !uri.getAuthority().isEmpty()) {
      throw new UnsupportedOperationException(String.format(
        "Unexpected URI: %s\nThis class is supposed to replace the local file system.",
        uri
      ));
    }
    return myFileSystem;
  }

  @Override
  protected @NotNull FileSystemProvider getDelegate(@Nullable Path path1, @Nullable Path path2) {
    if (path1 == null) {
      if (path2 == null) {
        return myLocalProvider;
      }
      path1 = path2;
      path2 = null;
    }

    if (!path1.isAbsolute()) {
      path1 = path1.toAbsolutePath();
    }

    FileSystemProvider provider1 = myFileSystem.getBackend(path1.getRoot().toString()).provider();
    if (path2 == null) {
      return provider1;
    }

    if (!path2.isAbsolute()) {
      path2 = path2.toAbsolutePath();
    }

    FileSystemProvider provider2 = myFileSystem.getBackend(path2.getRoot().toString()).provider();

    if (provider1.equals(provider2)) {
      return provider1;
    }
    else if (provider1 instanceof RoutingAwareFileSystemProvider && ((RoutingAwareFileSystemProvider)provider1).canHandleRouting()) {
      return provider1;
    }
    else if (provider2 instanceof RoutingAwareFileSystemProvider && ((RoutingAwareFileSystemProvider)provider2).canHandleRouting()) {
      return provider2;
    }
    else {
      throw new IllegalArgumentException(String.format("Provider mismatch: %s != %s", provider1, provider2));
    }
  }

  @Contract("null -> null; !null -> !null")
  @Override
  @VisibleForTesting
  public @Nullable Path toDelegatePath(@Nullable Path path) {
    if (path == null) {
      return null;
    }
    else if (path instanceof MultiRoutingFsPath) {
      return path;
    }
    else {
      return new MultiRoutingFsPath(myFileSystem, path);
    }
  }

  @Contract("null -> null; !null -> !null")
  @Override
  protected @Nullable Path fromDelegatePath(@Nullable Path path) {
    if (path instanceof MultiRoutingFsPath) {
      return ((MultiRoutingFsPath)path).getDelegate();
    }
    else {
      return path;
    }
  }
}