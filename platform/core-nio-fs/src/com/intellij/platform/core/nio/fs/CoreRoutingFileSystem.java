// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.core.nio.fs;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class CoreRoutingFileSystem extends FileSystem {
  private final CoreRoutingFileSystemProvider myProvider;
  private final FileSystem                    myLocalFS;

  private volatile FileSystem myMountedFS;
  private volatile CoreRoutingFileSystemDelegate myDelegate;
  private static volatile String ourMountedFSPrefix;

  public CoreRoutingFileSystem(CoreRoutingFileSystemProvider provider, FileSystem localFS) {
    myProvider = provider;
    myLocalFS = localFS;
  }

  public void initialize(@NotNull String filesystemClassName, @Nullable Class<? extends CoreRoutingFileSystemDelegate> routingFilesystemDelegateClass) {
    myMountedFS = myProvider.createInstance(
      filesystemClassName,
      new Class[]{FileSystemProvider.class},
      myProvider);
    if (routingFilesystemDelegateClass != null) {
      try {
        myDelegate = routingFilesystemDelegateClass.getConstructor().newInstance();
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  public static void setMountedFSPrefix(@NotNull String mountedFSPrefix) {
    ourMountedFSPrefix = mountedFSPrefix;
  }

  public boolean isInitialized() {
    return myMountedFS != null;
  }

  @Override
  public FileSystemProvider provider() {
    return myProvider;
  }

  @Override
  public void close() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isOpen() {
    return myLocalFS.isOpen() || myMountedFS != null && myMountedFS.isOpen();
  }

  @Override
  public boolean isReadOnly() {
    return myLocalFS.isReadOnly() && (myMountedFS == null || myMountedFS.isReadOnly());
  }

  @Override
  public String getSeparator() {
    return myLocalFS.getSeparator();
  }

  @Override
  public Iterable<Path> getRootDirectories() {
    Iterable<Path> roots = concat(myLocalFS.getRootDirectories(), myMountedFS == null ? Collections.emptyList() : myMountedFS.getRootDirectories());

    return new Iterable<Path>() {
      @Override
      public Iterator<Path> iterator() {
        Iterator<Path> iterator = roots.iterator();
        return new Iterator<Path>() {
          @Override
          public boolean hasNext() {
            return iterator.hasNext();
          }

          @Override
          public Path next() {
            return CoreRoutingFileSystemProvider.path(CoreRoutingFileSystem.this, iterator.next());
          }
        };
      }
    };
  }

  @Override
  public Iterable<FileStore> getFileStores() {
    return concat(myLocalFS.getFileStores(), myMountedFS == null ? Collections.emptyList() : myMountedFS.getFileStores());
  }

  @Override
  public Set<String> supportedFileAttributeViews() {
    Set<String> result = new HashSet<>(myLocalFS.supportedFileAttributeViews());
    if (myMountedFS != null) result.addAll(myMountedFS.supportedFileAttributeViews());
    return result;
  }

  @Override
  public Path getPath(String first, String... more) {
    FileSystem fs = myMountedFS != null && isMountedFSFile(first) ? myMountedFS : myLocalFS;
    Path result = CoreRoutingFileSystemProvider.path(this, fs.getPath(first, more));

    CoreRoutingFileSystemDelegate delegate = myDelegate;
    return delegate == null ? result : delegate.wrap(result);
  }

  @Override
  public PathMatcher getPathMatcher(String syntaxAndPattern) {
    return myLocalFS.getPathMatcher(syntaxAndPattern);
  }

  @Override
  public UserPrincipalLookupService getUserPrincipalLookupService() {
    return myLocalFS.getUserPrincipalLookupService();
  }

  @Override
  public WatchService newWatchService() throws IOException {
    return myLocalFS.newWatchService();
  }

  public boolean isMountedFSPath(CorePath path) {
    if (path.isMountedFS()) return true;
    CoreRoutingFileSystemDelegate delegate = myDelegate;
    return delegate != null && delegate.isMountedFSPath(path);
  }

  public static boolean isMountedFSFile(String virtualFilePath) {
    return CoreRoutingFileSystemProvider.normalizePath(virtualFilePath).startsWith(ourMountedFSPrefix);
  }

  private static <T> Iterable<T> concat(Iterable<T> first, Iterable<T> second) {
    Stream<T> firstStream = StreamSupport.stream(first.spliterator(), false);
    Stream<T> secondStream = StreamSupport.stream(second.spliterator(), false);
    Stream<T> concat = Stream.concat(firstStream, secondStream);
    return new Iterable<T>() {
      @Override
      public Iterator<T> iterator() {
        return concat.iterator();
      }
    };
  }
}
