// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.options.Scheme;
import com.intellij.openapi.util.NlsContexts.Label;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Describes a filetype.
 * <p/>
 * Must be registered via {@code com.intellij.fileType} extension point.
 * If file type depends on given file, {@link com.intellij.openapi.fileTypes.ex.FileTypeIdentifiableByVirtualFile}
 * can be used for non-static mapping.
 * <p/>
 * Use {@link LanguageFileType} for files having {@link com.intellij.lang.Language} support.
 *
 * @see com.intellij.openapi.fileTypes.FileTypes
 * @see INativeFileType
 */
public interface FileType extends Scheme {
  FileType[] EMPTY_ARRAY = new FileType[0];

  /**
   * Returns the name of the file type. The name must be unique among all file types registered in the system.
   *
   * @return The file type name.
   */
  @Override
  @NotNull
  @NonNls
  String getName();

  /**
   * Returns the user-readable description of the file type.
   *
   * @return The file type description.
   */

  @NotNull
  @Label
  String getDescription();

  /**
   * Returns the default extension for files of the type.
   *
   * @return The extension, <em>not</em> including the leading '.'.
   */

  @NotNull
  @NonNls
  String getDefaultExtension();

  /**
   * Returns the icon used for showing files of the type.
   *
   * @return The icon instance, or {@code null} if no icon should be shown.
   */

  @Nullable
  Icon getIcon();

  /**
   * Returns {@code true} if files of the specified type contain binary data. Used for source control, to-do items scanning and other purposes.
   *
   * @return {@code true} if the file is binary, {@code false} if the file is plain text.
   */
  boolean isBinary();

  /**
   * Returns {@code true} if the specified file type is read-only. Read-only file types are not shown in the "File Types" settings dialog,
   * and users cannot change the extensions associated with the file type.
   *
   * @return {@code true} if the file type is read-only, {@code false} otherwise.
   */

  boolean isReadOnly();

  /**
   * Returns the character set for the specified file.
   *
   * @param file    The file for which the character set is requested.
   * @param content File content.
   * @return The character set name, in the format supported by {@link java.nio.charset.Charset} class.
   */
  @Nullable
  @NonNls
  String getCharset(@NotNull VirtualFile file, byte @NotNull [] content);
}
