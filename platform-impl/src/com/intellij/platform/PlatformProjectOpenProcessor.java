package com.intellij.platform;

import com.intellij.openapi.extensions.Extensions;
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
import java.io.File;

/**
 * @author max
 */
public class PlatformProjectOpenProcessor extends ProjectOpenProcessor {
  public static PlatformProjectOpenProcessor getInstance() {
    ProjectOpenProcessor[] processors = Extensions.getExtensions(EXTENSION_POINT_NAME);
    for(ProjectOpenProcessor processor: processors) {
      if (processor instanceof PlatformProjectOpenProcessor) {
        return (PlatformProjectOpenProcessor) processor;
      }
    }
    assert false;
    return null;
  }

  public boolean canOpenProject(final VirtualFile file) {
    return file.isDirectory() || !file.getFileType().isBinary();
  }

  @Nullable
  public Project doOpenProject(@NotNull final VirtualFile virtualFile, final Project projectToClose, final boolean forceOpenInNewFrame) {
    VirtualFile baseDir = virtualFile.isDirectory() ? virtualFile : virtualFile.getParent();
    final File projectDir = new File(baseDir.getPath(), ".idea");

    final ProjectManagerEx projectManager = ProjectManagerEx.getInstanceEx();
    Project project = null;
    if (projectDir.exists()) {
      try {
        project = projectManager.loadProject(baseDir.getPath());
      }
      catch (Exception e) {
        // ignore
      }
    }
    else {
      projectDir.mkdirs();
    }
    if (project == null) {
      project = projectManager.newProject(projectDir.getParentFile().getName(), projectDir.getParent(), true, false);
    }
    if (project == null) return null;
    ProjectBaseDirectory.getInstance(project).setBaseDir(baseDir);
    for(DirectoryProjectConfigurator configurator: Extensions.getExtensions(DirectoryProjectConfigurator.EP_NAME)) {
      configurator.configureProject(project, baseDir);
    }

    openFileFromCommandLine(project, virtualFile);
    projectManager.openProject(project);

    return project;
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
}