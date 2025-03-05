// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.core.nio.fs;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 * Allows extending some {@link FileSystemProvider} via composition instead of inheritance.
 * The inheritor of the class defines {@link #getDelegate(Path, Path)}.
 * That delegate is used for all file system operations.
 */
public abstract class DelegatingFileSystemProvider<
  P extends DelegatingFileSystemProvider<P, F>,
  F extends DelegatingFileSystem<P>
  > extends FileSystemProvider {

  /**
   * @return A delegating file system which is bound to this provider.
   */
  protected abstract @NotNull F wrapDelegateFileSystem(@NotNull FileSystem delegateFs);

  /**
   * @param path1 A path passed to some method of {@link FileSystemProvider}.
   *              Can be null if the method doesn't accept any path, like {@link #newFileSystem(URI, Map)} or {@link #getPath(URI)}
   * @param path2 A path passes as a second argument to some method, like in {@link #copy(Path, Path, CopyOption...)}.
   *              It is null for methods that accept one or zero paths, like {@link #checkAccess(Path, AccessMode...)}.
   * @return The original delegate, which should be used for actual operations.
   */
  protected abstract @NotNull FileSystemProvider getDelegate(@Nullable Path path1, @Nullable Path path2);

  /**
   * @param path A path that the original file system accepts and produces.
   * @return Some wrapped path that {@link DelegatingFileSystem} and {@link DelegatingFileSystemProvider} expect.
   */
  @Contract("null -> null; !null -> !null")
  protected abstract @Nullable Path toDelegatePath(@Nullable Path path);

  /**
   * @param path A wrapped path that {@link DelegatingFileSystem} and {@link DelegatingFileSystemProvider} expect.
   * @return A path that the original file system accepts and produces.
   */
  @Contract("null -> null; !null -> !null")
  protected abstract @Nullable Path fromDelegatePath(@Nullable Path path);

  @Override
  public @Nullable F newFileSystem(Path path, Map<String, ?> env) throws IOException {
    return wrapDelegateFileSystem(getDelegate(path, null).newFileSystem(fromDelegatePath(path), env));
  }

  @Override
  public InputStream newInputStream(Path path, OpenOption... options) throws IOException {
    return getDelegate(path, null).newInputStream(fromDelegatePath(path), options);
  }

  @Override
  public OutputStream newOutputStream(Path path, OpenOption... options) throws IOException {
    return getDelegate(path, null).newOutputStream(fromDelegatePath(path), options);
  }

  @Override
  public FileChannel newFileChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
    return getDelegate(path, null).newFileChannel(fromDelegatePath(path), options, attrs);
  }

  @Override
  public AsynchronousFileChannel newAsynchronousFileChannel(
    Path path,
    Set<? extends OpenOption> options,
    ExecutorService executor,
    FileAttribute<?>... attrs
  ) throws IOException {
    return getDelegate(path, null).newAsynchronousFileChannel(fromDelegatePath(path), options, executor, attrs);
  }

  @Override
  public void createSymbolicLink(Path link, Path target, FileAttribute<?>... attrs) throws IOException {
    getDelegate(link, target).createSymbolicLink(fromDelegatePath(link), fromDelegatePath(target), attrs);
  }

  @Override
  public void createLink(Path link, Path existing) throws IOException {
    getDelegate(link, existing).createLink(fromDelegatePath(link), fromDelegatePath(existing));
  }

  @Override
  public boolean deleteIfExists(Path path) throws IOException {
    return getDelegate(path, null).deleteIfExists(fromDelegatePath(path));
  }

  @Override
  public Path readSymbolicLink(Path link) throws IOException {
    return toDelegatePath(getDelegate(link, null).readSymbolicLink(fromDelegatePath(link)));
  }

  @Override
  public String getScheme() {
    return getDelegate(null, null).getScheme();
  }

  @Override
  public @Nullable F newFileSystem(URI uri, Map<String, ?> env) throws IOException {
    return wrapDelegateFileSystem(getDelegate(null, null).newFileSystem(uri, env));
  }

  @Override
  public @NotNull F getFileSystem(URI uri) {
    return wrapDelegateFileSystem(getDelegate(null, null).getFileSystem(uri));
  }

  @Override
  public @NotNull Path getPath(@NotNull URI uri) {
    return toDelegatePath(getDelegate(null, null).getPath(uri));
  }

  @Override
  public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
    return getDelegate(path, null).newByteChannel(fromDelegatePath(path), options, attrs);
  }

  @Override
  public DirectoryStream<Path> newDirectoryStream(Path dir, @Nullable DirectoryStream.Filter<? super Path> filter) throws IOException {
    return new DirectoryStream<Path>() {
      final DirectoryStream<Path> myStream = getDelegate(dir, null)
        .newDirectoryStream(
          fromDelegatePath(dir),
          craftFilter(filter)
        );

      @Override
      public Iterator<Path> iterator() {
        return new Iterator<Path>() {
          final Iterator<Path> myIterator = myStream.iterator();

          @Override
          public boolean hasNext() {
            return myIterator.hasNext();
          }

          @Override
          public Path next() {
            return toDelegatePath(myIterator.next());
          }
        };
      }

      @Override
      public void close() throws IOException {
        myStream.close();
      }
    };
  }

  @Contract("null -> null; !null -> !null")
  private @Nullable DirectoryStream.Filter<? super Path> craftFilter(@Nullable DirectoryStream.Filter<? super Path> originalFilter) {
    if (originalFilter == null) {
      return null;
    }
    if (BasicFileAttributesHolder2.FetchAttributesFilter.isFetchAttributesFilter(originalFilter)) {
      return (BasicFileAttributesHolder2.FetchAttributesFilter)p -> originalFilter.accept(fromDelegatePath(p));
    }
    else {
      return p -> originalFilter.accept(fromDelegatePath(p));
    }
  }

  @Override
  public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
    getDelegate(dir, null).createDirectory(fromDelegatePath(dir), attrs);
  }

  @Override
  public void delete(Path path) throws IOException {
    getDelegate(path, null).delete(fromDelegatePath(path));
  }

  @Override
  public void copy(Path source, Path target, CopyOption... options) throws IOException {
    getDelegate(source, target).copy(fromDelegatePath(source), fromDelegatePath(target), options);
  }

  @Override
  public void move(Path source, Path target, CopyOption... options) throws IOException {
    getDelegate(source, target).move(fromDelegatePath(source), fromDelegatePath(target), options);
  }

  @Override
  public boolean isSameFile(Path path, Path path2) throws IOException {
    return getDelegate(path, path2).isSameFile(fromDelegatePath(path), fromDelegatePath(path2));
  }

  @Override
  public boolean isHidden(Path path) throws IOException {
    return getDelegate(path, null).isHidden(fromDelegatePath(path));
  }

  @Override
  public FileStore getFileStore(Path path) throws IOException {
    return getDelegate(path, null).getFileStore(fromDelegatePath(path));
  }

  @Override
  public void checkAccess(Path path, AccessMode... modes) throws IOException {
    getDelegate(path, null).checkAccess(fromDelegatePath(path), modes);
  }

  @Override
  public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
    return getDelegate(path, null).getFileAttributeView(fromDelegatePath(path), type, options);
  }

  @Override
  public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options) throws IOException {
    return getDelegate(path, null).readAttributes(fromDelegatePath(path), type, options);
  }

  @Override
  public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
    return getDelegate(path, null).readAttributes(fromDelegatePath(path), attributes, options);
  }

  @Override
  public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
    getDelegate(path, null).setAttribute(fromDelegatePath(path), attribute, value, options);
  }

  /** Used in {@link sun.nio.ch.UnixDomainSockets#getPathBytes}. */
  @SuppressWarnings("unused")
  public byte[] getSunPathForSocketFile(Path path) {
    // TODO Accessor?
    FileSystemProvider provider = getDelegate(path, null);
    Path path1 = path;
    if (path1 instanceof CorePath) {
      path1 = ((CorePath)path1).getDelegate();
    }
    else if (path1 instanceof MultiRoutingFsPath) {
      path1 = ((MultiRoutingFsPath)path1).getDelegate();
    }
    try {
      Method method = provider.getClass().getMethod("getSunPathForSocketFile", Path.class);
      method.setAccessible(true);
      Object result = method.invoke(provider, path1);
      return (byte[])result;
    }
    catch (NoSuchMethodException | SecurityException e) {
      // This should've been verified when UnixDomainSocketAddress was created
      throw new Error("Can't find getSunPathForSocketFile(Path) in the non-default file system provider " + provider.getClass());
    }
    catch (InvocationTargetException | IllegalAccessException e) {
      throw new RuntimeException("Can't invoke getSunPathForSocketFile(Path) from a non-default file system provider", e);
    }
  }
}
