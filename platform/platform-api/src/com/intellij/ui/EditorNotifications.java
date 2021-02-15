// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.ide.lightEdit.LightEditService;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class EditorNotifications {
  private static final EditorNotifications NULL_IMPL = new EditorNotifications() {
    @Override
    public void updateNotifications(@NotNull VirtualFile file) {
    }

    @Override
    public void updateNotifications(@NotNull Provider<?> provider) {
    }

    @Override
    public void updateAllNotifications() {
    }

    @Override
    public void logNotificationActionInvocation(@Nullable Key<?> providerKey, @Nullable Class<?> runnableClass) {
    }
  };

  /**
   * An extension allowing to add custom notifications to the top of file editors.
   * <p>
   * During indexing, only {@link com.intellij.openapi.project.DumbAware} instances are executed.
   *
   * @param <T> the type of the notification UI component, see also {@link EditorNotificationPanel}
   */
  public abstract static class Provider<T extends JComponent> {
    @NotNull
    public abstract Key<T> getKey();

    /**
     * @deprecated Override {@link #createNotificationPanel(VirtualFile, FileEditor, Project)}
     */
    @SuppressWarnings({"unused"})
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

  public static @NotNull EditorNotifications getInstance(@NotNull Project project) {
    return project.isDefault() ? NULL_IMPL : project.getService(EditorNotifications.class);
  }

  public abstract void updateNotifications(@NotNull VirtualFile file);

  public abstract void updateNotifications(@NotNull Provider<?> provider);

  public abstract void updateAllNotifications();

  @ApiStatus.Internal
  public abstract void logNotificationActionInvocation(@Nullable Key<?> providerKey, @Nullable Class<?> runnableClass);

  public static void updateAll() {
    Project[] projects = ProjectManager.getInstance().getOpenProjects();
    for (Project project : projects) {
      getInstance(project).updateAllNotifications();
    }
    Project lightEditProject = LightEditService.getInstance().getProject();
    if (lightEditProject != null) {
      getInstance(lightEditProject).updateAllNotifications();
    }
  }
}
