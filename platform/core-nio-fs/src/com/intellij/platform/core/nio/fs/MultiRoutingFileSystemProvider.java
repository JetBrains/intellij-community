// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.core.nio.fs;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import sun.nio.fs.DefaultFileTypeDetector;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.spi.FileSystemProvider;
import java.nio.file.spi.FileTypeDetector;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A file system that can delegate specific paths to other file systems.
 *
 * @see MultiRoutingFileSystem#setBackendProvider
 * @see #getTheOnlyFileSystem
 * @see RoutingAwareFileSystemProvider
 */
@SuppressWarnings("UseOfSystemOutOrSystemErr")
public final class MultiRoutingFileSystemProvider
  extends DelegatingFileSystemProvider<MultiRoutingFileSystemProvider, MultiRoutingFileSystem> {

  /**
   * A production IDE has two VM options file: the bundled one and the user-defined one.
   * The user can only add new options to the user level file, but there's no way to remove a system property
   * defined in the bundled VM options file.
   * <p>
   * When it's not possible to unregister a custom file system, this option orders the file system to behave like the default one.
   */
  static final boolean ourForceDefaultFs = Objects.equals(System.getProperty("idea.force.default.filesystem"), "true");

  /**
   * The fallback provider for requests to roots
   * not registered via {@link #computeBackend(FileSystemProvider, String, boolean, boolean, BiFunction)}.
   * <p>
   * Used in tests via reflection.
   */
  @VisibleForTesting
  public final FileSystemProvider myLocalProvider;

  private final MultiRoutingFileSystem myFileSystem;

  public @NotNull MultiRoutingFileSystem getTheOnlyFileSystem() {
    return myFileSystem;
  }

  public MultiRoutingFileSystemProvider(FileSystemProvider localFSProvider) {
    myLocalProvider = localFSProvider;
    myFileSystem = new MultiRoutingFileSystem(this, myLocalProvider.getFileSystem(URI.create("file:///")));
  }

  @Override
  public @NotNull MultiRoutingFileSystem wrapDelegateFileSystem(@NotNull FileSystem delegateFs) {
    return new MultiRoutingFileSystem(this, delegateFs);
  }

  @Override
  public @Nullable MultiRoutingFileSystem newFileSystem(Path path, @Nullable Map<String, ?> env) {
    throw new UnsupportedOperationException(MultiRoutingFileSystemProvider.class.getName() + " doesn't open other files as filesystems");
  }

  @SuppressWarnings("unchecked")
  @Override
  public @Nullable MultiRoutingFileSystem newFileSystem(URI uri, @Nullable Map<String, ?> env) {
    if (env == null || !env.containsKey(KEY_MRFS)) {
      throw new UnsupportedOperationException(
        MultiRoutingFileSystem.class.getName() + " can be created only with `" +
        MultiRoutingFileSystemProvider.class.getName() + ".computeBackend()`." +
        " Otherwise, this file system provider behaves as a default file system provider and throws an error."
      );
    }

    BiFunction<FileSystem, String, FileSystem> computeFn =
      Objects.requireNonNull((BiFunction<FileSystem, String, FileSystem>)env.get(KEY_COMPUTE_FN));

    Function<FileSystem, Collection<Path>> getCustomRootsFn =
      Objects.requireNonNull((Function<FileSystem, Collection<Path>>)env.get(KEY_GET_CUSTOM_ROOTS_FN));

    Function<FileSystem, Collection<FileStore>> getCustomFileStoresFn =
      Objects.requireNonNull((Function<FileSystem, Collection<FileStore>>)env.get(KEY_GET_CUSTOM_FILE_STORES_FN));

    myFileSystem.setBackendProvider(computeFn, getCustomRootsFn, getCustomFileStoresFn);

    return null;
  }

  private static final String KEY_MRFS = "MRFS";
  private static final String KEY_COMPUTE_FN = "KEY_COMPUTE_FN";
  private static final String KEY_GET_CUSTOM_ROOTS_FN = "KEY_GET_CUSTOM_ROOTS_FN";
  private static final String KEY_GET_CUSTOM_FILE_STORES_FN = "KEY_GET_CUSTOM_FILE_STORES_FN";

  @Override
  public @NotNull MultiRoutingFileSystem getFileSystem(@NotNull URI uri) {
    if (uri.equals(URI.create("file:///"))) {
      return myFileSystem;
    }
    throw new UnsupportedOperationException(String.format(
      "Unexpected URI: %s\nThis class is supposed to replace the local file system.",
      uri
    ));
  }

  @Override
  protected @NotNull FileSystemProvider getDelegate(@Nullable Path path1, @Nullable Path path2) {
    if (ourForceDefaultFs) {
      return myLocalProvider;
    }

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

    String path1String = path1.toString();
    FileSystem backend1 = myFileSystem.getBackend(path1String);
    FileSystemProvider provider1 = backend1.provider();
    if (path2 == null) {
      return provider1;
    }

    if (!path2.isAbsolute()) {
      path2 = path2.toAbsolutePath();
    }

    String path2String = path2.toString();
    FileSystem backend2 = myFileSystem.getBackend(path2String);
    FileSystemProvider provider2 = backend2.provider();

    if (provider1.equals(provider2)) {
      return provider1;
    }

    if (canHandleRouting(provider1, backend2.getPath(path2String))) {
      return provider1;
    }

    if (canHandleRouting(provider2, backend1.getPath(path1String))) {
      return provider2;
    }

    throw new IllegalArgumentException(String.format("Provider mismatch: %s != %s", provider1, provider2));
  }

  private static boolean canHandleRouting(FileSystemProvider provider, @NotNull Path path) {
    if (provider instanceof RoutingAwareFileSystemProvider rafsp) {
      // `instanceof` is still faster than a successful cache hit.
      // Even if `instanceof` misses, its negative impact is negligible. See a benchmark in the commit message.
      return rafsp.canHandleRouting(path);
    }
    return false;
  }

  @Contract("null -> null; !null -> !null")
  @Override
  @VisibleForTesting
  public @Nullable Path wrapDelegatePath(@Nullable Path delegatePath) {
    if (delegatePath == null) {
      return null;
    }
    else if (delegatePath instanceof MultiRoutingFsPath) {
      // `MultiRoutingFsPath` is encapsulated and can't be created outside this package.
      // Tricks with classloaders are not expected here.
      return delegatePath;
    }
    else {
      return new MultiRoutingFsPath(myFileSystem, delegatePath);
    }
  }

  @NotNull
  private static FileTypeDetector getDefaultFileTypeDetector() {
    try {
      return DefaultFileTypeDetector.create();
    }
    catch (Throwable e) {
      e.printStackTrace(System.err);
      return new FileTypeDetector() {
        @Override
        public String probeContentType(Path path) {
          return null;
        }
      };
    }
  }

  @NotNull
  static FileTypeDetector getFileTypeDetector(FileSystemProvider multiRoutingFileSystemProvider) {
    FileTypeDetector fileTypeDetector;
    // For some not clear reason {@code multiRoutingFileSystemProvider} usually appears to be from a different classloader
    if (multiRoutingFileSystemProvider instanceof MultiRoutingFileSystemProvider provider) {
      try {
        fileTypeDetector = provider.getFileTypeDetectorInternal();
      }
      catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
        e.printStackTrace(System.err);
        fileTypeDetector = getDefaultFileTypeDetector();
      }
    }
    else {
      fileTypeDetector = getDefaultFileTypeDetector();
    }
    return fileTypeDetector;
  }

  @SuppressWarnings("unused")
  private FileTypeDetector getFileTypeDetectorInternal() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    FileTypeDetector delegateDetector;
    Class<? extends FileSystemProvider> unixFileSystemProviderClass = getUnixFileSystemProviderClass();
    if (unixFileSystemProviderClass != null) {
      Method getFileTypeDetectorMethod = unixFileSystemProviderClass.getDeclaredMethod("getFileTypeDetector");
      getFileTypeDetectorMethod.setAccessible(true);
      delegateDetector = (FileTypeDetector)getFileTypeDetectorMethod.invoke(myLocalProvider);
    }
    else {
      // in windows, delegate detector is RegistryFileTypeDetector
      delegateDetector = getDefaultFileTypeDetector();
    }
    return new FileTypeDetector() {
      @Override
      public String probeContentType(Path path) throws IOException {
        return delegateDetector.probeContentType(toDelegatePath(path));
      }
    };
  }

  /**
   * @return {@code sun.nio.fs.UnixFileSystemProvider.class} in unix, {@code null} in windows
   */
  private @Nullable Class<? extends FileSystemProvider> getUnixFileSystemProviderClass() {
    Class<? extends FileSystemProvider> unixFileSystemProviderClass = myLocalProvider.getClass();
    while (unixFileSystemProviderClass != null && !"sun.nio.fs.UnixFileSystemProvider".equals(unixFileSystemProviderClass.getName())) {
      Class<?> superclass = unixFileSystemProviderClass.getSuperclass();
      if (FileSystemProvider.class.isAssignableFrom(superclass)) {
        unixFileSystemProviderClass = superclass.asSubclass(FileSystemProvider.class);
      } else {
        unixFileSystemProviderClass = null;
      }
    }
    return unixFileSystemProviderClass;
  }

  @Contract("null -> null; !null -> !null")
  @Override
  public @Nullable Path toDelegatePath(@Nullable Path path) {
    if (path instanceof MultiRoutingFsPath) {
      // `MultiRoutingFsPath` is encapsulated and can't be created outside this package.
      // Tricks with classloaders are not expected here.
      return ((MultiRoutingFsPath)path).getCurrentDelegate();
    }
    else {
      return path;
    }
  }
}
