// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.core.nio.fs;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.*;
import java.util.concurrent.ExecutorService;

public class CoreRoutingFileSystemProvider extends FileSystemProvider {
  public static final String SEPARATOR = "/";

  /** Key for signaling to {@link CoreRoutingFileSystemProvider} that it should initialize itself. */
  private static final String INITIALIZATION_KEY                     = "CoreRoutingFSInitialization";
  /** Key for signaling to {@link CoreRoutingFileSystemProvider} that it should eagerly initialize the mounted FS provider under lock as
   * well. */
  private static final String INITIALIZATION_MOUNTED_FS_PROVIDER_KEY = "RoutingFilesystemInitialization_MountedFSProvider";
  private static final String PROVIDER_CLASS_NAME                    = "ProviderClassName";
  private static final String PATH_CLASS_NAME                        = "PathClassName";
  private static final String MOUNTED_FS_PREFIX                      = "MountedFSPrefix";
  private static final String FILESYSTEM_CLASS_NAME                  = "FilesystemClassName";
  private static final String ROUTING_FILESYSTEM_DELEGATE_CLASS      = "RoutingFilesystemDelegateClass";

  private final Object myLock = new Object();
  private final FileSystemProvider    myLocalProvider;
  private final CoreRoutingFileSystem myFileSystem;
  private final boolean myUseContextClassLoader;

  private volatile FileSystemProvider myProvider;
  private volatile String myProviderClassName;

  public CoreRoutingFileSystemProvider(FileSystemProvider localFSProvider) {
    this(localFSProvider, true);
  }

  /**
   * @param useContextClassLoader Force {@link #createInstance(String, Class[], Object...)} use system class loader which is required when this class is used as default system provider
   */
  public CoreRoutingFileSystemProvider(FileSystemProvider localFSProvider, boolean useContextClassLoader) {
    FileSystem fileSystem = localFSProvider.getFileSystem(URI.create("file:///"));
    myLocalProvider = fileSystem.supportedFileAttributeViews().contains("posix")
                      ? localFSProvider
                      : new CorePosixFilteringFileSystemProvider(localFSProvider);
    myFileSystem = new CoreRoutingFileSystem(this, fileSystem);
    myUseContextClassLoader = useContextClassLoader;
  }

  @Override
  public FileSystem getFileSystem(URI uri) {
    return myFileSystem;
  }

  @Override
  public String getScheme() {
    return getProvider().getScheme();
  }

  @Override
  public FileSystem newFileSystem(URI uri, Map<String, ?> env) {
    if (env.get(INITIALIZATION_KEY) == Boolean.TRUE) {
      CoreRoutingFileSystem.setMountedFSPrefix((String)env.get(MOUNTED_FS_PREFIX));
      CorePath.setMountedDelegateClassName((String)env.get(PATH_CLASS_NAME));
      myProviderClassName = (String)env.get(PROVIDER_CLASS_NAME);
      if (env.get(INITIALIZATION_MOUNTED_FS_PROVIDER_KEY) == Boolean.TRUE) {
        synchronized (myLock) {
          //noinspection unchecked
          myFileSystem.initialize((String)env.get(FILESYSTEM_CLASS_NAME),
                                  (Class<? extends CoreRoutingFileSystemDelegate>)env.get(ROUTING_FILESYSTEM_DELEGATE_CLASS));
          getMountedFSProvider();
          return null;
        }
      }
      //noinspection unchecked
      myFileSystem.initialize((String)env.get(FILESYSTEM_CLASS_NAME),
                              (Class<? extends CoreRoutingFileSystemDelegate>)env.get(ROUTING_FILESYSTEM_DELEGATE_CLASS));
      return null;
    }
    throw new IllegalStateException("File system already exists");
  }


