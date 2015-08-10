/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.service.project.manage;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.task.TaskData;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapManagerListener;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 * @author Vladislav.Soroka
 * @since 10/27/2014
 */
public class ExternalSystemShortcutsManager implements Disposable {

  private static final String ACTION_ID_PREFIX = "ExternalSystem_";
  @NotNull
  private final Project myProject;
  private ExternalSystemKeyMapListener myKeyMapListener;
  private final List<Listener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  public ExternalSystemShortcutsManager(@NotNull Project project) {
    myProject = project;
  }

  public void init() {
    myKeyMapListener = new ExternalSystemKeyMapListener();
  }

  public String getActionId(@Nullable String projectPath, @Nullable String taskName) {
    StringBuilder result = new StringBuilder(ACTION_ID_PREFIX);
    result.append(myProject.getLocationHash());

    if (projectPath != null) {
      String portablePath = FileUtil.toSystemIndependentName(projectPath);
      File file = new File(portablePath);
      result.append(file.isFile() && file.getParentFile() != null ? file.getParentFile().getName() : file.getName());
      result.append(Integer.toHexString(portablePath.hashCode()));

      if (taskName != null) result.append(taskName);
    }

    return result.toString();
  }

  public String getDescription(@Nullable String projectPath, @Nullable String taskName) {
    Shortcut[] shortcuts = getShortcuts(projectPath, taskName);
    if (shortcuts.length == 0) return "";
    return KeymapUtil.getShortcutsText(shortcuts);
  }

  public boolean hasShortcuts(@Nullable String projectPath, @Nullable String taskName) {
    return getShortcuts(projectPath, taskName).length > 0;
  }

  public boolean hasShortcuts(@NotNull String actionId) {
    Keymap activeKeymap = KeymapManager.getInstance().getActiveKeymap();
    return activeKeymap.getShortcuts(actionId).length > 0;
  }

  @NotNull
  private Shortcut[] getShortcuts(@Nullable String projectPath, @Nullable String taskName) {
    String actionId = getActionId(projectPath, taskName);
    if (actionId == null) return Shortcut.EMPTY_ARRAY;
    Keymap activeKeymap = KeymapManager.getInstance().getActiveKeymap();
    return activeKeymap.getShortcuts(actionId);
  }

  private void fireShortcutsUpdated() {
    for (Listener listener : myListeners) {
      listener.shortcutsUpdated();
    }
  }

  public void addListener(Listener listener) {
    myListeners.add(listener);
  }

  public interface Listener {
    void shortcutsUpdated();
  }

  private class ExternalSystemKeyMapListener implements KeymapManagerListener, Keymap.Listener {
    private Keymap myCurrentKeymap;

    private ExternalSystemKeyMapListener() {
      KeymapManager keymapManager = KeymapManager.getInstance();
      if (keymapManager != null) {
        listenTo(keymapManager.getActiveKeymap());
        keymapManager.addKeymapManagerListener(this, ExternalSystemShortcutsManager.this);
      }
    }

    @Override
    public void activeKeymapChanged(Keymap keymap) {
      listenTo(keymap);
      fireShortcutsUpdated();
    }

    private void listenTo(Keymap keymap) {
      if (myCurrentKeymap != null) {
        myCurrentKeymap.removeShortcutChangeListener(this);
      }
      myCurrentKeymap = keymap;
      if (myCurrentKeymap != null) {
        myCurrentKeymap.addShortcutChangeListener(this);
      }
    }

    @Override
    public void onShortcutChanged(String actionId) {
      fireShortcutsUpdated();
    }

    private void stopListen() {
      listenTo(null);
    }
  }

  public void scheduleKeymapUpdate(Collection<DataNode<TaskData>> taskData) {
    ExternalSystemKeymapExtension.updateActions(myProject, taskData);
  }

  public void scheduleRunConfigurationKeymapUpdate(@NotNull ProjectSystemId externalSystemId) {
    ExternalSystemKeymapExtension.updateRunConfigurationActions(myProject, externalSystemId);
  }

  @Override
  public void dispose() {
    if (myKeyMapListener != null) {
      myKeyMapListener.stopListen();
    }
    ExternalSystemKeymapExtension.clearActions(myProject);
  }
}
