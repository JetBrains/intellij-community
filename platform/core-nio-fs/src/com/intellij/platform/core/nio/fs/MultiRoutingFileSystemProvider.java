// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.core.nio.fs;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import sun.nio.fs.DefaultFileTypeDetector;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.spi.FileSystemProvider;
import java.nio.file.spi.FileTypeDetector;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;

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

  volatile BiConsumer<String, Boolean> myPathSpy = (ignored, ignored2) -> {};

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
      arguments.put(KEY_MRFS, Void.TYPE);
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

  public static void setPathSpy(
    @NotNull FileSystemProvider provider,
    @NotNull BiConsumer<@NotNull String, @NotNull Boolean> pathSpy
  ) {
    if (provider.getClass().getName().equals(MultiRoutingFileSystemProvider.class.getName())) {
      try {
        provider.getClass().getMethod("setPathSpy", BiConsumer.class).invoke(provider, pathSpy);
      }
      catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
        throw new RuntimeException(e);
      }
    }
    else {
      throw new IllegalArgumentException(String.format("%s is not an instance of %s", provider, MultiRoutingFileSystemProvider.class));
    }
  }

  public void setPathSpy(@NotNull BiConsumer<String, Boolean> pathSpy) {
    myPathSpy = pathSpy;
  }

  public MultiRoutingFileSystemProvider(FileSystemProvider localFSProvider) {
    myLocalProvider = localFSProvider;
    myFileSystem = new MultiRoutingFileSystem(this, myLocalProvider.getFileSystem(URI.create("file:///")));
  }

  @Override
  protected @NotNull MultiRoutingFileSystem wrapDelegateFileSystem(@NotNull FileSystem delegateFs) {
    return new MultiRoutingFileSystem(this, delegateFs);
  }

  @Override
  public @Nullable MultiRoutingFileSystem newFileSystem(Path path, @Nullable Map<String, ?> env) {
    throw new UnsupportedOperationException(MultiRoutingFileSystemProvider.class.getName() + " doesn't open other files as filesystems");
  }

  @Override
  public @Nullable MultiRoutingFileSystem newFileSystem(URI uri, @Nullable Map<String, ?> env) {
    if (env == null || !env.containsKey(KEY_MRFS)) {
      throw new UnsupportedOperationException(
        MultiRoutingFileSystem.class.getName() + " can be created only with `" +
        MultiRoutingFileSystemProvider.class.getName() + ".computeBackend()`." +
        " Otherwise, this file system provider behaves as a default file system provider and throws an error."
      );
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

  private static final String KEY_MRFS = "MRFS";
  private static final String KEY_ROOT = "KEY_ROOT";
  private static final String KEY_PREFIX = "KEY_PREFIX";
  private static final String KEY_CASE_SENSITIVE = "KEY_CASE_SENSITIVE";
  private static final String KEY_FUNCTION = "KEY_FUNCTION";

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
      myPathSpy.accept(path1String, provider1 != this);
      return provider1;
    }

    if (!path2.isAbsolute()) {
      path2 = path2.toAbsolutePath();
    }

    String path2String = path2.toString();
    FileSystem backend2 = myFileSystem.getBackend(path2String);
    FileSystemProvider provider2 = backend2.provider();

    if (provider1.equals(provider2)) {
      myPathSpy.accept(path1String, provider1 != this);
      return provider1;
    }

    if (canHandleRouting(provider1, backend2.getPath(path2String))) {
      myPathSpy.accept(path1String, provider1 != this);
      return provider1;
    }

    if (canHandleRouting(provider2, backend1.getPath(path1String))) {
      myPathSpy.accept(path2String, provider2 != this);
      return provider2;
    }

    throw new IllegalArgumentException(String.format("Provider mismatch: %s != %s", provider1, provider2));
  }

  /**
   * `intellij.platform.util` is not available in the boot classpath.
   * Hence, concurrent weak maps from the platform can't be used here.
   */
  private static final Map<FileSystemProvider, Optional<Method>> ourCanHandleRoutingCache = Collections.synchronizedMap(new WeakHashMap<>());

  private static boolean canHandleRouting(FileSystemProvider provider, @NotNull Path path) {
    if (provider instanceof RoutingAwareFileSystemProvider) {
      // `instanceof` is still faster than a successful cache hit.
      // Even if `instanceof` misses, its negative impact is negligible. See a benchmark in the commit message.
      return ((RoutingAwareFileSystemProvider)provider).canHandleRouting(path);
    }
    Method method = ourCanHandleRoutingCache
      .computeIfAbsent(provider, MultiRoutingFileSystemProvider::canHandleRoutingImpl)
      .orElse(null);
    if (method == null) {
      return false;
    }
    try {
      return (boolean)method.invoke(provider, path);
    }
    catch (IllegalAccessException | InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * It often happens with such low-level things like this one that some class/interface gets loaded by two different classloaders:
   * <ul>
   *   <li>These particular classes are injected into the boot classpath.</li>
   *   <li>The classes are loaded by {#link com.intellij.util.lang.PathClassLoader} again.</li>
   * </ul>
   * Therefore, the usual expression {@code a instanceof B} doesn't work when {@code a} is an instance of {@code B} loaded by
   * a different classloader.
   */
  private static Optional<Method> canHandleRoutingImpl(FileSystemProvider provider) {
    Class<?> providerClass = provider.getClass();
    do {
      for (Class<?> iface : providerClass.getInterfaces()) {
        if (iface.getName().equals(RoutingAwareFileSystemProvider.class.getName())) {
          try {
            return Optional.of(iface.getMethod("canHandleRouting"));
          }
          catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
          }
        }
      }
      providerClass = providerClass.getSuperclass();
    }
    while (providerClass != null);
    return Optional.empty();
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
    else if (multiRoutingFileSystemProvider.getClass().getName().equals(MultiRoutingFileSystemProvider.class.getName())) {
      try {
        Method method = multiRoutingFileSystemProvider.getClass().getDeclaredMethod("getFileTypeDetectorInternal");
        method.setAccessible(true);
        fileTypeDetector = (FileTypeDetector)method.invoke(multiRoutingFileSystemProvider);
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
      return ((MultiRoutingFsPath)path).getDelegate();
    }
    else {
      return path;
    }
  }
}
