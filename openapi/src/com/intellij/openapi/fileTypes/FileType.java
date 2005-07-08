/*
 * Copyright (c) 2000-05 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.openapi.fileTypes;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public interface FileType {
  FileType[] EMPTY_ARRAY = new FileType[0];

  /**
   * Returns the name of the file type. The name must be unique among all file types registered in the system.
   * @return The file type name.
   */

  @NotNull
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
  String getCharset(@NotNull VirtualFile file);

  /**
   * Returns the syntax highlighter for the files of the type.
   * @param project The project in which the highligher will work, or null if the highlighter is not tied to any project.
   * @return The highlighter implementation.
   */

  @NotNull
  SyntaxHighlighter getHighlighter(Project project);

  /**
   * Returns the structure view builder for the specified file.
   * @param file The file for which the structure view builder is requested.
   * @param project The project to which the file belongs.
   * @return The structure view builder, or null if no structure view is available for the file.
   */

  @Nullable
  StructureViewBuilder getStructureViewBuilder(VirtualFile file, Project project);
}
