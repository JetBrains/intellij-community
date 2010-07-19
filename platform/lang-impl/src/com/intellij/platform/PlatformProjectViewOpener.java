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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowType;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author yole
 */
public class PlatformProjectViewOpener implements DirectoryProjectConfigurator {
  public void configureProject(final Project project, @NotNull final VirtualFile baseDir) {
    StartupManager.getInstance(project).registerPostStartupActivity(new DumbAwareRunnable() {
      public void run() {
        // ensure the dialog is shown after all startup activities are done
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              public void run() {
                if (project.isDisposed()) return;
                final ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.PROJECT_VIEW);
                if (toolWindow != null && toolWindow.getType() != ToolWindowType.SLIDING) {
                  toolWindow.activate(null);
                }
              }
            }, ModalityState.NON_MODAL);
          }
        });
      }
    });
  }
}
