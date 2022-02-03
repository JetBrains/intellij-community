// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.ide.lightEdit.LightEditService;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Function;

public abstract class EditorNotifications {

  private static final EditorNotifications NULL_IMPL = new EditorNotifications() {
    @Override
    public void updateNotifications(@NotNull VirtualFile file) {
    }

    @Override
    public void updateNotifications(@NotNull EditorNotificationProvider provider) {
    }

    @Override
    public void updateAllNotifications() {
    }

    @Override
    public void logNotificationActionInvocation(@NotNull EditorNotificationProvider provider,
                                                @NotNull Class<?> handlerClass) {
    }
  };

  /**
   * @deprecated Please use {@link EditorNotificationProvider} instead.
   */
  @Deprecated
  public abstract static class Provider<T extends JComponent> implements EditorNotificationProvider {

    /**
     * A unique key.
     */
    public abstract @NotNull Key<T> getKey();

    @RequiresEdt
    public @Nullable T createNotificationPanel(@NotNull VirtualFile file,
                                               @NotNull FileEditor fileEditor) {
      throw new AbstractMethodError();
    }

    @RequiresEdt
    public @Nullable T createNotificationPanel(@NotNull VirtualFile file,
                                               @NotNull FileEditor fileEditor,
                                               @NotNull Project project) {
      return createNotificationPanel(file, fileEditor);
    }

    @Override
    public @NotNull Function<? super @NotNull FileEditor, @Nullable T> collectNotificationData(@NotNull Project project,
                                                                                               @NotNull VirtualFile file) {
      return fileEditor -> createNotificationPanel(file, fileEditor, project);
    }
  }

  public static @NotNull EditorNotifications getInstance(@NotNull Project project) {
    return project.isDefault() ? NULL_IMPL : project.getService(EditorNotifications.class);
  }

  public abstract void updateNotifications(@NotNull VirtualFile file);

  public abstract void updateNotifications(@NotNull EditorNotificationProvider provider);

  public abstract void updateAllNotifications();

  @ApiStatus.Internal
  public abstract void logNotificationActionInvocation(@NotNull EditorNotificationProvider provider,
                                                       @NotNull Class<?> handlerClass);

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
