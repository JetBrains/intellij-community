// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.core.nio.fs;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.Map;
import java.util.Set;

public abstract class TracingFileSystemProvider<
  P extends TracingFileSystemProvider<P, F>,
  F extends DelegatingFileSystem<P>
  > extends DelegatingFileSystemProvider<P, F> {

  private FileSystemTracingListener myListener = new FileSystemTracingListener.NoopFileSystemTracingListener();

  public void setTraceListener(FileSystemTracingListener listener) {
    myListener = listener;
  }

  @Override
  public void createSymbolicLink(Path link, Path target, FileAttribute<?>... attrs) throws IOException {
    FileSystemProvider delegate = getDelegate(link, target);
    myListener.providerCreateSymbolicLinkStarted(delegate, link, target, attrs);
    try {
      delegate.createSymbolicLink(toDelegatePath(link), toDelegatePath(target), attrs);
      myListener.providerCreateSymbolicLinkReturn(delegate, link, target, attrs);
    }
    catch (IOException err) {
      myListener.providerCreateSymbolicLinkError(delegate, err, link, target, attrs);
      throw err;
    }
  }

  @Override
  public void createLink(Path link, Path existing) throws IOException {
    FileSystemProvider delegate = getDelegate(link, existing);
    myListener.providerCreateLinkStarted(delegate, link, existing);
    try {
      delegate.createLink(toDelegatePath(link), toDelegatePath(existing));
      myListener.providerCreateLinkReturn(delegate, link, existing);
    }
    catch (IOException err) {
      myListener.providerCreateLinkError(delegate, err, link, existing);
      throw err;
    }
  }

  @Override
  public boolean deleteIfExists(Path path) throws IOException {
    FileSystemProvider delegate = getDelegate(path, null);
    myListener.providerDeleteIfExistsStarted(delegate, path);
    try {
      boolean result = delegate.deleteIfExists(toDelegatePath(path));
      myListener.providerDeleteIfExistsReturn(delegate, result, path);
      return result;
    }
    catch (IOException err) {
      myListener.providerDeleteIfExistsError(delegate, err, path);
      throw err;
    }
  }

  @Override
  public Path readSymbolicLink(Path link) throws IOException {
    FileSystemProvider delegate = getDelegate(link, null);
    myListener.providerReadSymbolicLinkStarted(delegate, link);
    try {
      Path result = wrapDelegatePath(delegate.readSymbolicLink(toDelegatePath(link)));
      myListener.providerReadSymbolicLinkReturn(delegate, result, link);
      return result;
    }
    catch (IOException err) {
      myListener.providerReadSymbolicLinkError(delegate, err, link);
      throw err;
    }
  }

  @Override
  public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
    FileSystemProvider delegate = getDelegate(path, null);
    myListener.providerNewByteChannelStarted(delegate, path, options, attrs);
    try {
      SeekableByteChannel result = delegate.newByteChannel(toDelegatePath(path), options, attrs);
      SeekableByteChannel wrappedResult = myListener.providerNewByteChannelReturn(delegate, result, path, options, attrs);
      return wrappedResult;
    }
    catch (IOException err) {
      myListener.providerNewByteChannelError(delegate, err, path, options, attrs);
      throw err;
    }
  }

  @Override
  public DirectoryStream<Path> newDirectoryStream(Path dir, @Nullable DirectoryStream.Filter<? super Path> filter) throws IOException {
    FileSystemProvider delegate = getDelegate(dir, null);
    myListener.providerNewDirectoryStreamStarted(delegate, dir, filter);
    try {
      var result = delegateNewDirectoryStream(delegate, dir, filter);
      DirectoryStream<Path> wrappedResult = myListener.providerNewDirectoryStreamReturn(delegate, result, dir, filter);
      return wrappedResult;
    }
    catch (IOException err) {
      myListener.providerNewDirectoryStreamError(delegate, err, dir, filter);
      throw err;
    }
  }

  @Override
  public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
    FileSystemProvider delegate = getDelegate(dir, null);
    myListener.providerCreateDirectoryStarted(delegate, dir, attrs);
    try {
      delegate.createDirectory(toDelegatePath(dir), attrs);
      myListener.providerCreateDirectoryReturn(delegate, dir, attrs);
    }
    catch (IOException err) {
      myListener.providerCreateDirectoryError(delegate, err, dir, attrs);
      throw err;
    }
  }

  @Override
  public void delete(Path path) throws IOException {
    FileSystemProvider delegate = getDelegate(path, null);
    myListener.providerDeleteStarted(delegate, path);
    try {
      delegate.delete(toDelegatePath(path));
      myListener.providerDeleteReturn(delegate, path);
    }
    catch (IOException err) {
      myListener.providerDeleteError(delegate, err, path);
      throw err;
    }
  }

  @Override
  public void copy(Path source, Path target, CopyOption... options) throws IOException {
    FileSystemProvider delegate = getDelegate(source, target);
    myListener.providerCopyStarted(delegate, source, target, options);
    try {
      delegate.copy(toDelegatePath(source), toDelegatePath(target), options);
      myListener.providerCopyReturn(delegate, source, target, options);
    }
    catch (IOException err) {
      myListener.providerCopyError(delegate, err, source, target, options);
      throw err;
    }
  }

  @Override
  public void move(Path source, Path target, CopyOption... options) throws IOException {
    FileSystemProvider delegate = getDelegate(source, target);
    myListener.providerMoveStarted(delegate, source, target, options);
    try {
      delegate.move(toDelegatePath(source), toDelegatePath(target), options);
      myListener.providerMoveReturn(delegate, source, target, options);
    }
    catch (IOException err) {
      myListener.providerMoveError(delegate, err, source, target, options);
      throw err;
    }
  }

  @Override
  public boolean isSameFile(Path path, Path path2) throws IOException {
    FileSystemProvider delegate = getDelegate(path, path2);
    myListener.providerIsSameFileStarted(delegate, path, path2);
    try {
      boolean result = delegate.isSameFile(toDelegatePath(path), toDelegatePath(path2));
      myListener.providerIsSameFileReturn(delegate, result, path, path2);
      return result;
    }
    catch (IOException err) {
      myListener.providerIsSameFileError(delegate, err, path, path2);
      throw err;
    }
  }

  @Override
  public boolean isHidden(Path path) throws IOException {
    FileSystemProvider delegate = getDelegate(path, null);
    myListener.providerIsHiddenStarted(delegate, path);
    try {
      boolean result = delegate.isHidden(toDelegatePath(path));
      myListener.providerIsHiddenReturn(delegate, result, path);
      return result;
    }
    catch (IOException err) {
      myListener.providerIsHiddenError(delegate, err, path);
      throw err;
    }
  }

  @Override
  public FileStore getFileStore(Path path) throws IOException {
    FileSystemProvider delegate = getDelegate(path, null);
    myListener.providerGetFileStoreStarted(delegate, path);
    try {
      FileStore result = delegate.getFileStore(toDelegatePath(path));
      myListener.providerGetFileStoreReturn(delegate, result, path);
      return result;
    }
    catch (IOException err) {
      myListener.providerGetFileStoreError(delegate, err, path);
      throw err;
    }
  }

  @Override
  public void checkAccess(Path path, AccessMode... modes) throws IOException {
    FileSystemProvider delegate = getDelegate(path, null);
    myListener.providerCheckAccessStarted(delegate, path, modes);
    try {
      delegate.checkAccess(toDelegatePath(path), modes);
      myListener.providerCheckAccessReturn(delegate, path, modes);
    }
    catch (IOException err) {
      myListener.providerCheckAccessError(delegate, err, path, modes);
      throw err;
    }
  }

  @Override
  public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
    FileSystemProvider delegate = getDelegate(path, null);
    myListener.providerGetFileAttributeViewStarted(delegate, path, type, options);
    V result = delegate.getFileAttributeView(toDelegatePath(path), type, options);
    myListener.providerGetFileAttributeViewReturn(delegate, result, path, type, options);
    return result;
  }

  @Override
  public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options) throws IOException {
    FileSystemProvider delegate = getDelegate(path, null);
    myListener.providerReadAttributesStarted(delegate, path, type, options);
    try {
      A result = delegate.readAttributes(toDelegatePath(path), type, options);
      myListener.providerReadAttributesReturn(delegate, result, path, type, options);
      return result;
    }
    catch (IOException err) {
      myListener.providerReadAttributesError(delegate, err, path, type, options);
      throw err;
    }
  }

  @Override
  public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
    FileSystemProvider delegate = getDelegate(path, null);
    myListener.providerReadAttributesStarted(delegate, path, attributes, options);
    try {
      Map<String, Object> result = delegate.readAttributes(toDelegatePath(path), attributes, options);
      myListener.providerReadAttributesReturn(delegate, result, path, attributes, options);
      return result;
    }
    catch (IOException err) {
      myListener.providerReadAttributesError(delegate, err, path, attributes, options);
      throw err;
    }
  }

  @Override
  public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
    FileSystemProvider delegate = getDelegate(path, null);
    myListener.providerSetAttributeStarted(delegate, path, attribute, value, options);
    try {
      delegate.setAttribute(toDelegatePath(path), attribute, value, options);
      myListener.providerSetAttributeReturn(delegate, path, attribute, value, options);
    }
    catch (IOException err) {
      myListener.providerSetAttributeError(delegate, err, path, attribute, value, options);
      throw err;
    }
  }
}
