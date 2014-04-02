/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.options.Scheme;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @see com.intellij.openapi.fileTypes.StdFileTypes
 * @see com.intellij.openapi.fileTypes.FileTypes
 */
public interface FileType extends Scheme {
  FileType[] EMPTY_ARRAY = new FileType[0];

  /**
   * Returns the name of the file type. The name must be unique among all file types registered in the system.
   * @return The file type name.
   */
  @Override
  @NotNull
  @NonNls
  String getName();

  /**
   * Returns the user-readable description of the file type.
   * @return The file type description.
   */

  @NotNull
  String getDescription();

  /**
   * Returns the default extension for files of the type.
   * @return The extension, not including the leading '.'.
   */

  @NotNull
  @NonNls
  String getDefaultExtension();

  /**
   * Returns the icon used for showing files of the type.
   * @return The icon instance, or null if no icon should be shown.
   */

  @Nullable
  Icon getIcon();

  /**
   * Returns true if files of the specified type contain binary data. Used for source control, to-do items scanning and other purposes.
   * @return true if the file is binary, false if the file is plain text.
   */
  boolean isBinary();

  /**
   * Returns true if the specified file type is read-only. Read-only file types are not shown in the "File Types" settings dialog,
   * and users cannot change the extensions associated with the file type.
   * @return true if the file type is read-only, false otherwise.
   */

  boolean isReadOnly();

  /**
   * Returns the character set for the specified file.
   * @param file The file for which the character set is requested.
   * @return The character set name, in the format supported by {@link java.nio.charset.Charset} class.
   */

  @Nullable
  @NonNls
  String getCharset(@NotNull VirtualFile file, @NotNull byte[] content);

}
