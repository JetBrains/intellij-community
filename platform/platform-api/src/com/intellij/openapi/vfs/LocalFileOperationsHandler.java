/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vfs;

import com.intellij.util.ThrowableConsumer;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

/**
 * Allows a version control plugin to intercept file operations performed in the local file system
 * and to perform them through the corresponding VCS.
 *
 * @author max
 * @see com.intellij.openapi.vfs.LocalFileSystem#registerAuxiliaryFileOperationsHandler(LocalFileOperationsHandler)
 */
public interface LocalFileOperationsHandler {
  /**
   * Intercepts the deletion of a file.
   * @param file the file being deleted.
   * @return true if the handler has performed the deletion, false if the deletion needs to be performed through
   * standard core logic.
   */
  boolean delete(VirtualFile file) throws IOException;

  /**
   * Intercepts the movement of a file.
   * @param file  the file being moved.
   * @param toDir the destination directory.
   * @return true if the handler has performed the move, false if the move needs to be performed through
   * standard core logic.
   */
  boolean move(VirtualFile file, VirtualFile toDir) throws IOException;

  /**
   * Intercepts the copying of a file.
   * @param file  the file being copied.
   * @param toDir the destination directory.
   * @param copyName the name for the copy
   * @return the copy result if the handler has performed the copy, null if the copy needs to be performed through
   * standard core logic.
   */
  @Nullable
  File copy(VirtualFile file, VirtualFile toDir, final String copyName) throws IOException;

  /**
   * Intercepts the renaming of a file.
   * @param file  the file being renamed.
   * @param newName the new name.
   * @return true if the handler has performed the rename, false if the rename needs to be performed through
   * standard core logic.
   */
  boolean rename(VirtualFile file, String newName) throws IOException;

  /**
   * Intercepts the creation of a file.
   * @param dir  the directory in which the file is being created.
   * @param name the name of the new file.
   * @return true if the handler has performed the file creation, false if the creation needs to be performed through
   * standard core logic.
   */
  boolean createFile(VirtualFile dir, String name) throws IOException;

  /**
   * Intercepts the creation of a directory.
   * @param dir  the directory in which the directory is being created.
   * @param name the name of the new directory.
   * @return true if the handler has performed the directory creation, false if the creation needs to be performed through
   * standard core logic.
   */
  boolean createDirectory(VirtualFile dir, String name) throws IOException;

  void afterDone(final ThrowableConsumer<LocalFileOperationsHandler, IOException> invoker);
}
