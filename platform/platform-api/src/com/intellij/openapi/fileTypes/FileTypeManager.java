// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.CachedSingletonsRegistry;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.fileTypes.FileNameMatcherFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages the relationship between filenames and {@link FileType} instances.
 */
public abstract class FileTypeManager extends FileTypeRegistry {
  static {
    FileTypeRegistry.ourInstanceGetter = FileTypeManager::getInstance;
  }

  private static FileTypeManager ourInstance = CachedSingletonsRegistry.markCachedField(FileTypeManager.class);

  public static final @NotNull Topic<FileTypeListener> TOPIC = new Topic<>(FileTypeListener.class, Topic.BroadcastDirection.TO_DIRECT_CHILDREN);

  /**
   * Returns the singleton instance of the FileTypeManager component.
   *
   * @return the instance of FileTypeManager
   */
  public static FileTypeManager getInstance() {
    FileTypeManager instance = ourInstance;
    if (instance == null) {
      Application app = ApplicationManager.getApplication();
      ourInstance = instance = app != null ? app.getService(FileTypeManager.class) : new MockFileTypeManager();
    }
    return instance;
  }

  /**
   * @deprecated use {@code com.intellij.fileType} extension point or {@link FileTypeFactory} instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public abstract void registerFileType(@NotNull FileType type, @NotNull List<? extends FileNameMatcher> defaultAssociations);

  /**
   * Registers a file type.
   *
   * @param type                        The file type to register.
   * @param defaultAssociatedExtensions The list of extensions which cause the file to be
   *                                    treated as the specified file type. The extensions should not start with '.'.
   * @deprecated use {@code com.intellij.fileType} extension point or {@link FileTypeFactory} instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public final void registerFileType(@NotNull FileType type, @NonNls String @Nullable ... defaultAssociatedExtensions) {
    List<FileNameMatcher> matchers = new ArrayList<>();
    if (defaultAssociatedExtensions != null) {
      for (String extension : defaultAssociatedExtensions) {
        matchers.add(new ExtensionFileNameMatcher(extension));
      }
    }
    registerFileType(type, matchers);
  }

  /**
   * Checks if the specified file is ignored by the IDE. Ignored files are not visible in
   * different project views and cannot be opened in the editor. They will neither be parsed nor compiled.
   *
   * @param name The name of the file to check.
   * @return {@code true} if the file is ignored, {@code false} otherwise.
   */
  public abstract boolean isFileIgnored(@NonNls @NotNull String name);

  /**
   * Returns the list of extensions associated with the specified file type.
   *
   * @param type The file type for which the extensions are requested.
   * @return The array of extensions associated with the file type.
   * @deprecated since more generic way of associations using wildcards exist, not every association matches extension paradigm
   */
  @Deprecated
  public abstract String @NotNull [] getAssociatedExtensions(@NotNull FileType type);

  public abstract @NotNull List<FileNameMatcher> getAssociations(@NotNull FileType type);

  /**
   * Adds a listener for receiving notifications about changes in the list of
   * registered file types.
   *
   * @param listener The listener instance.
   * @deprecated Subscribe to {@link #TOPIC} on any message bus level instead.
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public abstract void addFileTypeListener(@NotNull FileTypeListener listener);

  /**
   * Removes a listener for receiving notifications about changes in the list of
   * registered file types.
   *
   * @param listener The listener instance.
   * @deprecated Subscribe to {@link #TOPIC} on any message bus level instead.
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public abstract void removeFileTypeListener(@NotNull FileTypeListener listener);

  /**
   * If file is already associated with any known file type returns it.
   * Otherwise asks user to select file type and associates it with fileName extension if any selected.
   *
   * @param file file to ask for file type association
   * @return Known file type or {@code null}. Never returns {@link FileTypes#UNKNOWN}.
   * @deprecated Use {@link #getKnownFileTypeOrAssociate(VirtualFile, Project)} instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  public @Nullable FileType getKnownFileTypeOrAssociate(@NotNull VirtualFile file) { return file.getFileType(); }

  public abstract @Nullable FileType getKnownFileTypeOrAssociate(@NotNull VirtualFile file, @NotNull Project project);

  /**
   * Returns the semicolon-delimited list of patterns for files and folders
   * which are excluded from the project structure though they may be present
   * physically on disk.
   *
   * @return Semicolon-delimited list of patterns.
   */
  public abstract @NotNull String getIgnoredFilesList();

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
   */
  public final void removeAssociatedExtension(@NotNull FileType type, @NotNull @NonNls String extension) {
    removeAssociation(type, new ExtensionFileNameMatcher(extension));
  }

  public abstract void removeAssociation(@NotNull FileType type, @NotNull FileNameMatcher matcher);

  public static @NotNull FileNameMatcher parseFromString(@NotNull String pattern) {
    return FileNameMatcherFactory.getInstance().createMatcher(pattern);
  }

  public abstract @NotNull FileType getStdFileType(@NotNull @NonNls String fileTypeName);
}
