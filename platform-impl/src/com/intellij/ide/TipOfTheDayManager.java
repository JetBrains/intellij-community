package com.intellij.ide;

import com.intellij.ide.util.TipDialog;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.wm.ToolWindowManager;

public class TipOfTheDayManager extends ProjectManagerAdapter implements ApplicationComponent {
  private boolean myDoNotShowThisTime = false;
  private boolean myVeryFirstProjectOpening = true;

  public static TipOfTheDayManager getInstance() {
    return ApplicationManager.getApplication().getComponent(TipOfTheDayManager.class);
  }


  public TipOfTheDayManager(ProjectManager projectManager) {
    projectManager.addProjectManagerListener(this);
  }

  public String getComponentName() {
    return "TipOfTheDayManager";
  }

  public void initComponent() { }

  public void disposeComponent() {
    ProjectManager.getInstance().removeProjectManagerListener(this);
  }

  public void projectOpened(final Project project) {
    if (!myVeryFirstProjectOpening || !GeneralSettings.getInstance().showTipsOnStartup()) {
      return;
    }

    myVeryFirstProjectOpening = false;

    StartupManager.getInstance(project).registerPostStartupActivity(new Runnable() {
      public void run() {
        if (myDoNotShowThisTime) return;
        ToolWindowManager.getInstance(project).invokeLater(new Runnable() {
          public void run() {
            if (project.isDisposed()) return;
            ToolWindowManager.getInstance(project).invokeLater(new Runnable() {
              public void run() {
                if (project.isDisposed()) return;
                new TipDialog().show();
              }
            });
          }
        });
      }
    });
  }

  public void doNotShowThisTime() {
    myDoNotShowThisTime = true;
  }
}
