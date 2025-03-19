// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.core.nio.fs;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.FileSystem;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.spi.FileSystemProvider;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 * {@link FileSystemProvider} facade that silently ignores posix file attributes, so that {@link MultiRoutingFileSystem} can advertise
 * blanket posix support, even when the local file system does not offer it and would otherwise throw exceptions.
 *
 * @see sun.nio.fs.WindowsSecurityDescriptor#fromAttribute
 * @see java.nio.file.TempFileHelper#create
 * @see java.nio.file.TempFileHelper#isPosix
 * @see MultiRoutingFileSystem#supportedFileAttributeViews
 */
class CorePosixFilteringFileSystemProvider
  extends DelegatingFileSystemProvider<CorePosixFilteringFileSystemProvider, CorePosixFilteringFileSystem>
  implements RoutingAwareFileSystemProvider {

  private final FileSystemProvider myFileSystemProvider;

  CorePosixFilteringFileSystemProvider(FileSystemProvider provider) { myFileSystemProvider = provider; }

  private static FileAttribute<?>[] filterAttrs(FileAttribute<?>... attrs) {
    return Arrays.stream(attrs).filter(attr -> !attr.name().startsWith("posix:")).toArray(FileAttribute<?>[]::new);
  }

  @Override
  public @NotNull CorePosixFilteringFileSystem wrapDelegateFileSystem(@NotNull FileSystem delegateFs) {
    return new CorePosixFilteringFileSystem(this, delegateFs);
  }

  @Override
  protected @NotNull FileSystemProvider getDelegate(@Nullable Path path1, @Nullable Path path2) {
    return myFileSystemProvider;
  }

  @Override
  @Contract("null -> null; !null -> !null")
  protected @Nullable Path wrapDelegatePath(@Nullable Path delegatePath) {
    return delegatePath;
  }

  @Override
  protected @Nullable Path toDelegatePath(@Nullable Path path) {
    return path;
  }

  @Override
  public FileChannel newFileChannel(Path path,
                                    Set<? extends OpenOption> options,
                                    FileAttribute<?>... attrs) throws IOException {
    return super.newFileChannel(path, options, filterAttrs(attrs));
  }

  @Override
  public AsynchronousFileChannel newAsynchronousFileChannel(Path path,
                                                            Set<? extends OpenOption> options,
                                                            ExecutorService executor,
                                                            FileAttribute<?>... attrs) throws IOException {
    return super.newAsynchronousFileChannel(path, options, executor, filterAttrs(attrs));
  }

  @Override
  public SeekableByteChannel newByteChannel(Path path,
                                            Set<? extends OpenOption> options,
                                            FileAttribute<?>... attrs) throws IOException {
    return super.newByteChannel(path, options, filterAttrs(attrs));
  }

  @Override
  public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
    super.createDirectory(dir, filterAttrs(attrs));
  }

  @Override
  public void createSymbolicLink(Path link, Path target, FileAttribute<?>... attrs) throws IOException {
    super.createSymbolicLink(link, target, filterAttrs(attrs));
  }

  @Override
  public String toString() {
    return String.format("%s(%s)", getClass().getName(), myFileSystemProvider);
  }
}
