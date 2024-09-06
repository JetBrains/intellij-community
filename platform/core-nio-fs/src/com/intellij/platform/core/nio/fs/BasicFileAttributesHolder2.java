// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.core.nio.fs;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.nio.fs.BasicFileAttributesHolder;

import java.lang.ref.WeakReference;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * This interface allows not adding `--add-exports` to modules where some class implements {@link BasicFileAttributesHolder}.
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
    protected volatile @NotNull WeakReference<@Nullable BasicFileAttributes> myCachedAttributes = new WeakReference<>(null);

    @Override
    public BasicFileAttributes get() {
      return myCachedAttributes.get();
    }

    @Override
    public void invalidate() {
      myCachedAttributes.clear();
    }
  }
}
