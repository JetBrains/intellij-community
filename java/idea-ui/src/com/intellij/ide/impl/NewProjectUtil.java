/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import javax.swing.*;
import java.io.File;
import java.io.IOException;

public class NewProjectUtil {
  private NewProjectUtil() {
  }

  public static void createNewProject(Project projectToClose, AbstractProjectWizard wizard) {
    final boolean proceed = ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        ProjectManager.getInstance().getDefaultProject(); //warm up components
      }
    }, ProjectBundle.message("project.new.wizard.progress.title"), true, null);
    if (!proceed) return;
    if (!wizard.showAndGet()) {
      return;
    }

    createFromWizard(wizard, projectToClose);
  }

  public static Project createFromWizard(AbstractProjectWizard dialog, Project projectToClose) {
    try {
      return doCreate(dialog, projectToClose);
    }
    catch (final IOException e) {
      UIUtil.invokeLaterIfNeeded(new Runnable() {
        @Override
        public void run() {
          Messages.showErrorDialog(e.getMessage(), "Project Initialization Failed");
        }
      });
      return null;
    }
  }

  private static Project doCreate(final AbstractProjectWizard dialog, @Nullable Project projectToClose) throws IOException {
    final ProjectManagerEx projectManager = ProjectManagerEx.getInstanceEx();
    final String projectFilePath = dialog.getNewProjectFilePath();
    final ProjectBuilder projectBuilder = dialog.getProjectBuilder();

    try {
      File projectDir = new File(projectFilePath).getParentFile();
      LOG.assertTrue(projectDir != null, "Cannot create project in '" + projectFilePath + "': no parent file exists");
      FileUtil.ensureExists(projectDir);
      if (StorageScheme.DIRECTORY_BASED == dialog.getStorageScheme()) {
        final File ideaDir = new File(projectFilePath, Project.DIRECTORY_STORE_FOLDER);
        FileUtil.ensureExists(ideaDir);
      }

      final Project newProject;
      if (projectBuilder == null || !projectBuilder.isUpdate()) {
        String name = dialog.getProjectName();
        newProject = projectBuilder == null
                   ? projectManager.newProject(name, projectFilePath, true, false)
                   : projectBuilder.createProject(name, projectFilePath);
      }
      else {
        newProject = projectToClose;
      }

      if (newProject == null) return projectToClose;

      final Sdk jdk = dialog.getNewProjectJdk();
      if (jdk != null) {
        CommandProcessor.getInstance().executeCommand(newProject, new Runnable() {
          public void run() {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              public void run() {
                applyJdkToProject(newProject, jdk);
              }
            });
          }
        }, null, null);
      }

      final String compileOutput = dialog.getNewCompileOutput();
      CommandProcessor.getInstance().executeCommand(newProject, new Runnable() {
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              String canonicalPath = compileOutput;
              try {
                canonicalPath = FileUtil.resolveShortWindowsName(compileOutput);
              }
              catch (IOException e) {
                //file doesn't exist
              }
              canonicalPath = FileUtil.toSystemIndependentName(canonicalPath);
              CompilerProjectExtension.getInstance(newProject).setCompilerOutputUrl(VfsUtilCore.pathToUrl(canonicalPath));
            }
          });
        }
      }, null, null);

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

      final boolean need2OpenProjectStructure = projectBuilder == null || projectBuilder.isOpenProjectSettingsAfter();
      StartupManager.getInstance(newProject).registerPostStartupActivity(new Runnable() {
        public void run() {
          // ensure the dialog is shown after all startup activities are done
          //noinspection SSBasedInspection
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              if (newProject.isDisposed() || ApplicationManager.getApplication().isUnitTestMode()) return;
              if (need2OpenProjectStructure) {
                ModulesConfigurator.showDialog(newProject, null, null);
              }
              ApplicationManager.getApplication().invokeLater(new Runnable() {
                public void run() {
                  if (newProject.isDisposed()) return;
                  final ToolWindow toolWindow = ToolWindowManager.getInstance(newProject).getToolWindow(ToolWindowId.PROJECT_VIEW);
                  if (toolWindow != null) {
                    toolWindow.activate(null);
                  }
                }
              }, ModalityState.NON_MODAL);
            }
          });
        }
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

  public static void closePreviousProject(final Project projectToClose) {
    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    if (openProjects.length > 0) {
      int exitCode = ProjectUtil.confirmOpenNewProject(true);
      if (exitCode == GeneralSettings.OPEN_PROJECT_SAME_WINDOW) {
        ProjectUtil.closeAndDispose(projectToClose != null ? projectToClose : openProjects[openProjects.length - 1]);
      }
    }
  }

  private final static Logger LOG = Logger.getInstance(NewProjectUtil.class);
}
