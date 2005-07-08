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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.SettingsSavingComponent;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * Manages the relationship between filenames and {@link FileType} instances.
 */

public abstract class FileTypeManager implements SettingsSavingComponent {
  /**
   * Returns the singleton instance of the FileTypeManager component.
   * @return the instace of FileTypeManager
   */
  public static FileTypeManager getInstance() {
    return ApplicationManager.getApplication().getComponent(FileTypeManager.class);
  }

  /**
   * Registers a file type.
   * @param type The file type to register.
   * @param defaultAssociatedExtensions The list of extensions which cause the file to be
   * treated as the specified file type. The extensions should not start with '.'.
   */
  public abstract void registerFileType(@NotNull FileType type, @Nullable String[] defaultAssociatedExtensions);

  /**
   * Returns the file type for the specified file name.
   * @param fileName The file name for which the type is requested.
   * @return The file type instance.
   */
  public abstract @NotNull FileType getFileTypeByFileName(@NotNull String fileName);

  /**
   * Returns the file type for the specified file.
   * @param file The file for which the type is requested.
   * @return The file type instance.
   */
  public abstract @NotNull FileType getFileTypeByFile(@NotNull VirtualFile file);

  /**
   * Returns the file type for the specified extension.
   * @param extension The extension for which the file type is requested, not including the leading '.'.
   * @return The file type instance.
   */
  public abstract @NotNull FileType getFileTypeByExtension(@NotNull String extension);

  /**
   * Returns the list of all registered file types.
   * @return The list of file types.
   */
  public abstract FileType[] getRegisteredFileTypes();

  /**
   * Checks if the specified file is ignored by IDEA. Ignored files are not visible in
   * different project views and cannot be opened in the editor. They will neither be parsed nor compiled.
   * @param name The name of the file to check.
   * @return true if the file is ignored, false otherwise.
   */

  public abstract boolean isFileIgnored(@NotNull String name);

  /**
   * Returns the list of extensions associated with the specified file type.
   * @param type The file type for which the extensions are requested.
   * @return The array of extensions associated with the file type.
   */
  public abstract String[] getAssociatedExtensions(FileType type);

  /**
   * Adds a listener for receiving notifications about changes in the list of
   * registered file types.
   * @param listener The listener instance.
   */

  public abstract void addFileTypeListener(FileTypeListener listener);

  /**
   * Removes a listener for receiving notifications about changes in the list of
   * registered file types.
   * @param listener The listener instance.
   */

  public abstract void removeFileTypeListener(FileTypeListener listener);

  /**
   * If fileName is already associated with any known file type returns it.
   * Otherwise asks user to select file type and associates it with fileName extension if any selected.
   * @return Known file type or null. Never returns {@link com.intellij.openapi.fileTypes.StdFileTypes#UNKNOWN}.
   */
  public abstract @NotNull FileType getKnownFileTypeOrAssociate(VirtualFile file);
}
