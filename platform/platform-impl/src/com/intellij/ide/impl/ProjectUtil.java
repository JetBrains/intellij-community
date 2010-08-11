/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.ide.highlighter.ModuleFileType;
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.ide.highlighter.WorkspaceFileType;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.components.impl.stores.IProjectStore;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.FocusCommand;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.projectImport.ProjectOpenProcessor;
import com.intellij.ui.AppIcon;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NonNls;
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
  @NonNls public static final String DIRECTORY_BASED_PROJECT_DIR = ".idea";

  private ProjectUtil() {
  }

  public static void updateLastProjectLocation(final String projectFilePath) {
    File lastProjectLocation = new File(projectFilePath);
    if (lastProjectLocation.isFile()) {
      lastProjectLocation = lastProjectLocation.getParentFile(); //for directory based project storages
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
    GeneralSettings.getInstance().setLastProjectLocation(path.replace(File.separatorChar, '/'));
  }

  /**
   * @param project cannot be null
   */
  public static boolean closeProject(@NotNull Project project) {
    if (!ProjectManagerEx.getInstanceEx().closeProject(project)) return false;
    Disposer.dispose(project);
    return true;
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
  public static Project openOrImport(@NotNull final String path, final Project projectToClose, boolean forceOpenInNewFrame) {
    final VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(path);

    if (virtualFile == null) return null;

    if (path.endsWith(ProjectFileType.DOT_DEFAULT_EXTENSION) ||
        virtualFile.isDirectory() && virtualFile.findChild(DIRECTORY_BASED_PROJECT_DIR) != null) {
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
      return provider.doOpenProject(virtualFile, projectToClose, forceOpenInNewFrame);
    }
    return null;
  }

  @Nullable
  public static Project openProject(final String path, Project projectToClose, boolean forceOpenInNewFrame) {
    File file = new File(path);
    if (!file.exists()) {
      Messages.showMessageDialog(IdeBundle.message("error.project.file.does.not.exist", path), CommonBundle.getErrorTitle(),
                                 Messages.getErrorIcon());
      return null;
    }

    if (file.isDirectory() && !new File(file, DIRECTORY_BASED_PROJECT_DIR).exists()) {
      Messages.showMessageDialog(IdeBundle.message("error.project.file.does.not.exist", new File(file, DIRECTORY_BASED_PROJECT_DIR).getPath()), CommonBundle.getErrorTitle(),
                                 Messages.getErrorIcon());
      return null;
    }

    Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
    for (Project project : openProjects) {
      if (isSameProject(path, project)) {
        focusProjectWindow(project, false);
        return project;
      }
    }

    if (!forceOpenInNewFrame && openProjects.length > 0) {
      final GeneralSettings settings = GeneralSettings.getInstance();
      int exitCode;
      if (settings.getConfirmOpenNewProject() < 0) {
        exitCode = Messages.showDialog(IdeBundle.message("prompt.open.project.in.new.frame"), IdeBundle.message("title.open.project"),
                                           new String[]{IdeBundle.message("button.newframe"), IdeBundle.message("button.existingframe"),
                                             CommonBundle.getCancelButtonText()}, 1, 0, Messages.getQuestionIcon(), new DialogWrapper.DoNotAskOption() {
            public boolean isToBeShown() {
              return true;
            }

            public void setToBeShown(boolean value, int exitCode) {
              settings.setConfirmOpenNewProject(value || exitCode == 2 ? -1 : exitCode);
            }

            public boolean canBeHidden() {
              return true;
            }

            public boolean shouldSaveOptionsOnCancel() {
              return false;
            }

            public String getDoNotShowMessage() {
              return CommonBundle.message("dialog.options.do.not.ask");
            }
          });
      } else {
        exitCode = settings.getConfirmOpenNewProject();
      }
      if (exitCode == 1) { // "No" option
        if (!closeProject(projectToClose != null ? projectToClose : openProjects[openProjects.length - 1])) return null;
      }
      else if (exitCode != 0) { // not "Yes"
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

  private static boolean isSameProject(String path, Project p) {
    final IProjectStore projectStore = ((ProjectEx)p).getStateStore();

    String toOpen = FileUtil.toSystemIndependentName(path);
    String existing = FileUtil.toSystemIndependentName(projectStore.getProjectFilePath());

    final VirtualFile existingBaseDir = projectStore.getProjectBaseDir();
    if (existingBaseDir == null) return false; // could be null if not yet initialized

    final File openFile = new File(toOpen);
    if (openFile.isDirectory()) {
      return FileUtil.pathsEqual(toOpen, existingBaseDir.getPath());
    } else {
      if (StorageScheme.DIRECTORY_BASED == projectStore.getStorageScheme()) {
        // todo: check if IPR is located not under the project base dir
        return FileUtil.pathsEqual(FileUtil.toSystemIndependentName(openFile.getParentFile().getPath()), existingBaseDir.getPath());
      }
    }

    return FileUtil.pathsEqual(toOpen, existing);
  }

  public static void focusProjectWindow(final Project p, boolean executeIfAppInactive) {
    FocusCommand cmd = new FocusCommand() {
      @Override
      public ActionCallback run() {
        JFrame f = WindowManager.getInstance().getFrame(p);
        if (f != null) {
          f.toFront();
          //f.requestFocus();
        }
        return new ActionCallback.Done();
      }
    };

    if (executeIfAppInactive) {
      AppIcon.getInstance().requestFocus((IdeFrame)WindowManager.getInstance().getFrame(p));
      cmd.run();
    } else {
      IdeFocusManager.getInstance(p).requestFocus(cmd, false);
    }
  }

  public static boolean isProjectOrWorkspaceFile(final VirtualFile file) {
    return isProjectOrWorkspaceFile(file, file.getFileType());
  }

  public static boolean isProjectOrWorkspaceFile(final VirtualFile file,
                                                 final FileType fileType) {
    final boolean iprBased = fileType instanceof WorkspaceFileType || fileType instanceof ProjectFileType || fileType instanceof ModuleFileType;
    if (iprBased) return true;
    final VirtualFile parent = file.getParent();
    return parent != null && parent.getName().equals(DIRECTORY_BASED_PROJECT_DIR);
  }
}
