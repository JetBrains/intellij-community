// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.core.nio.fs;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.util.Iterator;
import java.util.Set;

/**
 * Allows extending some {@link FileSystem} via composition instead of inheritance.
 * The inheritor of the class defines {@link #getDelegate()}.
 * That delegate is used for all file system operations.
 */
public abstract class DelegatingFileSystem<P extends DelegatingFileSystemProvider<P, ?>> extends FileSystem {
  protected abstract @NotNull FileSystem getDelegate();

  /**
   * This method can choose a backend file system if the inheritor is able to delegate calls to different file systems.
   *
   * @param root The first directory of an absolute path. On Windows, it can be {@code C:}, {@code \\wsl.localhost\Ubuntu-22.04}, etc.
   * @return A specialized filesystem for the specific root, or a fallback filesystem like returned by {@link #getDelegate()}.
   */
  protected @NotNull FileSystem getDelegate(@NotNull String root) {
    return getDelegate();
  }

  @Override
  public String toString() {
    return String.format("%s(delegate=%s)", getClass().getName(), getDelegate());
  }

  @Override
  public abstract P provider();

  @Override
  public void close() throws IOException {
    getDelegate().close();
  }

  @Override
  public boolean isOpen() {
    return getDelegate().isOpen();
  }

  @Override
  public boolean isReadOnly() {
    return getDelegate().isReadOnly();
  }

  @Override
  public String getSeparator() {
    return getDelegate().getSeparator();
  }

  @Override
  public Iterable<Path> getRootDirectories() {
    return new Iterable<Path>() {
      final Iterable<Path> myIterable = getDelegate().getRootDirectories();

      @Override
      public @NotNull Iterator<Path> iterator() {
        return new Iterator<Path>() {
          final Iterator<Path> myIterator = myIterable.iterator();

          @Override
          public boolean hasNext() {
            return myIterator.hasNext();
          }

          @Override
          public Path next() {
            return provider().toDelegatePath(myIterator.next());
          }
        };
      }
    };
  }

  @Override
  public Iterable<FileStore> getFileStores() {
    return getDelegate().getFileStores();
  }

  @Override
  public Set<String> supportedFileAttributeViews() {
    return getDelegate().supportedFileAttributeViews();
  }

  @Override
  public @NotNull Path getPath(@NotNull String first, @NotNull String @NotNull ... more) {
    return provider().toDelegatePath(getDelegate(first).getPath(first, more));
  }

  @Override
  public PathMatcher getPathMatcher(String syntaxAndPattern) {
    return getDelegate().getPathMatcher(syntaxAndPattern);
  }

  @Override
  public UserPrincipalLookupService getUserPrincipalLookupService() {
    return getDelegate().getUserPrincipalLookupService();
  }

  @Override
  public WatchService newWatchService() throws IOException {
    return getDelegate().newWatchService();
  }
}
