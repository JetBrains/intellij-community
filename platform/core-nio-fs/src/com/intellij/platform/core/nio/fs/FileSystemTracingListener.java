// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.core.nio.fs;

import org.jetbrains.annotations.Nullable;

import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.Set;

@SuppressWarnings("unused")
public interface FileSystemTracingListener<T> {

  // @formatter:off

  default T providerCheckAccessStarted(FileSystemProvider delegate, Path path, AccessMode... modes) { return null; }

  default T providerCopyStarted(FileSystemProvider delegate, Path source, Path target, CopyOption... options) { return null; }

  default T providerCreateDirectoryStarted(FileSystemProvider delegate, Path dir, FileAttribute<?>... attrs) { return null; }

  default T providerCreateLinkStarted(FileSystemProvider delegate, Path link, Path existing) { return null; }

  default T providerCreateSymbolicLinkStarted(FileSystemProvider delegate, Path link, Path target, FileAttribute<?>... attrs) { return null; }

  default T providerDeleteStarted(FileSystemProvider delegate, Path path) { return null; }

  default T providerDeleteIfExistsStarted(FileSystemProvider delegate, Path path) { return null; }

  default T providerGetFileAttributeViewStarted(FileSystemProvider delegate, Path path, Class<? extends FileAttributeView> type, LinkOption... options) { return null; }

  default T providerGetFileStoreStarted(FileSystemProvider delegate, Path path) { return null; }

  default T providerIsHiddenStarted(FileSystemProvider delegate, Path path) { return null; }

  default T providerIsSameFileStarted(FileSystemProvider delegate, Path path, Path path2) { return null; }

  default T providerMoveStarted(FileSystemProvider delegate, Path source, Path target, CopyOption... options) { return null; }

  default T providerNewByteChannelStarted(FileSystemProvider delegate, Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) { return null; }
  default SeekableByteChannel providerNewByteChannelReturn(T token, SeekableByteChannel result) { return result; }

  default T providerNewDirectoryStreamStarted(FileSystemProvider delegate, Path dir, @Nullable DirectoryStream.Filter<? super Path> filter) { return null; }
  default DirectoryStream<Path> providerNewDirectoryStreamReturn(T token, DirectoryStream<Path> result) { return result; }

  default T providerReadAttributesStarted(FileSystemProvider delegate, Path path, Class<? extends BasicFileAttributes> type, LinkOption... options) { return null; }

  default T providerReadAttributesStarted(FileSystemProvider delegate, Path path, String attributes, LinkOption... options) { return null; }

  default T providerReadSymbolicLinkStarted(FileSystemProvider delegate, Path link) { return null; }

  default T providerSetAttributeStarted(FileSystemProvider delegate, Path path, String attribute, Object value, LinkOption... options) { return null; }

  default void providerGenericReturn(T token) {}
  default void providerGenericError(T token, Throwable err) {}

  // @formatter:on

  class NoopFileSystemTracingListener implements FileSystemTracingListener<Void> {}

}