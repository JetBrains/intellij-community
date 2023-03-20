// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.CachedSingletonsRegistry;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.fileTypes.FileNameMatcherFactory;

import java.util.List;
import java.util.function.Supplier;

/**
 * Manages the relationship between filenames and {@link FileType} instances.
 */
public abstract class FileTypeManager extends FileTypeRegistry {
  static {
    FileTypeRegistry.setInstanceSupplier(FileTypeManager::getInstance);
  }

  private static final Supplier<FileTypeManager> ourInstance = CachedSingletonsRegistry.lazy(() -> {
    Application app = ApplicationManager.getApplication();
    return app == null ? new MockFileTypeManager() : app.getService(FileTypeManager.class);
  });

  public static final @NotNull Topic<FileTypeListener> TOPIC = new Topic<>(FileTypeListener.class, Topic.BroadcastDirection.TO_DIRECT_CHILDREN);

  /**
   * Returns the singleton instance of the FileTypeManager component.
   */
  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static FileTypeManager getInstance() {
    return ourInstance.get();
  }

  /** @deprecated use {@code com.intellij.fileType} extension point instead */
  @Deprecated(forRemoval = true)
  public abstract void registerFileType(@NotNull FileType type, String @Nullable ... defaultAssociatedExtensions);

  /**
   * Checks if the specified file is ignored by the IDE. Ignored files are not visible in
   * different project views and cannot be opened in the editor. They will neither be parsed nor compiled.
   *
   * @param name The name of the file to check.
   * @return {@code true} if the file is ignored, {@code false} otherwise.
   */
  public abstract boolean isFileIgnored(@NotNull String name);

  /** @deprecated obsolete - file type associations aren't limited to a mere extensions list (see {@link #getAssociations}) */
  @Deprecated(forRemoval = true)
  public abstract String @NotNull [] getAssociatedExtensions(@NotNull FileType type);

  public abstract @NotNull List<FileNameMatcher> getAssociations(@NotNull FileType type);

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
   * Sets a new list of semicolon-delimited patterns for files and folders which
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
  public final void associateExtension(@NotNull FileType type, @NotNull String extension) {
    associate(type, new ExtensionFileNameMatcher(extension));
  }

  public final void associatePattern(@NotNull FileType type, @NotNull String pattern) {
    associate(type, parseFromString(pattern));
  }

  public abstract void associate(@NotNull FileType type, @NotNull FileNameMatcher matcher);

  /**
   * Removes an extension from the list of extensions associated with a file type.
   *
   * @param type      the file type to remove the extension from.
   * @param extension the extension to remove.
   */
  public final void removeAssociatedExtension(@NotNull FileType type, @NotNull String extension) {
    removeAssociation(type, new ExtensionFileNameMatcher(extension));
  }

  public abstract void removeAssociation(@NotNull FileType type, @NotNull FileNameMatcher matcher);

  public static @NotNull FileNameMatcher parseFromString(@NotNull String pattern) {
    return FileNameMatcherFactory.getInstance().createMatcher(pattern);
  }

  public abstract @NotNull FileType getStdFileType(@NotNull String fileTypeName);
}
