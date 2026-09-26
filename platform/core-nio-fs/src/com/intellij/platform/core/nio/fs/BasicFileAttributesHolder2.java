// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.core.nio.fs;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.nio.fs.BasicFileAttributesHolder;

import java.lang.ref.WeakReference;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * This interface allows not adding `--add-exports` to modules where some class implements {@link BasicFileAttributesHolder}.
 *
 * Also, this interface defines some hacks and helper methods related to {@link BasicFileAttributesHolder}.
 */
public interface BasicFileAttributesHolder2 extends BasicFileAttributesHolder {
  /**
   * For being used with interface delegation in Kotlin.
   */
  class Impl implements BasicFileAttributesHolder2 {
    /**
     * <p>
     * Originally, {@link sun.nio.fs.WindowsPath.WindowsPathWithAttributes} has {@link WeakReference} inside,
     * and the whole implementation of the default FS for Windows doesn't hold any strong reference to this path.
     * </p>
     * <p>
     * There has never been any strong reference that could outlive the local variable since the beginning:
     * <ul>
     * <li><a href="https://github.com/JetBrains/JetBrainsRuntime/blob/6d59271ca995653f3dd2166980fc309eb01d99d6/jdk/src/windows/classes/sun/nio/fs/WindowsPath.java#L157">WindowsPath</a></li>
     * <li><a href="https://github.com/JetBrains/JetBrainsRuntime/blob/6d59271ca995653f3dd2166980fc309eb01d99d6/jdk/src/windows/classes/sun/nio/fs/WindowsDirectoryStream.java#L198">WindowsDirectoryStream</a></li>
     * </ul>
     * There was <a href="https://mail.openjdk.org/pipermail/nio-dev/2011-October/001437.html">an attempt to replace the weak reference</a>
     * with a strong reference in the original implementation, but it didn't land onto the upstream branch.
     * </p>
     * <p>
     * It's not clear why it works more or less reliably in the original implementation, but
     * however strange the original works, this implementation should work the same.
     * </p>
     */
    protected final @NotNull WeakReference<@Nullable BasicFileAttributes> myCachedAttributes;

    protected Impl(@Nullable BasicFileAttributes attributes) {
      myCachedAttributes = new WeakReference<>(attributes);
    }

    @Override
    public BasicFileAttributes get() {
      return myCachedAttributes.get();
    }

    @Override
    public void invalidate() {
      myCachedAttributes.clear();
    }
  }

  static @Nullable BasicFileAttributes getAttributesFromHolder(@NotNull Path path) {
    if (path instanceof BasicFileAttributesHolder bafh) {
      return bafh.get();
    }
    return null;
  }

  /**
   * A marker interface for {@link java.nio.file.spi.FileSystemProvider#newDirectoryStream}
   * that advises the file system to fetch file attributes and fill {@link BasicFileAttributesHolder}, if it's supported.
   * <p>
   * Unlike many other methods in {@code FileSystemProvider}, {@code newDirectoryStream} doesn't accept a set of options.
   * The easiest way to provide additional information to the method is through the filter argument.
   * </p>
   * A file system provider should support this feature explicitly.
   * This interface has no effect on default filesystems from JDK:
   * <ul>
   *   <li>In the default file system on Windows, file attributes are always fetched, regardless of the filter argument.</li>
   *   <li>In the default file system on Posix, file attributes are never fetched.</li>
   * </ul>
   */
  @FunctionalInterface
  interface FetchAttributesFilter extends DirectoryStream.Filter<Path> {

    /**
     * In production, we have two coexisting app class loaders: {@link jdk.internal.loader.ClassLoaders.AppClassLoader} and {@link com.intellij.util.lang.PathClassLoader}.
     * The code executing here sometimes runs under {@link com.intellij.util.lang.PathClassLoader}, but the classes of routing FS are loaded with {@link jdk.internal.loader.ClassLoaders.AppClassLoader}.
     * These two class loaders are not related to each other, hence checks for {@code instanceof} between objects loaded with them will fail.
     * Here we forcefully use the classloader that corresponds to a filter with the purpose of having a match between the class and the instance.
     */
    static boolean isFetchAttributesFilter(DirectoryStream.Filter<? super Path> filter) {
      try {
        ClassLoader classLoader = filter.getClass().getClassLoader();
        if (classLoader == null) {
          return filter instanceof BasicFileAttributesHolder2.FetchAttributesFilter;
        }
        else {
          return classLoader.loadClass("com.intellij.platform.core.nio.fs.BasicFileAttributesHolder2$FetchAttributesFilter")
            .isInstance(filter);
        }
      }
      catch (ClassNotFoundException e) {
        return filter instanceof BasicFileAttributesHolder2.FetchAttributesFilter;
      }
    }

    FetchAttributesFilter ACCEPT_ALL = path -> true;
  }
}
