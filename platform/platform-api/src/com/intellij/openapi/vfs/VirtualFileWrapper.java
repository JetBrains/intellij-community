// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vfs;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

/**
 * Utility class for work with both java.io.File and VirtualFile. It helps
 * to create new VirtualFiles by java.io.Files. It is helpful for storing
 * information about non-existing files with possibility to create VirtualFile
 * on demand.
 *
 * @author Konstantin Bulenkov
 * @see VirtualFile
 */
public final class VirtualFileWrapper {
  private final File myFile;

  /**
   * Constructs wrapper based on java.io.File
   *
   * @param file java.io.File
   */
  public VirtualFileWrapper(@NotNull File file) {
    myFile = file;
  }

  /**
   * Tests whether the file passed to constructor exists.
   *
   * @return  {@code true} if and only if the file passed to constructor
   * exists; {@code false} otherwise
   *
   * @throws SecurityException
   *          If a security manager exists and its <code>{@link
   *          SecurityManager#checkRead(String)}</code>
   *          method denies read access to the file or directory
   */
  public boolean exists() {
    return myFile.exists();
  }

  /**
   * Refreshes LocalFileSystem and looks for VirtualFile
   *
   * @return VirtualFile or null if file is not exist
   */
  @Nullable
  public VirtualFile getVirtualFile() {
    return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(myFile);
  }

  /**
   * Looks for VirtualFile and tries to create new VirtualFile if
   * <tt>createIfNotExist</tt> is <tt>true</tt> and file is not exist.
   *
   * @param createIfNotExist if <tt>true</tt> and java.io.File is not exist,
   *      VirtualFileWrapper will try to create new file
   *
   * @return virtual file
   */
  @Nullable
  public VirtualFile getVirtualFile(boolean createIfNotExist) {
    if (createIfNotExist && !myFile.exists()) {
      try {
        if (!myFile.createNewFile()) {
          return null;
        }
      } catch (IOException e) {//
      }
    }
    return getVirtualFile();
  }

  /**
   * Returns original java.io.File
   *
   * @return original java.io.File
   */
  @NotNull
  public File getFile() {
    return myFile;
  }
}
