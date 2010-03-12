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
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages the relationship between filenames and {@link FileType} instances.
 */

public abstract class FileTypeManager{
  private static class FileTypeManagerHolder {
    private static final FileTypeManager ourInstance = createFileTypeManager();
  }

  /**
   * Returns the singleton instance of the FileTypeManager component.
   *
   * @return the instace of FileTypeManager
   */
  public static FileTypeManager getInstance() {
    return FileTypeManagerHolder.ourInstance;
  }

  private static FileTypeManager createFileTypeManager() {
    Application app = ApplicationManager.getApplication();
    return app == null ? new MockFileTypeManager() : app.getComponent(FileTypeManager.class);
  }

  public abstract void registerFileType(@NotNull FileType type, @NotNull List<FileNameMatcher> defaultAssociations);

  /**
   * Registers a file type.
   *
   * @param type                        The file type to register.
   * @param defaultAssociatedExtensions The list of extensions which cause the file to be
   *                                    treated as the specified file type. The extensions should not start with '.'.
   */
  public final void registerFileType(@NotNull FileType type, @NonNls @Nullable String... defaultAssociatedExtensions) {
    List<FileNameMatcher> matchers = new ArrayList<FileNameMatcher>();
    if (defaultAssociatedExtensions != null) {
      for (String extension : defaultAssociatedExtensions) {
        matchers.add(new ExtensionFileNameMatcher(extension));
      }
    }
    registerFileType(type, matchers);
  }

  /**
   * Returns the file type for the specified file name.
   *
   * @param fileName The file name for which the type is requested.
   * @return The file type instance, or {@link FileTypes#UNKNOWN} if not found.
   */
  @NotNull
  public abstract
  FileType getFileTypeByFileName(@NotNull @NonNls String fileName);

  /**
   * Returns the file type for the specified file.
   *
   * @param file The file for which the type is requested.
   * @return The file type instance.
   */
  @NotNull
  public abstract
  FileType getFileTypeByFile(@NotNull VirtualFile file);

  /**
   * Returns the file type for the specified extension.
   * Note that a more general way of obtaining file type is with {@link #getFileTypeByFile(VirtualFile)}
   * @param extension The extension for which the file type is requested, not including the leading '.'.
   * @return The file type instance.
   */
  @NotNull
  public abstract
  FileType getFileTypeByExtension(@NonNls @NotNull String extension);

  /**
   * Returns the list of all registered file types.
   *
   * @return The list of file types.
   */
  @NotNull
  public abstract FileType[] getRegisteredFileTypes();

  /**
   * Checks if the specified file is ignored by IDEA. Ignored files are not visible in
   * different project views and cannot be opened in the editor. They will neither be parsed nor compiled.
   *
   * @param name The name of the file to check.
   * @return true if the file is ignored, false otherwise.
   */

  public abstract boolean isFileIgnored(@NonNls @NotNull String name);

  /**
   * Returns the list of extensions associated with the specified file type.
   *
   * @param type The file type for which the extensions are requested.
   * @return The array of extensions associated with the file type.
   * @deprecated since more generic way of associations by means of whildcards exist not every associations matches extension paradigm
   */
  @NotNull
  public abstract String[] getAssociatedExtensions(@NotNull FileType type);


  @NotNull
  public abstract List<FileNameMatcher> getAssociations(@NotNull FileType type);

  public abstract boolean isFileOfType(VirtualFile file, FileType type);

  /**
   * Adds a listener for receiving notifications about changes in the list of
   * registered file types.
   *
   * @deprecated Subscribe to #FILE_TYPES on any message bus level.
   *
   * @param listener The listener instance.
   */

  public abstract void addFileTypeListener(@NotNull FileTypeListener listener);

  /**
   * Removes a listener for receiving notifications about changes in the list of
   * registered file types.

   * @deprecated Subscribe to #FILE_TYPES on any message bus level.
   *
   * @param listener The listener instance.
   */

  public abstract void removeFileTypeListener(@NotNull FileTypeListener listener);

  /**
   * If fileName is already associated with any known file type returns it.
   * Otherwise asks user to select file type and associates it with fileName extension if any selected.
   *
   * @param file - a file to ask for file type association
   * @return Known file type or null. Never returns {@link FileTypes#UNKNOWN}.
   */
  @Nullable
  public abstract FileType getKnownFileTypeOrAssociate(@NotNull VirtualFile file);

  /**
   * Returns the semicolon-delimited list of patterns for files and folders
   * which are excluded from the project structure though they may be present
   * physically on the HD.
   *
   * @return Semicolon-delimited list of patterns.
   */
  @NotNull
  public abstract String getIgnoredFilesList();

  /**
   * Sets new list of semicolon-delimited patterns for files and folders which
   * are excluded from the project structure.
   *
   * @param list List of semicolon-delimited patterns.
   */
  public abstract void setIgnoredFilesList(@NotNull String list);

  /**
   * Adds an extension to the list of extensions associated with a file type.
   *
   * @param type      the file type to associate the extension with.
   * @param extension the extension to associate.
   * @since 5.0.2
   */
  public final void associateExtension(@NotNull FileType type, @NotNull @NonNls String extension) {
    associate(type, new ExtensionFileNameMatcher(extension));
  }

  public final void associatePattern(@NotNull FileType type, @NotNull @NonNls String pattern) {
    associate(type, parseFromString(pattern));
  }

  public abstract void associate(@NotNull FileType type, @NotNull FileNameMatcher matcher);

  /**
   * Removes an extension from the list of extensions associated with a file type.
   *
   * @param type      the file type to remove the extension from.
   * @param extension the extension to remove.
   * @since 5.0.2
   */
  public final void removeAssociatedExtension(@NotNull FileType type, @NotNull @NonNls String extension) {
    removeAssociation(type, new ExtensionFileNameMatcher(extension));
  }

  public abstract void removeAssociation(@NotNull FileType type, @NotNull FileNameMatcher matcher);

  public static FileNameMatcher parseFromString(String pattern) {
    if (pattern.startsWith("*.") &&
        pattern.indexOf('*', 2) < 0 &&
        pattern.indexOf('.', 2) < 0 &&
        pattern.indexOf('?', 2) < 0) {
      return new ExtensionFileNameMatcher(pattern.substring(2).toLowerCase());
    }

    if (pattern.contains("*") ||  pattern.contains("?")) {
      return new WildcardFileNameMatcher(pattern);
    }

    return new ExactFileNameMatcher(pattern);
  }

  @NotNull
  public abstract FileType getStdFileType(@NotNull @NonNls String fileTypeName);
}
