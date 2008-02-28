package com.intellij.platform;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.projectImport.ProjectOpenProcessor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;

/**
 * @author max
 */
public class PlatformProjectOpenProcessor extends ProjectOpenProcessor {
  public static VirtualFile BASE_DIR = null;

  public boolean canOpenProject(final VirtualFile file) {
    return file.isDirectory() || !file.getFileType().isBinary();
  }

  @Nullable
  public Project doOpenProject(@NotNull final VirtualFile virtualFile, final Project projectToClose, final boolean forceOpenInNewFrame) {
    if (virtualFile.isDirectory()) {
      BASE_DIR = virtualFile;
    }

    final File projectFile = new File(getIprBaseName() + ".ipr");

    final ProjectManagerEx projectManager = ProjectManagerEx.getInstanceEx();
    Project project = null;
    if (projectFile.exists()) {
      try {
        project = projectManager.loadProject(projectFile.getPath());
      }
      catch (Exception e) {
        // ignore
      }
    }
    if (project == null) {
      project = projectManager.newProject(projectFile.getPath(), true, false);
    }
    if (project == null) return null;
    openFileFromCommandLine(project, virtualFile);
    projectManager.openProject(project);

    return project;
  }

  public static String getIprBaseName() {
    @NonNls String projectsDir = PathManager.getConfigPath() + "/platform/projects/";
    @NonNls String projectName;
    if (BASE_DIR != null && BASE_DIR.isDirectory()) {
      projectName = BASE_DIR.getPath().replace(":", "_").replace("/", "_").replace("\\", "_");
    }
    else {
      projectName = "dummy";
    }
    return new File(projectsDir, projectName).getPath();
  }

  private static void openFileFromCommandLine(final Project project, final VirtualFile virtualFile) {
    StartupManager.getInstance(project).registerPostStartupActivity(new Runnable() {
      public void run() {
        ToolWindowManager.getInstance(project).invokeLater(new Runnable() {
          public void run() {
            ToolWindowManager.getInstance(project).invokeLater(new Runnable() {
              public void run() {
                if (!virtualFile.isDirectory()) {
                  FileEditorManager.getInstance(project).openFile(virtualFile, true);
                }
              }
            });
          }
        });
      }
    });
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