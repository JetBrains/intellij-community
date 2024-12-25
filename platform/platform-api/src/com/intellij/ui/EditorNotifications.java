// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.codeInsight.intention.IntentionActionProvider;
import com.intellij.codeInsight.intention.IntentionActionWithOptions;
import com.intellij.ide.lightEdit.LightEditService;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.List;
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
  };

  /**
   * @return intention actions which were registered via {@link IntentionActionProvider#getIntentionAction()} in this {@link EditorNotificationPanel}
   * @see com.intellij.ui.EditorNotificationsImpl#collectIntentionActions(FileEditor, Project)
   */
  public @NotNull List<IntentionActionWithOptions> getStoredFileLevelIntentions(@NotNull FileEditor fileEditor) {
    return Collections.emptyList();
  }

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

  @ApiStatus.Internal
  public void scheduleUpdateNotifications(@NotNull TextEditor editor) {
  }

  public abstract void updateNotifications(@NotNull VirtualFile file);

  /**
   * This method is broken and should have been named {@link #removeNotificationsForProvider}.
   * It DOES NOT RUN {@link EditorNotificationProvider#collectNotificationData} to check if there are any new notifications.
   * Use {@link #updateAllNotifications} instead.
   *
   * @deprecated until its implementation matches expectations from its name
   */
  @Deprecated
  public abstract void updateNotifications(@NotNull EditorNotificationProvider provider);

  public void removeNotificationsForProvider(@NotNull EditorNotificationProvider provider) {
    updateNotifications(provider);
  }

  public abstract void updateAllNotifications();

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
