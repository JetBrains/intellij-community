// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class EditorNotifications {
  public static final ExtensionPointName<Provider> EXTENSION_POINT_NAME = ExtensionPointName.create("com.intellij.editorNotificationProvider");
  @NotNull protected final Project myProject;

  /**
   * An extension allowing to add custom notifications to the top of file editors.
   *
   * During indexing, only {@link com.intellij.openapi.project.DumbAware} instances are executed.
   * @param <T> the type of the notification UI component
   */
  public abstract static class Provider<T extends JComponent> {
    @NotNull
    public abstract Key<T> getKey();

    @Nullable
    public abstract T createNotificationPanel(@NotNull VirtualFile file, @NotNull FileEditor fileEditor);
  }

  public static EditorNotifications getInstance(Project project) {
    return project.getComponent(EditorNotifications.class);
  }

  public EditorNotifications(@NotNull Project project) {
    myProject = project;
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
