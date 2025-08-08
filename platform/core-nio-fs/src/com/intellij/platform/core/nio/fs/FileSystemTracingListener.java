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

@SuppressWarnings("unused")
public interface FileSystemTracingListener {

  // @formatter:off

  default void providerCheckAccessStarted(FileSystemProvider delegate, Path path, AccessMode... modes) {}
  default void providerCheckAccessReturn(FileSystemProvider delegate, Path path, AccessMode... modes) {}
  default void providerCheckAccessError(FileSystemProvider delegate, IOException err, Path path, AccessMode... modes) {}

  default void providerCopyStarted(FileSystemProvider delegate, Path source, Path target, CopyOption... options) {}
  default void providerCopyReturn(FileSystemProvider delegate, Path source, Path target, CopyOption... options) {}
  default void providerCopyError(FileSystemProvider delegate, IOException err, Path source, Path target, CopyOption... options) {}

  default void providerCreateDirectoryStarted(FileSystemProvider delegate, Path dir, FileAttribute<?>... attrs) {}
  default void providerCreateDirectoryReturn(FileSystemProvider delegate, Path dir, FileAttribute<?>... attrs) {}
  default void providerCreateDirectoryError(FileSystemProvider delegate, IOException err, Path dir, FileAttribute<?>... attrs) {}

  default void providerCreateLinkStarted(FileSystemProvider delegate, Path link, Path existing) {}
  default void providerCreateLinkReturn(FileSystemProvider delegate, Path link, Path existing) {}
  default void providerCreateLinkError(FileSystemProvider delegate, IOException err, Path link, Path existing) {}

  default void providerCreateSymbolicLinkStarted(FileSystemProvider delegate, Path link, Path target, FileAttribute<?>... attrs) {}
  default void providerCreateSymbolicLinkReturn(FileSystemProvider delegate, Path link, Path target, FileAttribute<?>... attrs) {}
  default void providerCreateSymbolicLinkError(FileSystemProvider delegate, IOException err, Path link, Path target, FileAttribute<?>... attrs) {}

  default void providerDeleteStarted(FileSystemProvider delegate, Path path) {}
  default void providerDeleteReturn(FileSystemProvider delegate, Path path) {}
  default void providerDeleteError(FileSystemProvider delegate, IOException err, Path path) {}

  default void providerDeleteIfExistsStarted(FileSystemProvider delegate, Path path) {}
  default void providerDeleteIfExistsReturn(FileSystemProvider delegate, boolean result, Path path) {}
  default void providerDeleteIfExistsError(FileSystemProvider delegate, IOException err, Path path) {}

  default void providerGetFileAttributeViewStarted(FileSystemProvider delegate, Path path, Class<? extends FileAttributeView> type, LinkOption... options) {}
  default void providerGetFileAttributeViewReturn(FileSystemProvider delegate, FileAttributeView result, Path path, Class<? extends FileAttributeView> type, LinkOption... options) {}

  default void providerGetFileStoreStarted(FileSystemProvider delegate, Path path) {}
  default void providerGetFileStoreReturn(FileSystemProvider delegate, FileStore result, Path path) {}
  default void providerGetFileStoreError(FileSystemProvider delegate, IOException err, Path path) {}

  default void providerIsHiddenStarted(FileSystemProvider delegate, Path path) {}
  default void providerIsHiddenReturn(FileSystemProvider delegate, boolean result, Path path) {}
  default void providerIsHiddenError(FileSystemProvider delegate, IOException err, Path path) {}

  default void providerIsSameFileStarted(FileSystemProvider delegate, Path path, Path path2) {}
  default void providerIsSameFileReturn(FileSystemProvider delegate, boolean result, Path path, Path path2) {}
  default void providerIsSameFileError(FileSystemProvider delegate, IOException err, Path path, Path path2) {}

  default void providerMoveStarted(FileSystemProvider delegate, Path source, Path target, CopyOption... options) {}
  default void providerMoveReturn(FileSystemProvider delegate, Path source, Path target, CopyOption... options) {}
  default void providerMoveError(FileSystemProvider delegate, IOException err, Path source, Path target, CopyOption... options) {}

  default void providerNewByteChannelStarted(FileSystemProvider delegate, Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) {}
  default SeekableByteChannel providerNewByteChannelReturn(FileSystemProvider delegate, SeekableByteChannel result, Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) { return result; }
  default void providerNewByteChannelError(FileSystemProvider delegate, IOException err, Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) {}

  default void providerNewDirectoryStreamStarted(FileSystemProvider delegate, Path dir, @Nullable DirectoryStream.Filter<? super Path> filter) {}
  default DirectoryStream<Path> providerNewDirectoryStreamReturn(FileSystemProvider delegate, DirectoryStream<Path> result, Path dir, @Nullable DirectoryStream.Filter<? super Path> filter) { return result; }
  default void providerNewDirectoryStreamError(FileSystemProvider delegate, IOException err, Path dir, @Nullable DirectoryStream.Filter<? super Path> filter) {}

  default void providerReadAttributesStarted(FileSystemProvider delegate, Path path, Class<? extends BasicFileAttributes> type, LinkOption... options) {}
  default void providerReadAttributesReturn(FileSystemProvider delegate, BasicFileAttributes result, Path path, Class<? extends BasicFileAttributes> type, LinkOption... options) {}
  default void providerReadAttributesError(FileSystemProvider delegate, IOException err, Path path, Class<? extends BasicFileAttributes> type, LinkOption... options) {}

  default void providerReadAttributesStarted(FileSystemProvider delegate, Path path, String attributes, LinkOption... options) {}
  default void providerReadAttributesReturn(FileSystemProvider delegate, Map<String, Object> result, Path path, String attributes, LinkOption... options) {}
  default void providerReadAttributesError(FileSystemProvider delegate, IOException err, Path path, String attributes, LinkOption... options) {}

  default void providerReadSymbolicLinkStarted(FileSystemProvider delegate, Path link) {}
  default void providerReadSymbolicLinkReturn(FileSystemProvider delegate, Path result, Path link) {}
  default void providerReadSymbolicLinkError(FileSystemProvider delegate, IOException err, Path link) {}

  default void providerSetAttributeStarted(FileSystemProvider delegate, Path path, String attribute, Object value, LinkOption... options) {}
  default void providerSetAttributeReturn(FileSystemProvider delegate, Path path, String attribute, Object value, LinkOption... options) {}
  default void providerSetAttributeError(FileSystemProvider delegate, IOException err, Path path, String attribute, Object value, LinkOption... options) {}

  // @formatter:on

  class NoopFileSystemTracingListener implements FileSystemTracingListener {}

}