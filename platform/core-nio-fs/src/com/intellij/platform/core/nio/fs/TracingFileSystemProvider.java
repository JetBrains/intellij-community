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

  @SuppressWarnings({"rawtypes", "unchecked"})
  private FileSystemTracingListener<Object> myListener = (FileSystemTracingListener) new FileSystemTracingListener.NoopFileSystemTracingListener();

  @SuppressWarnings({"rawtypes", "unchecked"})
  public void setTraceListener(FileSystemTracingListener<?> listener) {
    myListener = (FileSystemTracingListener) listener;
  }

  @Override
  public void createSymbolicLink(Path link, Path target, FileAttribute<?>... attrs) throws IOException {
    FileSystemProvider delegate = getDelegate(link, target);
    Object token = myListener.providerCreateSymbolicLinkStarted(delegate, link, target, attrs);
    try {
      delegate.createSymbolicLink(toDelegatePath(link), toDelegatePath(target), attrs);
      myListener.providerGenericReturn(token);
    }
    catch (Throwable err) {
      myListener.providerGenericError(delegate, err);
      throw err;
    }
  }

  @Override
  public void createLink(Path link, Path existing) throws IOException {
    FileSystemProvider delegate = getDelegate(link, existing);
    Object token = myListener.providerCreateLinkStarted(delegate, link, existing);
    try {
      delegate.createLink(toDelegatePath(link), toDelegatePath(existing));
      myListener.providerGenericReturn(token);
    }
    catch (Throwable err) {
      myListener.providerGenericError(delegate, err);
      throw err;
    }
  }

  @Override
  public boolean deleteIfExists(Path path) throws IOException {
    FileSystemProvider delegate = getDelegate(path, null);
    Object token = myListener.providerDeleteIfExistsStarted(delegate, path);
    try {
      boolean result = delegate.deleteIfExists(toDelegatePath(path));
      myListener.providerGenericReturn(token);
      return result;
    }
    catch (Throwable err) {
      myListener.providerGenericError(delegate, err);
      throw err;
    }
  }

  @Override
  public Path readSymbolicLink(Path link) throws IOException {
    FileSystemProvider delegate = getDelegate(link, null);
    Object token = myListener.providerReadSymbolicLinkStarted(delegate, link);
    try {
      Path result = wrapDelegatePath(delegate.readSymbolicLink(toDelegatePath(link)));
      myListener.providerGenericReturn(token);
      return result;
    }
    catch (Throwable err) {
      myListener.providerGenericError(delegate, err);
      throw err;
    }
  }

  @Override
  public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
    FileSystemProvider delegate = getDelegate(path, null);
    Object token = myListener.providerNewByteChannelStarted(delegate, path, options, attrs);
    try {
      SeekableByteChannel result = delegate.newByteChannel(toDelegatePath(path), options, attrs);
      SeekableByteChannel wrappedResult = myListener.providerNewByteChannelReturn(token, result);
      return wrappedResult;
    }
    catch (Throwable err) {
      myListener.providerGenericError(token, err);
      throw err;
    }
  }

  @Override
  public DirectoryStream<Path> newDirectoryStream(Path dir, @Nullable DirectoryStream.Filter<? super Path> filter) throws IOException {
    FileSystemProvider delegate = getDelegate(dir, null);
    Object token = myListener.providerNewDirectoryStreamStarted(delegate, dir, filter);
    try {
      var result = delegateNewDirectoryStream(delegate, dir, filter);
      DirectoryStream<Path> wrappedResult = myListener.providerNewDirectoryStreamReturn(token, result);
      return wrappedResult;
    }
    catch (Throwable err) {
      myListener.providerGenericError(token, err);
      throw err;
    }
  }

  @Override
  public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
    FileSystemProvider delegate = getDelegate(dir, null);
    Object token = myListener.providerCreateDirectoryStarted(delegate, dir, attrs);
    try {
      delegate.createDirectory(toDelegatePath(dir), attrs);
      myListener.providerGenericReturn(token);
    }
    catch (Throwable err) {
      myListener.providerGenericError(token, err);
      throw err;
    }
  }

  @Override
  public void delete(Path path) throws IOException {
    FileSystemProvider delegate = getDelegate(path, null);
    Object token = myListener.providerDeleteStarted(delegate, path);
    try {
      delegate.delete(toDelegatePath(path));
      myListener.providerGenericReturn(token);
    }
    catch (Throwable err) {
      myListener.providerGenericError(token, err);
      throw err;
    }
  }

  @Override
  public void copy(Path source, Path target, CopyOption... options) throws IOException {
    FileSystemProvider delegate = getDelegate(source, target);
    Object token = myListener.providerCopyStarted(delegate, source, target, options);
    try {
      delegate.copy(toDelegatePath(source), toDelegatePath(target), options);
      myListener.providerGenericReturn(token);
    }
    catch (Throwable err) {
      myListener.providerGenericError(token, err);
      throw err;
    }
  }

  @Override
  public void move(Path source, Path target, CopyOption... options) throws IOException {
    FileSystemProvider delegate = getDelegate(source, target);
    Object token = myListener.providerMoveStarted(delegate, source, target, options);
    try {
      delegate.move(toDelegatePath(source), toDelegatePath(target), options);
      myListener.providerGenericReturn(token);
    }
    catch (Throwable err) {
      myListener.providerGenericError(token, err);
      throw err;
    }
  }

  @Override
  public boolean isSameFile(Path path, Path path2) throws IOException {
    FileSystemProvider delegate = getDelegate(path, path2);
    Object token = myListener.providerIsSameFileStarted(delegate, path, path2);
    try {
      boolean result = delegate.isSameFile(toDelegatePath(path), toDelegatePath(path2));
      myListener.providerGenericReturn(token);
      return result;
    }
    catch (Throwable err) {
      myListener.providerGenericError(token, err);
      throw err;
    }
  }

  @Override
  public boolean isHidden(Path path) throws IOException {
    FileSystemProvider delegate = getDelegate(path, null);
    Object token = myListener.providerIsHiddenStarted(delegate, path);
    try {
      boolean result = delegate.isHidden(toDelegatePath(path));
      myListener.providerGenericReturn(token);
      return result;
    }
    catch (Throwable err) {
      myListener.providerGenericError(token, err);
      throw err;
    }
  }

  @Override
  public FileStore getFileStore(Path path) throws IOException {
    FileSystemProvider delegate = getDelegate(path, null);
    Object token = myListener.providerGetFileStoreStarted(delegate, path);
    try {
      FileStore result = delegate.getFileStore(toDelegatePath(path));
      myListener.providerGenericReturn(token);
      return result;
    }
    catch (Throwable err) {
      myListener.providerGenericError(token, err);
      throw err;
    }
  }

  @Override
  public void checkAccess(Path path, AccessMode... modes) throws IOException {
    FileSystemProvider delegate = getDelegate(path, null);
    Object token = myListener.providerCheckAccessStarted(delegate, path, modes);
    try {
      delegate.checkAccess(toDelegatePath(path), modes);
      myListener.providerGenericReturn(token);
    }
    catch (Throwable err) {
      myListener.providerGenericError(token, err);
      throw err;
    }
  }

  @Override
  public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
    FileSystemProvider delegate = getDelegate(path, null);
    Object token = myListener.providerGetFileAttributeViewStarted(delegate, path, type, options);
    try {
      V result = delegate.getFileAttributeView(toDelegatePath(path), type, options);
      myListener.providerGenericReturn(token);
      return result;
    }
    catch (Throwable err) {
      myListener.providerGenericError(token, err);
      throw err;
    }
  }

  @Override
  public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options) throws IOException {
    FileSystemProvider delegate = getDelegate(path, null);
    Object token = myListener.providerReadAttributesStarted(delegate, path, type, options);
    try {
      A result = delegate.readAttributes(toDelegatePath(path), type, options);
      myListener.providerGenericReturn(token);
      return result;
    }
    catch (Throwable err) {
      myListener.providerGenericError(token, err);
      throw err;
    }
  }

  @Override
  public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
    FileSystemProvider delegate = getDelegate(path, null);
    Object token = myListener.providerReadAttributesStarted(delegate, path, attributes, options);
    try {
      Map<String, Object> result = delegate.readAttributes(toDelegatePath(path), attributes, options);
      myListener.providerGenericReturn(token);
      return result;
    }
    catch (Throwable err) {
      myListener.providerGenericError(token, err);
      throw err;
    }
  }

  @Override
  public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
    FileSystemProvider delegate = getDelegate(path, null);
    Object token = myListener.providerSetAttributeStarted(delegate, path, attribute, value, options);
    try {
      delegate.setAttribute(toDelegatePath(path), attribute, value, options);
      myListener.providerGenericReturn(token);
    }
    catch (Throwable err) {
      myListener.providerGenericError(token, err);
      throw err;
    }
  }
}
