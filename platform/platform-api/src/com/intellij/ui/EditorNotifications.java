// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class EditorNotifications {
  /**
   * An extension allowing to add custom notifications to the top of file editors.
   *
   * During indexing, only {@link com.intellij.openapi.project.DumbAware} instances are executed.
   * @param <T> the type of the notification UI component
   */
  public abstract static class Provider<T extends JComponent> {
    @NotNull
    public abstract Key<T> getKey();

    /**
     * @deprecated Override {@link #createNotificationPanel(VirtualFile, FileEditor, Project)}
     */
    @SuppressWarnings({"DeprecatedIsStillUsed", "unused"})
    @Nullable
    @Deprecated
    public T createNotificationPanel(@NotNull VirtualFile file, @NotNull FileEditor fileEditor) {
      throw new AbstractMethodError();
    }

    @Nullable
    public T createNotificationPanel(@NotNull VirtualFile file, @NotNull FileEditor fileEditor, @NotNull Project project) {
      return createNotificationPanel(file, fileEditor);
    }
  }

  public static EditorNotifications getInstance(Project project) {
    return project.getComponent(EditorNotifications.class);
  }

  public abstract void updateNotifications(@NotNull VirtualFile file);

  public abstract void updateAllNotifications();

  public static void updateAll() {
    Project[] projects = ProjectManager.getInstance().getOpenProjects();
    for (Project project : projects) {
      getInstance(project).updateAllNotifications();
    }
  }
}
