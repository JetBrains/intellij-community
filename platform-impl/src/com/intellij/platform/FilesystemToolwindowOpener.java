package com.intellij.platform;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class FilesystemToolwindowOpener implements ProjectComponent {
  private Project myProject;

  public FilesystemToolwindowOpener(final Project project) {
    myProject = project;
  }

  public void projectOpened() {
    final VirtualFile baseDir = ProjectBaseDirectory.getInstance(myProject).getBaseDir();
    if (baseDir == null || !baseDir.isDirectory()) return;
    StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
      public void run() {
        ToolWindowManager.getInstance(myProject).invokeLater(new Runnable() {
          public void run() {
            ToolWindowManager.getInstance(myProject).invokeLater(new Runnable() {
              public void run() {
                new FilesystemToolwindow(baseDir, myProject);
              }
            });
          }
        });
      }
    });
  }

  public void projectClosed() {
  }

  @NotNull
  public String getComponentName() {
    return "FilesystemToolwindowOpener";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }
}
