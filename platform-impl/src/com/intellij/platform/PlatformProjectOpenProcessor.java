/*
 * @author max
 */
package com.intellij.platform;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.projectImport.ProjectOpenProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class PlatformProjectOpenProcessor extends ProjectOpenProcessor {
  public static VirtualFile BASE_DIR = null;

  public boolean canOpenProject(final VirtualFile file) {
    return file.isDirectory() || !file.getFileType().isBinary();
  }

  @Nullable
  public Project doOpenProject(@NotNull final VirtualFile virtualFile, final Project projectToClose, final boolean forceOpenInNewFrame) {
    final ProjectManagerEx projectManager = ProjectManagerEx.getInstanceEx();
    final Project project = projectManager.newProject(PathManager.getConfigPath() + "/dummy.ipr", true, false);

    if (virtualFile.isDirectory()) {
      BASE_DIR = virtualFile;
    }

    StartupManager.getInstance(project).registerPostStartupActivity(new Runnable() {
      public void run() {
        ToolWindowManager.getInstance(project).invokeLater(new Runnable() {
          public void run() {
            ToolWindowManager.getInstance(project).invokeLater(new Runnable() {
              public void run() {
                if (virtualFile.isDirectory()) {
                  new FilesystemToolwindow(virtualFile, project);
                }
                else {
                  FileEditorManager.getInstance(project).openFile(virtualFile, true);
                }
              }
            });
          }
        });
      }
    });

    projectManager.openProject(project);

    return project;
  }

  @Nullable
  public Icon getIcon() {
    return null;
  }

  public String getName() {
    return "text editor";
  }

  public static VirtualFile getBaseDir(final VirtualFile baseDir) {
    if (BASE_DIR != null) {
      return BASE_DIR;
    }
    return baseDir;
  }
}