  /**
   * Initializes the passed {@link CoreRoutingFileSystemProvider} using the current context class loader.
   *
   * @param provider                    The {@link CoreRoutingFileSystemProvider}, which may have been loaded by a different class loader.
   * @param initializeMountedFSProvider Specifies whether to eagerly initialize the mounted FS provider under lock as well,
   *                                    e.g., in order to ensure the same class loader is used.
   * @see CoreRoutingFileSystemProvider#INITIALIZATION_KEY
   * @see CoreRoutingFileSystemProvider#INITIALIZATION_MOUNTED_FS_PROVIDER_KEY
   */
  public static void initialize(FileSystemProvider provider,
                                String providerClassName,
                                String pathClassName,
                                String mountedFSPrefix,
                                String filesystemClassName,
                                @Nullable Class<? extends CoreRoutingFileSystemDelegate> routingFilesystemDelegateClass,
                                boolean initializeMountedFSProvider) throws IOException {
    // Now we can use our provider. Initializing in such a hacky way because of different classloaders.
    Map<String, Object> map = new HashMap<>();
    map.put(INITIALIZATION_KEY, true);
    map.put(INITIALIZATION_MOUNTED_FS_PROVIDER_KEY, initializeMountedFSProvider);
    map.put(PROVIDER_CLASS_NAME, providerClassName);
    map.put(PATH_CLASS_NAME, pathClassName);
    map.put(MOUNTED_FS_PREFIX, mountedFSPrefix);
    map.put(FILESYSTEM_CLASS_NAME, filesystemClassName);
    map.put(ROUTING_FILESYSTEM_DELEGATE_CLASS, routingFilesystemDelegateClass);
    //noinspection resource
    provider.newFileSystem(URI.create("file:///"), map);
  }


  @Override
  public Path getPath(URI uri) {
    return path(getProvider(uri).getPath(uri));
  }

  @Override
  public Path readSymbolicLink(Path link) throws IOException {
    return path(getProvider(link).readSymbolicLink(unwrap(link)));
  }

