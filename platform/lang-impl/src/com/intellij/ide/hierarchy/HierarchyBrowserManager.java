// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.hierarchy;

import com.intellij.icons.AllIcons;
import com.intellij.ide.impl.ContentManagerWatcher;
import com.intellij.openapi.application.AppUIExecutor;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.*;
import com.intellij.ui.UIBundle;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.annotations.NotNull;

@State(name = "HierarchyBrowserManager", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class HierarchyBrowserManager implements PersistentStateComponent<HierarchyBrowserManager.State> {
  public static final class State {
    public boolean IS_AUTOSCROLL_TO_SOURCE;
    public boolean SORT_ALPHABETICALLY;
    public boolean HIDE_CLASSES_WHERE_METHOD_NOT_IMPLEMENTED;
    public String SCOPE;
    public String EXPORT_FILE_PATH;
  }

  private State myState = new State();
  private ContentManager myContentManager;

  public HierarchyBrowserManager(@NotNull Project project) {
    AppUIExecutor.onUiThread().expireWith(project).submit(() -> {
      ToolWindow toolWindow = ToolWindowManager.getInstance(project).registerToolWindow(
        RegisterToolWindowTask.closable(ToolWindowId.HIERARCHY, UIBundle.messagePointer("tool.window.name.hierarchy"),
                                        AllIcons.Toolwindows.ToolWindowHierarchy, ToolWindowAnchor.RIGHT));

      myContentManager = toolWindow.getContentManager();
      ContentManagerWatcher.watchContentManager(toolWindow, myContentManager);
    });
  }

  public final ContentManager getContentManager() {
    return myContentManager;
  }

  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull final State state) {
    myState = state;
  }

  public static HierarchyBrowserManager getInstance(@NotNull Project project) {
    return project.getService(HierarchyBrowserManager.class);
  }

  public static State getSettings(@NotNull Project project) {
    State state = getInstance(project).getState();
    return state != null ? state : new State();
  }
}