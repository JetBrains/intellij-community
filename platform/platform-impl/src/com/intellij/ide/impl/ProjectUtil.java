/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ide.impl;

import com.intellij.CommonBundle;
import com.intellij.ide.GeneralSettings;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ServiceKt;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.components.impl.stores.IComponentStore;
import com.intellij.openapi.components.impl.stores.IProjectStore;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.*;
import com.intellij.projectImport.ProjectOpenProcessor;
import com.intellij.ui.AppIcon;
import com.intellij.util.SystemProperties;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;

/**
 * @author Eugene Belyaev
 */
public class ProjectUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.impl.ProjectUtil");

  private ProjectUtil() { }

  public static void updateLastProjectLocation(final String projectFilePath) {
    File lastProjectLocation = new File(projectFilePath);
    if (lastProjectLocation.isFile()) {
      lastProjectLocation = lastProjectLocation.getParentFile(); // for directory-based project storage
    }
    if (lastProjectLocation == null) { // the immediate parent of the ipr file
      return;
    }
    lastProjectLocation = lastProjectLocation.getParentFile(); // the candidate directory to be saved
    if (lastProjectLocation == null) {
      return;
    }
    String path = lastProjectLocation.getPath();
    try {
      path = FileUtil.resolveShortWindowsName(path);
    }
    catch (IOException e) {
      LOG.info(e);
      return;
    }
    RecentProjectsManager.getInstance().setLastProjectCreationLocation(path.replace(File.separatorChar, '/'));
  }

  /**
   * @param project cannot be null
   */
  public static boolean closeAndDispose(@NotNull final Project project) {
    return ProjectManagerEx.getInstanceEx().closeAndDispose(project);
  }

  /**
   * @param path                project file path
   * @param projectToClose      currently active project
   * @param forceOpenInNewFrame forces opening in new frame
   * @return project by path if the path was recognized as IDEA project file or one of the project formats supported by
   *         installed importers (regardless of opening/import result)
   *         null otherwise
   */
  @Nullable
  public static Project openOrImport(@NotNull String path, Project projectToClose, boolean forceOpenInNewFrame) {
    VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
    if (virtualFile == null) return null;
    virtualFile.refresh(false, false);

    Project existing = findAndFocusExistingProjectForPath(path);
    if (existing != null) return existing;

    ProjectOpenProcessor strong = ProjectOpenProcessor.getStrongImportProvider(virtualFile);
    if (strong != null) {
      return strong.doOpenProject(virtualFile, projectToClose, forceOpenInNewFrame);
    }

    if (path.endsWith(ProjectFileType.DOT_DEFAULT_EXTENSION) ||
        virtualFile.isDirectory() && virtualFile.findChild(Project.DIRECTORY_STORE_FOLDER) != null) {
      return openProject(path, projectToClose, forceOpenInNewFrame);
    }

    if (virtualFile.isDirectory()) {
      for (VirtualFile child : virtualFile.getChildren()) {
        final String childPath = child.getPath();
        if (childPath.endsWith(ProjectFileType.DOT_DEFAULT_EXTENSION)) {
          return openProject(childPath, projectToClose, forceOpenInNewFrame);
        }
      }
    }

    ProjectOpenProcessor provider = ProjectOpenProcessor.getImportProvider(virtualFile);
    if (provider != null) {
      final Project project = provider.doOpenProject(virtualFile, projectToClose, forceOpenInNewFrame);

      if (project != null) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            if (!project.isDisposed()) {
              final ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.PROJECT_VIEW);
              if (toolWindow != null) {
                toolWindow.activate(null);
              }
            }
          }
        }, ModalityState.NON_MODAL);
      }

      return project;
    }

    return null;
  }

  @Nullable
  public static Project openProject(final String path, @Nullable Project projectToClose, boolean forceOpenInNewFrame) {
    File file = new File(path);
    if (!file.exists()) {
      Messages.showErrorDialog(IdeBundle.message("error.project.file.does.not.exist", path), CommonBundle.getErrorTitle());
      return null;
    }

    if (file.isDirectory() && !new File(file, Project.DIRECTORY_STORE_FOLDER).exists()) {
      String message = IdeBundle.message("error.project.file.does.not.exist", new File(file, Project.DIRECTORY_STORE_FOLDER).getPath());
      Messages.showErrorDialog(message, CommonBundle.getErrorTitle());
      return null;
    }

    Project existing = findAndFocusExistingProjectForPath(path);
    if (existing != null) return existing;

    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    if (!forceOpenInNewFrame && openProjects.length > 0) {
      int exitCode = confirmOpenNewProject(false);
      if (exitCode == GeneralSettings.OPEN_PROJECT_SAME_WINDOW) {
        final Project toClose = projectToClose != null ? projectToClose : openProjects[openProjects.length - 1];
        if (!closeAndDispose(toClose)) return null;
      }
      else if (exitCode != GeneralSettings.OPEN_PROJECT_NEW_WINDOW) {
        return null;
      }
    }

    ProjectManagerEx projectManager = ProjectManagerEx.getInstanceEx();
    Project project = null;
    try {
      project = projectManager.loadAndOpenProject(path);
    }
    catch (IOException e) {
      Messages.showMessageDialog(IdeBundle.message("error.cannot.load.project", e.getMessage()),
                                 IdeBundle.message("title.cannot.load.project"), Messages.getErrorIcon());
    }
    catch (JDOMException e) {
      LOG.info(e);
      Messages.showMessageDialog(IdeBundle.message("error.project.file.is.corrupted"), IdeBundle.message("title.cannot.load.project"),
                                 Messages.getErrorIcon());
    }
    catch (InvalidDataException e) {
      LOG.info(e);
      Messages.showMessageDialog(IdeBundle.message("error.project.file.is.corrupted"), IdeBundle.message("title.cannot.load.project"),
                                 Messages.getErrorIcon());
    }
    return project;
  }

  @Nullable
  private static Project findAndFocusExistingProjectForPath(String path) {
    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    for (Project project : openProjects) {
      if (!project.isDefault() && isSameProject(path, project)) {
        focusProjectWindow(project, false);
        return project;
      }
    }
    return null;
  }

  /**
   * @return {@link com.intellij.ide.GeneralSettings#OPEN_PROJECT_SAME_WINDOW}
   *         {@link com.intellij.ide.GeneralSettings#OPEN_PROJECT_NEW_WINDOW}
   *         {@link com.intellij.openapi.ui.Messages#CANCEL} - if user canceled the dialog
   * @param isNewProject
   */
  public static int confirmOpenNewProject(boolean isNewProject) {
    final GeneralSettings settings = GeneralSettings.getInstance();
    int confirmOpenNewProject = ApplicationManager.getApplication().isUnitTestMode() ? GeneralSettings.OPEN_PROJECT_NEW_WINDOW : settings.getConfirmOpenNewProject();
    if (confirmOpenNewProject == GeneralSettings.OPEN_PROJECT_ASK) {
      if (isNewProject) {
        int exitCode = Messages.showYesNoDialog(IdeBundle.message("prompt.open.project.in.new.frame"),
                                                IdeBundle.message("title.new.project"),
                                                IdeBundle.message("button.existingframe"),
                                                IdeBundle.message("button.newframe"),
                                                Messages.getQuestionIcon(),
                                                new ProjectNewWindowDoNotAskOption());
        return exitCode == Messages.YES ? GeneralSettings.OPEN_PROJECT_SAME_WINDOW : GeneralSettings.OPEN_PROJECT_NEW_WINDOW;
      }
      else {
        int exitCode = Messages.showYesNoCancelDialog(IdeBundle.message("prompt.open.project.in.new.frame"),
                                                      IdeBundle.message("title.open.project"),
                                                      IdeBundle.message("button.existingframe"),
                                                      IdeBundle.message("button.newframe"),
                                                      CommonBundle.getCancelButtonText(),
                                                      Messages.getQuestionIcon(),
                                                      new ProjectNewWindowDoNotAskOption());
        return exitCode == Messages.YES ? GeneralSettings.OPEN_PROJECT_SAME_WINDOW :
               exitCode == Messages.NO ? GeneralSettings.OPEN_PROJECT_NEW_WINDOW : Messages.CANCEL;
      }
    }
    return confirmOpenNewProject;
  }

  public static boolean isSameProject(String path, @NotNull Project project) {
    IProjectStore projectStore = (IProjectStore)ServiceKt.getStateStore(project);

    String toOpen = FileUtil.toSystemIndependentName(path);
    String existing = projectStore.getProjectFilePath();

    String existingBaseDir = projectStore.getProjectBasePath();
    if (existingBaseDir == null) {
      // could be null if not yet initialized
      return false;
    }

    final File openFile = new File(toOpen);
    if (openFile.isDirectory()) {
      return FileUtil.pathsEqual(toOpen, existingBaseDir);
    }
    if (StorageScheme.DIRECTORY_BASED == projectStore.getStorageScheme()) {
      // todo: check if IPR is located not under the project base dir
      return FileUtil.pathsEqual(FileUtil.toSystemIndependentName(openFile.getParentFile().getPath()), existingBaseDir);
    }

    return FileUtil.pathsEqual(toOpen, existing);
  }

  public static void focusProjectWindow(final Project p, boolean executeIfAppInactive) {
    FocusCommand cmd = new FocusCommand() {
      @NotNull
      @Override
      public ActionCallback run() {
        JFrame f = WindowManager.getInstance().getFrame(p);
        if (f != null) {
          f.toFront();
          //f.requestFocus();
        }
        return ActionCallback.DONE;
      }
    };

    if (executeIfAppInactive) {
      AppIcon.getInstance().requestFocus((IdeFrame)WindowManager.getInstance().getFrame(p));
      cmd.run();
    } else {
      IdeFocusManager.getInstance(p).requestFocus(cmd, true);
    }
  }

  public static String getBaseDir() {
    final String lastProjectLocation = RecentProjectsManager.getInstance().getLastProjectCreationLocation();
    if (lastProjectLocation != null) {
      return lastProjectLocation.replace('/', File.separatorChar);
    }
    final String userHome = SystemProperties.getUserHome();
    //noinspection HardCodedStringLiteral
    return userHome.replace('/', File.separatorChar) + File.separator + ApplicationNamesInfo.getInstance().getLowercaseProductName() +
           "Projects";
  }

  public static boolean isDirectoryBased(@NotNull Project project) {
    IComponentStore store = ServiceKt.getStateStore(project);
    return store instanceof IProjectStore && StorageScheme.DIRECTORY_BASED.equals(((IProjectStore)store).getStorageScheme());
  }
}
