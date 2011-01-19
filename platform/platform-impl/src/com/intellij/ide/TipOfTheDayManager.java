/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.ide;

import com.intellij.ide.util.TipDialog;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

public class TipOfTheDayManager extends ProjectManagerAdapter implements ApplicationComponent {
  private boolean myDoNotShowThisTime = false;
  private boolean myVeryFirstProjectOpening = true;

  public static TipOfTheDayManager getInstance() {
    return ApplicationManager.getApplication().getComponent(TipOfTheDayManager.class);
  }


  public TipOfTheDayManager(ProjectManager projectManager) {
    projectManager.addProjectManagerListener(this);
  }

  @NotNull
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

    StartupManager.getInstance(project).registerPostStartupActivity(new DumbAwareRunnable() {
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
