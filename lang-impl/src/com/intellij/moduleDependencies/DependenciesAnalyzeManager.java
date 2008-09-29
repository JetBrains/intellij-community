package com.intellij.moduleDependencies;

import com.intellij.ide.impl.ContentManagerWatcher;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;

/**
 * User: anna
 * Date: Feb 10, 2005
 */
@State(
    name = "DependenciesAnalyzeManager",
    storages = {@Storage(id = "default", file = "$WORKSPACE_FILE$")}
)
public class DependenciesAnalyzeManager implements PersistentStateComponent<DependenciesAnalyzeManager.State> {
  private Project myProject;
  private ContentManager myContentManager;

  public static class State {
    public boolean myForwardDirection;
  }

  private State myState;

  public DependenciesAnalyzeManager(final Project project) {
    myProject = project;
    StartupManager.getInstance(myProject).runWhenProjectIsInitialized(new Runnable() {
      public void run() {
        ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
        ToolWindow toolWindow = toolWindowManager.registerToolWindow(ToolWindowId.MODULES_DEPENDENCIES,
                                                                     true,
                                                                     ToolWindowAnchor.RIGHT,
                                                                     project);
        myContentManager = toolWindow.getContentManager();
        toolWindow.setIcon(IconLoader.getIcon("/general/toolWindowModuleDependencies.png"));
        new ContentManagerWatcher(toolWindow, myContentManager);
      }
    });
  }

  public static DependenciesAnalyzeManager getInstance(Project project){
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

  public State getState() {
    return myState;
  }

  public void loadState(final State state) {
    myState = state;
  }
}
