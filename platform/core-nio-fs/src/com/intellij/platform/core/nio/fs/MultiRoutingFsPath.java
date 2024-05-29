// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.core.nio.fs;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.util.Iterator;
import java.util.Objects;


public class MultiRoutingFsPath implements Path {
  private final Path myDelegate;
  private final MultiRoutingFileSystem myFileSystem;

  public MultiRoutingFsPath(MultiRoutingFileSystem fileSystem, Path delegate) {
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
  public Path getRoot() {
    return wrap(myDelegate.getRoot());
  }

  @Override
  public Path getFileName() {
    return wrap(myDelegate.getFileName());
  }

  @Override
  public Path getParent() {
    return wrap(myDelegate.getParent());
  }

  @Override
  public int getNameCount() {
    return myDelegate.getNameCount();
  }

  @Override
  public Path getName(int index) {
    return wrap(myDelegate.getName(index));
  }

  @Override
  public Path subpath(int beginIndex, int endIndex) {
    return wrap(myDelegate.subpath(beginIndex, endIndex));
  }

  @Override
  public boolean startsWith(Path other) {
    if (!(other instanceof MultiRoutingFsPath)) return false;
    return myDelegate.startsWith(unwrap(other));
  }

  @Override
  public boolean startsWith(String other) {
    return myDelegate.startsWith(other);
  }

  @Override
  public boolean endsWith(Path other) {
    if (!(other instanceof MultiRoutingFsPath)) return false;
    return myDelegate.endsWith(unwrap(other));
  }

  @Override
  public boolean endsWith(String other) {
    return myDelegate.endsWith(other);
  }

  @Override
  public Path normalize() {
    return wrap(myDelegate.normalize());
  }

  @Override
  public Path resolve(Path other) {
    return wrap(myDelegate.resolve(unwrap(other)));
  }

  @Override
  public Path resolve(String other) {
    return wrap(myDelegate.resolve(other));
  }

  @Override
  public Path resolveSibling(Path other) {
    return wrap(myDelegate.resolveSibling(unwrap(other)));
  }

  @Override
  public Path resolveSibling(String other) {
    return wrap(myDelegate.resolveSibling(other));
  }

  @Override
  public Path relativize(Path other) {
    return wrap(myDelegate.relativize(unwrap(other)));
  }

  @Override
  public URI toUri() {
    return myDelegate.toUri();
  }

  @Override
  public Path toAbsolutePath() {
    return wrap(myDelegate.toAbsolutePath());
  }

  @Override
  public Path toRealPath(LinkOption... options) throws IOException {
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
    return myDelegate.register(watcher, events, modifiers);
  }

  @Override
  public WatchKey register(WatchService watcher, WatchEvent.Kind<?>... events) throws IOException {
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
      public Path next() {
        return wrap(iterator.next());
      }
    };
  }

  @Override
  public int compareTo(Path other) {
    return myDelegate.compareTo(unwrap(other));
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
    else {
      return new MultiRoutingFsPath(myFileSystem, path);
    }
  }

  @Contract("null -> null; !null -> !null")
  private static @Nullable Path unwrap(@Nullable Path path) {
    return path == null ? null : ((MultiRoutingFsPath)path).getDelegate();
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
