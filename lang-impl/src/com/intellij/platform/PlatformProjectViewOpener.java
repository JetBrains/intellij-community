package com.intellij.platform;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ToolWindowId;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author yole
 */
public class PlatformProjectViewOpener implements DirectoryProjectConfigurator {
  public void configureProject(final Project project, @NotNull final VirtualFile baseDir) {
    StartupManager.getInstance(project).registerPostStartupActivity(new Runnable() {
      public void run() {
        // ensure the dialog is shown after all startup activities are done
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              public void run() {
                final ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.PROJECT_VIEW);
                if (toolWindow != null) {
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
