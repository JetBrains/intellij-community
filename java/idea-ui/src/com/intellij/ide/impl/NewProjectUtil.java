// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.ide.impl;

import com.intellij.ide.JavaUiBundle;
import com.intellij.ide.SaveAndSyncHandler;
import com.intellij.ide.util.newProjectWizard.AbstractProjectWizard;
import com.intellij.ide.util.projectWizard.ProjectBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.CompilerProjectExtension;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class NewProjectUtil {
  private final static Logger LOG = Logger.getInstance(NewProjectUtil.class);

  private NewProjectUtil() { }

  /**
   * @deprecated Use {@link #createNewProject(AbstractProjectWizard)}, projectToClose param is not used.
   */
  @Deprecated
  public static void createNewProject(@SuppressWarnings("unused") @Nullable Project projectToClose, @NotNull AbstractProjectWizard wizard) {
    createNewProject(wizard);
  }

  public static void createNewProject(@NotNull AbstractProjectWizard wizard) {
    String title = JavaUiBundle.message("project.new.wizard.progress.title");
    Runnable warmUp = () -> ProjectManager.getInstance().getDefaultProject();  // warm-up components
    boolean proceed = ProgressManager.getInstance().runProcessWithProgressSynchronously(warmUp, title, true, null);
    if (proceed && wizard.showAndGet()) {
      createFromWizard(wizard);
    }
  }

  public static Project createFromWizard(@NotNull AbstractProjectWizard wizard) {
    return createFromWizard(wizard, null);
  }

  public static Project createFromWizard(@NotNull AbstractProjectWizard wizard, @Nullable Project projectToClose) {
    try {
      return doCreate(wizard, projectToClose);
    }
    catch (IOException e) {
      UIUtil.invokeLaterIfNeeded(() -> Messages.showErrorDialog(e.getMessage(),
                                                                JavaUiBundle.message("dialog.title.project.initialization.failed")));
      return null;
    }
  }

  private static Project doCreate(@NotNull AbstractProjectWizard wizard, @Nullable Project projectToClose) throws IOException {
    String projectFilePath = wizard.getNewProjectFilePath();
    for (Project p : ProjectUtil.getOpenProjects()) {
      if (ProjectUtil.isSameProject(Paths.get(projectFilePath), p)) {
        ProjectUtil.focusProjectWindow(p, false);
        return null;
      }
    }

    ProjectBuilder projectBuilder = wizard.getProjectBuilder();
    LOG.debug("builder " + projectBuilder);

    ProjectManagerEx projectManager = ProjectManagerEx.getInstanceEx();
    try {
      Path projectFile = Paths.get(projectFilePath);
      Path projectDir;
      if (wizard.getStorageScheme() == StorageScheme.DEFAULT) {
        projectDir = projectFile.getParent();
        if (projectDir == null) {
          throw new IOException("Cannot create project in '" + projectFilePath + "': no parent file exists");
        }
      }
      else {
        projectDir = projectFile;
      }
      Files.createDirectories(projectDir);

      Project newProject;
      if (projectBuilder == null || !projectBuilder.isUpdate()) {
        String name = wizard.getProjectName();
        if (projectBuilder == null) {
          newProject = projectManager.newProject(projectFile, OpenProjectTask.newProject().withProjectName(name));
        }
        else {
          newProject = projectBuilder.createProject(name, projectFilePath);
        }
      }
      else {
        newProject = projectToClose;
      }

      if (newProject == null) {
        return projectToClose;
      }

      Sdk jdk = wizard.getNewProjectJdk();
      if (jdk != null) {
        CommandProcessor.getInstance().executeCommand(newProject, () -> ApplicationManager.getApplication().runWriteAction(() -> applyJdkToProject(newProject, jdk)), null, null);
      }

      String compileOutput = wizard.getNewCompileOutput();
      setCompilerOutputPath(newProject, compileOutput);

      if (projectBuilder != null) {
        // validate can require project on disk
        if (!ApplicationManager.getApplication().isUnitTestMode()) {
          newProject.save();
        }

        if (!projectBuilder.validate(projectToClose, newProject)) {
          return projectToClose;
        }

        projectBuilder.commit(newProject, null, ModulesProvider.EMPTY_MODULES_PROVIDER);
      }

      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        boolean needToOpenProjectStructure = projectBuilder == null || projectBuilder.isOpenProjectSettingsAfter();
        StartupManager.getInstance(newProject).registerPostStartupActivity(() -> {
          // ensure the dialog is shown after all startup activities are done
          ApplicationManager.getApplication().invokeLater(() -> {
            if (needToOpenProjectStructure) {
              ModulesConfigurator.showDialog(newProject, null, null);
            }
            ApplicationManager.getApplication().invokeLater(() -> {
              ToolWindow toolWindow = ToolWindowManager.getInstance(newProject).getToolWindow(ToolWindowId.PROJECT_VIEW);
              if (toolWindow != null) {
                toolWindow.activate(null);
              }
            }, ModalityState.NON_MODAL, newProject.getDisposed());
          }, ModalityState.NON_MODAL, newProject.getDisposed());
        });
      }

      if (newProject != projectToClose) {
        ProjectUtil.updateLastProjectLocation(projectFile);
        ProjectManagerEx.getInstanceEx().openProject(projectDir, OpenProjectTask.withCreatedProject(newProject).withProjectName(projectFile.getFileName().toString()));
      }

      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        SaveAndSyncHandler.getInstance().scheduleProjectSave(newProject);
      }
      return newProject;
    }
    finally {
      if (projectBuilder != null) {
        projectBuilder.cleanup();
      }
    }
  }

  public static void setCompilerOutputPath(@NotNull Project project, @NotNull String path) {
    CommandProcessor.getInstance().executeCommand(project, () -> ApplicationManager.getApplication().runWriteAction(() -> {
      CompilerProjectExtension extension = CompilerProjectExtension.getInstance(project);
      if (extension != null) {
        String canonicalPath = path;
        try {
          canonicalPath = FileUtil.resolveShortWindowsName(path);
        }
        catch (IOException ignored) { }
        extension.setCompilerOutputUrl(VfsUtilCore.pathToUrl(canonicalPath));
      }
    }), null, null);
  }

  public static void applyJdkToProject(@NotNull Project project, @NotNull Sdk jdk) {
    ProjectRootManagerEx rootManager = ProjectRootManagerEx.getInstanceEx(project);
    rootManager.setProjectSdk(jdk);

    JavaSdkVersion version = JavaSdk.getInstance().getVersion(jdk);
    if (version != null) {
      LanguageLevel maxLevel = version.getMaxLanguageLevel();
      LanguageLevelProjectExtension extension = LanguageLevelProjectExtension.getInstance(ProjectManager.getInstance().getDefaultProject());
      LanguageLevelProjectExtension ext = LanguageLevelProjectExtension.getInstance(project);
      if (extension.isDefault() || maxLevel.compareTo(ext.getLanguageLevel()) < 0) {
        ext.setLanguageLevel(maxLevel);
      }
    }
  }
}