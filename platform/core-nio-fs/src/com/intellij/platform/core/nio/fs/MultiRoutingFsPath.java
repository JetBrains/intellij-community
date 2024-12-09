// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.core.nio.fs;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Iterator;
import java.util.Objects;

@SuppressWarnings("UnnecessaryFullyQualifiedName")
@ApiStatus.Internal
public final class MultiRoutingFsPath implements Path, sun.nio.fs.BasicFileAttributesHolder {
  private final Path myDelegate;
  private final MultiRoutingFileSystem myFileSystem;

  MultiRoutingFsPath(MultiRoutingFileSystem fileSystem, Path delegate) {
    myDelegate = delegate;
    myFileSystem = fileSystem;
  }

  public Path getDelegate() {
    return myDelegate;
  }

  @Override
  public FileSystem getFileSystem() {
    return myFileSystem;
  }

  @Override
  public boolean isAbsolute() {
    return myDelegate.isAbsolute();
  }

  @Override
  public MultiRoutingFsPath getRoot() {
    return wrap(myDelegate.getRoot());
  }

  @Override
  public MultiRoutingFsPath getFileName() {
    return wrap(myDelegate.getFileName());
  }

  @Override
  public MultiRoutingFsPath getParent() {
    return wrap(myDelegate.getParent());
  }

  @Override
  public int getNameCount() {
    return myDelegate.getNameCount();
  }

  @Override
  public MultiRoutingFsPath getName(int index) {
    return wrap(myDelegate.getName(index));
  }

  @Override
  public MultiRoutingFsPath subpath(int beginIndex, int endIndex) {
    return wrap(myDelegate.subpath(beginIndex, endIndex));
  }

  @Override
  public boolean startsWith(Path other) {
    if (!(other instanceof MultiRoutingFsPath)) return false;
    return myDelegate.startsWith(toSameTypeAsDelegate(other));
  }

  @Override
  public boolean startsWith(String other) {
    return myDelegate.startsWith(other);
  }

  @Override
  public boolean endsWith(Path other) {
    if (!(other instanceof MultiRoutingFsPath)) return false;
    return myDelegate.endsWith(toSameTypeAsDelegate(other));
  }

  @Override
  public boolean endsWith(String other) {
    return myDelegate.endsWith(other);
  }

  @Override
  public MultiRoutingFsPath normalize() {
    return wrap(myDelegate.normalize());
  }

  @Override
  public MultiRoutingFsPath resolve(Path other) {
    return wrap(myDelegate.resolve(toSameTypeAsDelegate(other)));
  }

  @Override
  public MultiRoutingFsPath resolve(String other) {
    return wrap(myDelegate.resolve(other));
  }

  @Override
  public MultiRoutingFsPath resolveSibling(Path other) {
    return wrap(myDelegate.resolveSibling(toSameTypeAsDelegate(other)));
  }

  @Override
  public MultiRoutingFsPath resolveSibling(String other) {
    return wrap(myDelegate.resolveSibling(other));
  }

  @Override
  public MultiRoutingFsPath relativize(Path other) {
    return wrap(myDelegate.relativize(toSameTypeAsDelegate(other)));
  }

  @Override
  public URI toUri() {
    return myDelegate.toUri();
  }

  @Override
  public MultiRoutingFsPath toAbsolutePath() {
    return wrap(myDelegate.toAbsolutePath());
  }

  @Override
  public MultiRoutingFsPath toRealPath(LinkOption... options) throws IOException {
    return wrap(myDelegate.toRealPath(options));
  }

  @Override
  public File toFile() {
    if (getFileSystem().provider().getScheme().equals("file")) {
      return new File(myDelegate.toString());
    }
    else {
      // It will likely fail because the local file system should be handled with the other branch.
      return myDelegate.toFile();
    }
  }

  @Override
  public WatchKey register(WatchService watcher, WatchEvent.Kind<?>[] events, WatchEvent.Modifier... modifiers) throws IOException {
    if (watcher instanceof MultiRoutingWatchServiceDelegate delegated) {
      return myDelegate.register(delegated.myDelegate, events, modifiers);
    }
    return myDelegate.register(watcher, events, modifiers);
  }

  @Override
  public WatchKey register(WatchService watcher, WatchEvent.Kind<?>... events) throws IOException {
    if (watcher instanceof MultiRoutingWatchServiceDelegate delegated) {
      return myDelegate.register(delegated.myDelegate, events);
    }
    return myDelegate.register(watcher, events);
  }

  @Override
  public Iterator<Path> iterator() {
    Iterator<Path> iterator = myDelegate.iterator();
    return new Iterator<Path>() {
      @Override
      public boolean hasNext() {
        return iterator.hasNext();
      }

      @Override
      public MultiRoutingFsPath next() {
        return wrap(iterator.next());
      }
    };
  }

  @Override
  public int compareTo(Path other) {
    // The documentation of the method declares that:
    // * The returned order is provider-specific.
    // * `compareTo` should never be called for paths with different file systems.
    // However, the meaning of this machinery is a combination of different file systems into a single one.
    // It is assumed that every valid path of every underlying file system is a valid path for the other file systems.
    Path unwrappedOther = toSameTypeAsDelegate(other);
    if (unwrappedOther.getClass().isAssignableFrom(myDelegate.getClass())) {
      return myDelegate.compareTo(unwrappedOther);
    }
    else if (myDelegate.getClass().isAssignableFrom(unwrappedOther.getClass())) {
      return unwrappedOther.compareTo(myDelegate);
    }
    else {
      return myDelegate.compareTo(myDelegate.getFileSystem().getPath(unwrappedOther.toString()));
    }
  }

  @Override
  public String toString() {
    return myDelegate.toString();
  }

  @Contract("null -> null; !null -> !null")
  private @Nullable MultiRoutingFsPath wrap(@Nullable Path path) {
    if (path == null) {
      return null;
    }
    else if (path instanceof MultiRoutingFsPath) {
      return (MultiRoutingFsPath)path;
    }
    else if (path == myDelegate /* intentional reference comparison */) {
      // Some methods like `toAbsolutePath` return the same instance if the path is already absolute.
      // Some other methods like `getFileName` don't declare such an intention in their documentation
      // but deduplicate paths in their default implementations.
      return this;
    }
    else {
      return new MultiRoutingFsPath(myFileSystem, path);
    }
  }

  @Override
  public BasicFileAttributes get() {
    if (myDelegate instanceof sun.nio.fs.BasicFileAttributesHolder) {
      return ((sun.nio.fs.BasicFileAttributesHolder)myDelegate).get();
    }
    return null;
  }

  @Override
  public void invalidate() {
    if (myDelegate instanceof sun.nio.fs.BasicFileAttributesHolder) {
      ((sun.nio.fs.BasicFileAttributesHolder)myDelegate).invalidate();
    }
  }

  @Contract("null -> null; !null -> !null")
  private @Nullable Path toSameTypeAsDelegate(@Nullable Path path) {
    if (path == null) {
      return null;
    }
    if (path instanceof MultiRoutingFsPath mrfsp) {
      path = mrfsp.getDelegate();
    }
    if (myDelegate.getClass().equals(path.getClass())) {
      return path;
    }
    return myDelegate.getFileSystem().getPath(path.toString());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    MultiRoutingFsPath paths = (MultiRoutingFsPath)o;
    return Objects.equals(myDelegate, paths.myDelegate) &&
           Objects.equals(myFileSystem, paths.myFileSystem);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myDelegate, myFileSystem);
  }
}
