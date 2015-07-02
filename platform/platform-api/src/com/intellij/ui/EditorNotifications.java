/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ui;

import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 */
public abstract class EditorNotifications extends AbstractProjectComponent {

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

  public EditorNotifications(final Project project) {
    super(project);
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
