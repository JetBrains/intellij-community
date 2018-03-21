// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.ide.impl;

import com.intellij.ide.GeneralSettings;
import com.intellij.ide.util.newProjectWizard.AbstractProjectWizard;
import com.intellij.ide.util.projectWizard.ProjectBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
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
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.ex.IdeFrameEx;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

public class NewProjectUtil {
  private final static Logger LOG = Logger.getInstance(NewProjectUtil.class);

  private NewProjectUtil() { }

  public static void createNewProject(@Nullable Project projectToClose, @NotNull AbstractProjectWizard wizard) {
    String title = ProjectBundle.message("project.new.wizard.progress.title");
    Runnable warmUp = () -> ProjectManager.getInstance().getDefaultProject();  // warm-up components
    boolean proceed = ProgressManager.getInstance().runProcessWithProgressSynchronously(warmUp, title, true, null);
    if (proceed && wizard.showAndGet()) {
      createFromWizard(wizard, projectToClose);
    }
  }

  public static Project createFromWizard(@NotNull AbstractProjectWizard wizard, @Nullable Project projectToClose) {
    try {
      return doCreate(wizard, projectToClose);
    }
    catch (IOException e) {
      UIUtil.invokeLaterIfNeeded(() -> Messages.showErrorDialog(e.getMessage(), "Project Initialization Failed"));
      return null;
    }
  }

  private static Project doCreate(AbstractProjectWizard wizard, @Nullable Project projectToClose) throws IOException {
    ProjectManagerEx projectManager = ProjectManagerEx.getInstanceEx();
    String projectFilePath = wizard.getNewProjectFilePath();
    for (Project p : ProjectManager.getInstance().getOpenProjects()) {
      if (ProjectUtil.isSameProject(projectFilePath, p)) {
        ProjectUtil.focusProjectWindow(p, false);
        return null;
      }
    }

    ProjectBuilder projectBuilder = wizard.getProjectBuilder();
    LOG.debug("builder " + projectBuilder);

    try {
      File directoryToCreate = new File(projectFilePath);
      if (wizard.getStorageScheme() == StorageScheme.DEFAULT) {
        directoryToCreate = directoryToCreate.getParentFile();
        if (directoryToCreate == null) {
          throw new IOException("Cannot create project in '" + projectFilePath + "': no parent file exists");
        }
      }
      else if (wizard.getStorageScheme() == StorageScheme.DIRECTORY_BASED) {
        directoryToCreate = new File(projectFilePath, Project.DIRECTORY_STORE_FOLDER);
      }
      FileUtil.ensureExists(directoryToCreate);

      Project newProject;
      if (projectBuilder == null || !projectBuilder.isUpdate()) {
        String name = wizard.getProjectName();
        newProject = projectBuilder == null
                   ? projectManager.newProject(name, projectFilePath, true, false)
                   : projectBuilder.createProject(name, projectFilePath);
      }
      else {
        newProject = projectToClose;
      }

      if (newProject == null) return projectToClose;

      Sdk jdk = wizard.getNewProjectJdk();
      if (jdk != null) {
        CommandProcessor.getInstance().executeCommand(newProject, () -> ApplicationManager.getApplication().runWriteAction(() -> applyJdkToProject(newProject, jdk)), null, null);
      }

      String compileOutput = wizard.getNewCompileOutput();
      CommandProcessor.getInstance().executeCommand(newProject, () -> ApplicationManager.getApplication().runWriteAction(() -> {
        CompilerProjectExtension extension = CompilerProjectExtension.getInstance(newProject);
        if (extension != null) {
          String canonicalPath = compileOutput;
          try {
            canonicalPath = FileUtil.resolveShortWindowsName(compileOutput);
          }
          catch (IOException ignored) { }
          extension.setCompilerOutputUrl(VfsUtilCore.pathToUrl(FileUtil.toSystemIndependentName(canonicalPath)));
        }
      }), null, null);

      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        newProject.save();
      }

      if (projectBuilder != null && !projectBuilder.validate(projectToClose, newProject)) {
        return projectToClose;
      }

      if (newProject != projectToClose && !ApplicationManager.getApplication().isUnitTestMode()) {
        closePreviousProject(projectToClose);
      }

      if (projectBuilder != null) {
        projectBuilder.commit(newProject, null, ModulesProvider.EMPTY_MODULES_PROVIDER);
      }

      boolean need2OpenProjectStructure = projectBuilder == null || projectBuilder.isOpenProjectSettingsAfter();
      StartupManager.getInstance(newProject).registerPostStartupActivity(() -> {
        // ensure the dialog is shown after all startup activities are done
        ApplicationManager.getApplication().invokeLater(() -> {
          if (newProject.isDisposed() || ApplicationManager.getApplication().isUnitTestMode()) return;
          if (need2OpenProjectStructure) {
            ModulesConfigurator.showDialog(newProject, null, null);
          }
          ApplicationManager.getApplication().invokeLater(() -> {
            if (newProject.isDisposed()) return;
            ToolWindow toolWindow = ToolWindowManager.getInstance(newProject).getToolWindow(ToolWindowId.PROJECT_VIEW);
            if (toolWindow != null) {
              toolWindow.activate(null);
            }
          }, ModalityState.NON_MODAL);
        }, ModalityState.NON_MODAL);
      });

      if (newProject != projectToClose) {
        ProjectUtil.updateLastProjectLocation(projectFilePath);

        if (WindowManager.getInstance().isFullScreenSupportedInCurrentOS()) {
          IdeFocusManager instance = IdeFocusManager.findInstance();
          IdeFrame lastFocusedFrame = instance.getLastFocusedFrame();
          if (lastFocusedFrame instanceof IdeFrameEx) {
            boolean fullScreen = ((IdeFrameEx)lastFocusedFrame).isInFullScreen();
            if (fullScreen) {
              newProject.putUserData(IdeFrameImpl.SHOULD_OPEN_IN_FULL_SCREEN, Boolean.TRUE);
            }
          }
        }

        projectManager.openProject(newProject);
      }
      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        newProject.save();
      }
      return newProject;
    }
    finally {
      if (projectBuilder != null) {
        projectBuilder.cleanup();
      }
    }
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

  public static void closePreviousProject(Project projectToClose) {
    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    if (openProjects.length > 0) {
      int exitCode = ProjectUtil.confirmOpenNewProject(true);
      if (exitCode == GeneralSettings.OPEN_PROJECT_SAME_WINDOW) {
        ProjectUtil.closeAndDispose(projectToClose != null ? projectToClose : openProjects[openProjects.length - 1]);
      }
    }
  }
}