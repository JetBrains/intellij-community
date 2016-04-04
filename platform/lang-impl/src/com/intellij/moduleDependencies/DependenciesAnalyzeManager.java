/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.moduleDependencies;

import com.intellij.icons.AllIcons;
import com.intellij.ide.impl.ContentManagerWatcher;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author anna
 * @since Feb 10, 2005
 */
@State(name = "DependenciesAnalyzeManager", storages = {@Storage(StoragePathMacros.WORKSPACE_FILE)})
public class DependenciesAnalyzeManager implements PersistentStateComponent<DependenciesAnalyzeManager.State> {
  private final Project myProject;
  private ContentManager myContentManager;

  public static class State {
    public boolean forwardDirection = true;
    public boolean includeTests = false;
  }

  private State myState = new State();

  public DependenciesAnalyzeManager(final Project project) {
    myProject = project;
    StartupManager.getInstance(myProject).runWhenProjectIsInitialized(new Runnable() {
      @Override
      public void run() {
        ToolWindowManager manager = ToolWindowManager.getInstance(myProject);
        ToolWindow toolWindow = manager.registerToolWindow(ToolWindowId.MODULES_DEPENDENCIES, true, ToolWindowAnchor.RIGHT, project);
        myContentManager = toolWindow.getContentManager();
        toolWindow.setIcon(AllIcons.Toolwindows.ToolWindowModuleDependencies);
        new ContentManagerWatcher(toolWindow, myContentManager);
      }
    });
  }

  public static DependenciesAnalyzeManager getInstance(Project project) {
    return ServiceManager.getService(project, DependenciesAnalyzeManager.class);
  }

  public void addContent(Content content) {
    myContentManager.addContent(content);
    myContentManager.setSelectedContent(content);
    ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.MODULES_DEPENDENCIES).activate(null);
  }

  public void closeContent(Content content) {
    myContentManager.removeContent(content, true);
  }

  @Override
  @NotNull
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(State state) {
    myState = state != null ? state : new State();
  }
}