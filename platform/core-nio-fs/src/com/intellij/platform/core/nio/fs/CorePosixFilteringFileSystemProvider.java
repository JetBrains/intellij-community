// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.core.nio.fs;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 * {@link FileSystemProvider} facade that silently ignores posix file attributes, so that {@link CoreRoutingFileSystem} can advertise
 * blanket posix support, even when the local file system does not offer it and would otherwise throw exceptions.
 * @see sun.nio.fs.WindowsSecurityDescriptor#fromAttribute
 * @see TempFileHelper#create
 * @see TempFileHelper#isPosix
 * @see CoreRoutingFileSystem#supportedFileAttributeViews
 */
class CorePosixFilteringFileSystemProvider extends FileSystemProvider {
  private final FileSystemProvider myFileSystemProvider;
  CorePosixFilteringFileSystemProvider(FileSystemProvider provider) { myFileSystemProvider = provider; }

  private static FileAttribute<?>[] filterAttrs(FileAttribute<?>... attrs) {
    return Arrays.stream(attrs).filter(attr -> !attr.name().startsWith("posix:")).toArray(FileAttribute<?>[]::new);
  }

  @Override
  public FileSystem newFileSystem(URI uri, Map<String, ?> env) {
    throw new UnsupportedOperationException();
  }

  @Override
  public FileSystem getFileSystem(URI uri) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getScheme() {
    return myFileSystemProvider.getScheme();
  }

  @Override
  public Path getPath(URI uri) {
    return myFileSystemProvider.getPath(uri);
  }

  @Override
  public InputStream newInputStream(Path path, OpenOption... options) throws IOException {
    return myFileSystemProvider.newInputStream(path, options);
  }

  @Override
  public OutputStream newOutputStream(Path path, OpenOption... options) throws IOException {
    return myFileSystemProvider.newOutputStream(path, options);
  }

  @Override
  public FileChannel newFileChannel(Path path,
                                    Set<? extends OpenOption> options,
                                    FileAttribute<?>... attrs) throws IOException {
    return myFileSystemProvider.newFileChannel(path, options, filterAttrs(attrs));
  }

  @Override
  public AsynchronousFileChannel newAsynchronousFileChannel(Path path,
                                                            Set<? extends OpenOption> options,
                                                            ExecutorService executor,
                                                            FileAttribute<?>... attrs) throws IOException {
    return myFileSystemProvider.newAsynchronousFileChannel(path, options, executor, filterAttrs(attrs));
  }

  @Override
  public SeekableByteChannel newByteChannel(Path path,
                                            Set<? extends OpenOption> options,
                                            FileAttribute<?>... attrs) throws IOException {
    return myFileSystemProvider.newByteChannel(path, options, filterAttrs(attrs));
  }

  @Override
  public DirectoryStream<Path> newDirectoryStream(Path dir,
                                                  DirectoryStream.Filter<? super Path> filter) throws IOException {
    return myFileSystemProvider.newDirectoryStream(dir, filter);
  }

  @Override
  public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
    myFileSystemProvider.createDirectory(dir, filterAttrs(attrs));
  }

  @Override
  public void createSymbolicLink(Path link, Path target, FileAttribute<?>... attrs) throws IOException {
    myFileSystemProvider.createSymbolicLink(link, target, filterAttrs(attrs));
  }

  @Override
  public void createLink(Path link, Path existing) throws IOException {
    myFileSystemProvider.createLink(link, existing);
  }

  @Override
  public void delete(Path path) throws IOException {
    myFileSystemProvider.delete(path);
  }

  @Override
  public boolean deleteIfExists(Path path) throws IOException {
    return myFileSystemProvider.deleteIfExists(path);
  }

  @Override
  public Path readSymbolicLink(Path link) throws IOException {
    return myFileSystemProvider.readSymbolicLink(link);
  }

  @Override
  public void copy(Path source, Path target, CopyOption... options) throws IOException {
    myFileSystemProvider.copy(source, target, options);
  }

  @Override
  public void move(Path source, Path target, CopyOption... options) throws IOException {
    myFileSystemProvider.move(source, target, options);
  }

  @Override
  public boolean isSameFile(Path path, Path path2) throws IOException {
    return myFileSystemProvider.isSameFile(path, path2);
  }

  @Override
  public boolean isHidden(Path path) throws IOException {
    return myFileSystemProvider.isHidden(path);
  }

  @Override
  public FileStore getFileStore(Path path) throws IOException {
    return myFileSystemProvider.getFileStore(path);
  }

  @Override
  public void checkAccess(Path path, AccessMode... modes) throws IOException {
    myFileSystemProvider.checkAccess(path, modes);
  }

  @Override
  public <V extends FileAttributeView> V getFileAttributeView(Path path,
                                                              Class<V> type,
                                                              LinkOption... options) {
    return myFileSystemProvider.getFileAttributeView(path, type, options);
  }

  @Override
  public <A extends BasicFileAttributes> A readAttributes(Path path,
                                                          Class<A> type,
                                                          LinkOption... options) throws IOException {
    return myFileSystemProvider.readAttributes(path, type, options);
  }

  @Override
  public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
    return myFileSystemProvider.readAttributes(path, attributes, options);
  }

  @Override
  public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
    myFileSystemProvider.setAttribute(path, attribute, value, options);
  }
}
