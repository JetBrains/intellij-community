/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.ide.util.newProjectWizard.AddModuleWizard;
import com.intellij.ide.util.projectWizard.ProjectBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.StorageScheme;
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
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.ui.mac.MacMainFrameDecorator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;

public class NewProjectUtil {
  private NewProjectUtil() {
  }

  public static void createNewProject(Project projectToClose, @Nullable final String defaultPath) {
    final boolean proceed = ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        ProjectManager.getInstance().getDefaultProject(); //warm up components
      }
    }, ProjectBundle.message("project.new.wizard.progress.title"), true, null);
    if (!proceed) return;
    final AddModuleWizard dialog = new AddModuleWizard(null, ModulesProvider.EMPTY_MODULES_PROVIDER, defaultPath);
    dialog.show();
    if (!dialog.isOK()) {
      return;
    }

    final ProjectManagerEx projectManager = ProjectManagerEx.getInstanceEx();
    final String projectFilePath = dialog.getNewProjectFilePath();
    final ProjectBuilder projectBuilder = dialog.getProjectBuilder();

    try {
      if (StorageScheme.DIRECTORY_BASED == dialog.getStorageScheme()) {
        final File ideaDir = new File(projectFilePath, Project.DIRECTORY_STORE_FOLDER);
        if (!ideaDir.exists() && !ideaDir.mkdirs()) {
          SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
              Messages.showErrorDialog("Unable to create '.idea' directory at: " + projectFilePath, "Project Initialization Failed");
            }
          });

          return;
        }
      }

      final Project newProject;
      if (projectBuilder == null || !projectBuilder.isUpdate()) {
        newProject = ProgressManager.getInstance().runProcessWithProgressSynchronously(new ThrowableComputable<Project, RuntimeException>() {
          @Override
          public Project compute() throws RuntimeException {
            return projectManager.newProject(dialog.getProjectName(), projectFilePath, true, false);
          }
        }, "Creating Project", true, null);
      }
      else {
        newProject = projectToClose;
      }

      if (newProject == null) return;

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
              CompilerProjectExtension.getInstance(newProject).setCompilerOutputUrl(VfsUtil.pathToUrl(canonicalPath));
            }
          });
        }
      }, null, null);

      newProject.save();


      if (projectBuilder != null && !projectBuilder.validate(projectToClose, newProject)) {
        return;
      }

      if (newProject != projectToClose) {
        closePreviousProject(projectToClose);
      }

      if (projectBuilder != null) {
        projectBuilder.commit(newProject, null, ModulesProvider.EMPTY_MODULES_PROVIDER);
      }

      final boolean need2OpenProjectStructure = projectBuilder == null || projectBuilder.isOpenProjectSettingsAfter();
      StartupManager.getInstance(newProject).registerPostStartupActivity(new Runnable() {
        public void run() {
          // ensure the dialog is shown after all startup activities are done
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              if (newProject.isDisposed()) return;
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

        if (SystemInfo.isMacOSLion) {
          IdeFocusManager instance = IdeFocusManager.findInstance();
          IdeFrame lastFocusedFrame = instance.getLastFocusedFrame();
          if (lastFocusedFrame != null) {
            boolean fullScreen = WindowManagerEx.getInstanceEx().isFullScreen((Frame)lastFocusedFrame);
            if (fullScreen) {
              newProject.putUserData(MacMainFrameDecorator.SHOULD_OPEN_IN_FULLSCREEN, Boolean.TRUE);
            }
          }
        }
        
        projectManager.openProject(newProject);
      }
      newProject.save();
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
      LanguageLevel level = version.getMaxLanguageLevel();
      LanguageLevelProjectExtension ext = LanguageLevelProjectExtension.getInstance(project);
      if (level.compareTo(ext.getLanguageLevel()) < 0) {
        ext.setLanguageLevel(level);
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
}
