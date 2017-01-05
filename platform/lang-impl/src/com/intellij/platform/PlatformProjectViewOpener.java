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

package com.intellij.platform;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ToolWindowType;
import com.intellij.openapi.wm.ex.ToolWindowManagerAdapter;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import org.jetbrains.annotations.NotNull;

public class PlatformProjectViewOpener implements DirectoryProjectConfigurator {
  @Override
  public void configureProject(final Project project, @NotNull final VirtualFile baseDir, Ref<Module> moduleRef) {
    ToolWindowManagerEx manager = (ToolWindowManagerEx)ToolWindowManager.getInstance(project);
    ToolWindow toolWindow = manager.getToolWindow(ToolWindowId.PROJECT_VIEW);
    if (toolWindow == null) {
      manager.addToolWindowManagerListener(new MyListener(manager, project));
    }
    else {
      StartupManager.getInstance(project).runWhenProjectIsInitialized(new DumbAwareRunnable() {
        @Override
        public void run() {
          activateProjectToolWindow(project, toolWindow);
        }
      });
    }
  }

  private static void activateProjectToolWindow(@NotNull Project project, @NotNull ToolWindow toolWindow) {
    ApplicationManager.getApplication().invokeLater(() -> {
      if (project.isDisposed()) return;
      if (toolWindow.getType() != ToolWindowType.SLIDING) {
        toolWindow.activate(null);
      }
    }, ModalityState.NON_MODAL);
  }

  private static class MyListener extends ToolWindowManagerAdapter {
    private final ToolWindowManagerEx myManager;
    private final Project myProject;

    public MyListener(ToolWindowManagerEx manager, Project project) {
      myManager = manager;
      myProject = project;
    }

    public void toolWindowRegistered(@NotNull final String id) {
      if (id.equals(ToolWindowId.PROJECT_VIEW)) {
        myManager.removeToolWindowManagerListener(this);
        activateProjectToolWindow(myProject, myManager.getToolWindow(id));
      }
    }
  }
}