  @Override
  public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
    return getProvider(path).newByteChannel(unwrap(path), options, attrs);
  }

  @Override
  public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
    DirectoryStream.Filter<? super Path> wrappedFilter = filter != null ? path -> filter.accept(path(path)) : null;
    @SuppressWarnings("resource") DirectoryStream<Path> stream = getProvider(dir).newDirectoryStream(unwrap(dir), wrappedFilter);
    return stream == null ? null : new DirectoryStream<Path>() {
      @Override
      public void close() throws IOException {
        stream.close();
      }

      @Override
      public Iterator<Path> iterator() {
        Iterator<Path> iterator = stream.iterator();
        return new Iterator<Path>() {
          @Override
          public boolean hasNext() {
            return iterator.hasNext();
          }

          @Override
          public Path next() {
            return path(iterator.next());
          }
        };
      }
    };
  }

  @Override
  public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
    getProvider(dir).createDirectory(unwrap(dir), attrs);
  }

  @Override
  public void delete(Path path) throws IOException {
    getProvider(path).delete(unwrap(path));
  }

  @Override
  public void copy(Path source, Path target, CopyOption... options) throws IOException {
    getProvider(source, target).copy(unwrap(source), unwrap(target), options);
  }

  @Override
  public void move(Path source, Path target, CopyOption... options) throws IOException {
    getProvider(source, target).move(unwrap(source), unwrap(target), options);
  }

  @Override
  public boolean isSameFile(Path path, Path path2) throws IOException {
    return getProvider(path, path2).isSameFile(unwrap(path), unwrap(path2));
  }

  @Override
  public boolean isHidden(Path path) throws IOException {
    return getProvider(path).isHidden(unwrap(path));
  }

  @Override
  public FileStore getFileStore(Path path) throws IOException {
    return getProvider(path).getFileStore(unwrap(path));
  }

  @Override
  public void checkAccess(Path path, AccessMode... modes) throws IOException {
    getProvider(path).checkAccess(unwrap(path), modes);
  }

  @Override
  public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
    return getProvider(path).getFileAttributeView(unwrap(path), type, options);
  }

  @Override
  public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options) throws IOException {
    return getProvider(path).readAttributes(unwrap(path), type, options);
  }

  @Override
  public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
    return getProvider(path).readAttributes(unwrap(path), attributes, options);
  }

  @Override
  public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
    getProvider(path).setAttribute(unwrap(path), attribute, value, options);
  }

  @Override
  public AsynchronousFileChannel newAsynchronousFileChannel(Path path,
                                                            Set<? extends OpenOption> options,
                                                            ExecutorService executor,
                                                            FileAttribute<?>... attrs) throws IOException {
    return tryGetLocalProvider(path).newAsynchronousFileChannel(unwrap(path), options, executor, attrs);
  }

  @Override
  public void createSymbolicLink(Path link, Path target, FileAttribute<?>... attrs) throws IOException {
    tryGetLocalProvider(link).createSymbolicLink(unwrap(link), unwrap(target), attrs);
  }

  @Override
  public void createLink(Path link, Path existing) throws IOException {
    tryGetLocalProvider(link).createLink(unwrap(link), unwrap(existing));
  }

  @Override
  public FileChannel newFileChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
    return getProvider(path).newFileChannel(unwrap(path), options, attrs);
  }

  /** Used in {@link sun.nio.ch.UnixDomainSockets#getPathBytes}. */
  @SuppressWarnings("unused")
  public byte[] getSunPathForSocketFile(Path path) {
    if (isMountedFSPath(path)) {
      throw new IllegalArgumentException(path.toString());
    }
    String jnuEncoding = System.getProperty("sun.jnu.encoding");
    try {
      return path.toString().getBytes(jnuEncoding);
    }
    catch (UnsupportedEncodingException e) {
      throw new IllegalStateException("sun.jnu.encoding=" + jnuEncoding, e);
    }
  }

  private boolean isInitialized() {
    return myFileSystem.isInitialized();
  }

  private FileSystemProvider tryGetLocalProvider(Path path) {
    if (isMountedFSPath(path)) throw new UnsupportedOperationException();
    return myLocalProvider;
  }

  private FileSystemProvider getProvider() {
    return isInitialized() ? getMountedFSProvider() : myLocalProvider;
  }

  private FileSystemProvider getProvider(URI uri) {
    return isInitialized() && isMountedFSURI(uri) ? getMountedFSProvider() : myLocalProvider;
  }

  private FileSystemProvider getProvider(Path path) {
    return isInitialized() && isMountedFSPath(path) ? getMountedFSProvider() : myLocalProvider;
  }

  private FileSystemProvider getProvider(Path... path) {
    FileSystemProvider provider = null;
    for (Path p : path) {
      FileSystemProvider current = getProvider(p);
      if (provider == null) {
        provider = current;
        continue;
      }
      if (current != provider) throw new IllegalArgumentException("Provider mismatch");
    }
    return Objects.requireNonNull(provider);
  }

  public FileSystemProvider getMountedFSProvider() {
    if (myProvider == null) {
      synchronized (myLock) {
        if (myProvider == null) {
          myProvider = createInstance(
            myProviderClassName,
            new Class[]{FileSystem.class},
            myFileSystem);
        }
      }
    }
    return myProvider;
  }

  protected Path path(Path path) {
    return path == null ? null : path(myFileSystem, path);
  }

  public static Path path(CoreRoutingFileSystem fileSystem, Path path) {
    return path instanceof CorePath ? path : new CorePath(fileSystem, path);
  }

  protected boolean isMountedFSPath(Path path) {
    return path instanceof CorePath && myFileSystem.isMountedFSPath((CorePath)path);
  }

  private static boolean isMountedFSURI(URI uri) {
    return uri != null && CoreRoutingFileSystem.isMountedFSFile(uri.getPath());
  }

  public static Path unwrap(Path path) {
    return path == null ? null : ((CorePath)path).getDelegate();
  }

  public <T> T createInstance(String className,
                              Class<?>[] paramClasses,
                              Object... params) {
    try {
      ClassLoader loader = (myUseContextClassLoader ? Thread.currentThread().getContextClassLoader()
                                                    : CoreRoutingFileSystemProvider.class.getClassLoader());
      String loaderName = loader.getClass().getName();
      if (!("com.intellij.util.lang.PathClassLoader").equals(loaderName) &&
          !("com.intellij.util.lang.UrlClassLoader").equals(loaderName) &&
          !("com.intellij.ide.plugins.cl.PluginClassLoader" /* Downburst */).equals(loaderName)) {
        throw new RuntimeException("Trying to initialize a mounted file system with wrong classloader: " + loader);
      }
      Class<?> loaded = loader.loadClass(className);
      //noinspection unchecked
      return (T)loaded.getConstructor(paramClasses).newInstance(params);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static String normalizePath(String path) {
    return path.replace("\\", SEPARATOR);
  }
}
