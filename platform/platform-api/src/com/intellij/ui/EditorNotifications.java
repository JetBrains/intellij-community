// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.ide.lightEdit.LightEditService;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Supplier;

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
   * Adds custom notification/UI to the top of file editors.
   * <p>
   * During indexing, only {@link com.intellij.openapi.project.DumbAware} instances are shown.
   * </p>
   * <p>
   * Register in {@code com.intellij.editorNotificationProvider} extension point.
   * </p>
   *
   * @param <T> the type of the notification UI component, see also {@link EditorNotificationPanel}
   */
  public abstract static class Provider<T extends JComponent> {

    /**
     * Unique key.
     */
    public abstract @NotNull Key<T> getKey();

    /**
     * @deprecated Override {@link #createNotificationPanel(VirtualFile, FileEditor, Project)}
     */
    @SuppressWarnings({"unused"})
    @Deprecated
    @RequiresEdt
    @RequiresReadLock
    public @Nullable T createNotificationPanel(@NotNull VirtualFile file,
                                               @NotNull FileEditor fileEditor) {
      throw new AbstractMethodError();
    }

    @RequiresEdt
    @RequiresReadLock
    public @Nullable T createNotificationPanel(@NotNull VirtualFile file,
                                               @NotNull FileEditor fileEditor,
                                               @NotNull Project project) {
      return createNotificationPanel(file, fileEditor);
    }

    @ApiStatus.Experimental
    @RequiresReadLock
    public @NotNull Supplier<JComponent> collectNotificationData(@NotNull VirtualFile file,
                                                                 @NotNull FileEditor fileEditor,
                                                                 @NotNull Project project) {
      if (this instanceof PanelProvider) {
        PanelProvider.PanelData panelData = ((PanelProvider)this).collectNotificationData(file, project);
        return () -> panelData != null ? panelData.applyTo(fileEditor, project) : null;
      }
      else {
        JComponent component = createNotificationPanel(file, fileEditor, project);
        return () -> component;
      }
    }
  }

  @ApiStatus.Experimental
  public abstract static class PanelProvider extends Provider<EditorNotificationPanel> {

    public interface PanelData {

      @RequiresEdt
      @Nullable EditorNotificationPanel applyTo(@NotNull FileEditor fileEditor,
                                                @NotNull Project project);
    }

    @RequiresReadLock
    public abstract @Nullable PanelData collectNotificationData(@NotNull VirtualFile file,
                                                                @NotNull Project project);

    @Override
    public final @Nullable EditorNotificationPanel createNotificationPanel(@NotNull VirtualFile file,
                                                                           @NotNull FileEditor fileEditor) {
      return super.createNotificationPanel(file, fileEditor);
    }

    @Override
    public final @Nullable EditorNotificationPanel createNotificationPanel(@NotNull VirtualFile file,
                                                                           @NotNull FileEditor fileEditor,
                                                                           @NotNull Project project) {
      return super.createNotificationPanel(file, fileEditor, project);
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
