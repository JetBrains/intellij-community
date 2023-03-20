// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.options.Scheme;
import com.intellij.openapi.util.NlsContexts.Label;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * <p>Describes a filetype.</p>
 *
 * <p>Must be registered via {@code com.intellij.fileType} extension point.
 * If file type depends on given file, {@link com.intellij.openapi.fileTypes.ex.FileTypeIdentifiableByVirtualFile}
 * can be used for non-static mapping.</p>
 *
 * <p>Use {@link LanguageFileType} for files having {@link com.intellij.lang.Language} support.</p>
 *
 * @see com.intellij.openapi.fileTypes.FileTypes
 * @see INativeFileType
 */
public interface FileType extends Scheme {
  FileType[] EMPTY_ARRAY = new FileType[0];

  /**
   * Returns the name of the file type. The name must be unique among all file types registered in the system.
   */
  @Override
  @NonNls @NotNull String getName();

  /**
   * Returns the user-readable description of the file type.
   */
  @Label @NotNull String getDescription();

  /**
   * Returns the default extension for files of the type, <em>not</em> including the leading '.'.
   */
  @NlsSafe @NotNull String getDefaultExtension();

  /**
   * Returns the icon used for showing files of the type, or {@code null} if no icon should be shown.
   */
  Icon getIcon();

  /**
   * Returns {@code true} if files of the specified type contain binary data, {@code false} if the file is plain text.
   * Used for source control, to-do items scanning and other purposes.
   */
  boolean isBinary();

  /**
   * Returns {@code true} if the specified file type is read-only. Read-only file types are not shown in the "File Types" settings dialog,
   * and users cannot change the extensions associated with the file type.
   */
  default boolean isReadOnly() {
    return false;
  }

  /**
   * Returns the character set for the specified file.
   *
   * @param file    The file for which the character set is requested.
   * @param content File content.
   * @return The character set name, in the format supported by {@link java.nio.charset.Charset} class.
   */
  default @NonNls @Nullable String getCharset(@NotNull VirtualFile file, byte @NotNull [] content) {
    // TODO see MetadataJsonFileType (it's actually text but tries indexing itself as binary)
    // if (isBinary()) {
    //   throw new UnsupportedOperationException();
    // }
    return null;
  }
}
