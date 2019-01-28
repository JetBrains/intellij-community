// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.impl;

import com.intellij.CommonBundle;
import com.intellij.ide.GeneralSettings;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.components.impl.stores.IProjectStore;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.*;
import com.intellij.project.ProjectKt;
import com.intellij.projectImport.ProjectOpenProcessor;
import com.intellij.ui.AppIcon;
import com.intellij.util.PathUtil;
import com.intellij.util.PlatformUtils;
import com.intellij.util.SystemProperties;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;
import org.jetbrains.annotations.SystemIndependent;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;

/**
 * @author Eugene Belyaev
 */
public class ProjectUtil {
  private static final Logger LOG = Logger.getInstance(ProjectUtil.class);

  public static final String MODE_PROPERTY = "OpenOrAttachDialog.OpenMode";
  public static final String MODE_ATTACH = "attach";
  public static final String MODE_REPLACE = "replace";
  public static final String MODE_NEW = "new";

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
    RecentProjectsManager.getInstance().setLastProjectCreationLocation(PathUtil.toSystemIndependentName(path));
  }

  public static boolean closeAndDispose(@NotNull Project project) {
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
  public static Project openOrImport(@NotNull @SystemIndependent String path, Project projectToClose, boolean forceOpenInNewFrame) {
    Project existing = findAndFocusExistingProjectForPath(path);
    if (existing != null) {
      return existing;
    }

    VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
    if (virtualFile == null) {
      return null;
    }

    virtualFile.refresh(false, false);

    ProjectOpenProcessor strong = ProjectOpenProcessor.getStrongImportProvider(virtualFile);
    if (strong != null) {
      return strong.doOpenProject(virtualFile, projectToClose, forceOpenInNewFrame);
    }

    if (ProjectKt.isValidProjectPath(path)) {
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
        ApplicationManager.getApplication().invokeLater(() -> {
          if (!project.isDisposed()) {
            final ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.PROJECT_VIEW);
            if (toolWindow != null) {
              toolWindow.activate(null);
            }
          }
        }, ModalityState.NON_MODAL);
      }

      return project;
    }

    return null;
  }

  @Nullable
  public static Project openProject(@NotNull String path, @Nullable Project projectToClose, boolean forceOpenInNewFrame) {
    File file = new File(path);
    if (!file.exists()) {
      Messages.showErrorDialog(IdeBundle.message("error.project.file.does.not.exist", path), CommonBundle.getErrorTitle());
      return null;
    }

    if (file.isDirectory()) {
      File dir = new File(file, Project.DIRECTORY_STORE_FOLDER);
      if (!dir.exists()) {
        String message = IdeBundle.message("error.project.file.does.not.exist", dir.getPath());
        Messages.showErrorDialog(message, CommonBundle.getErrorTitle());
        return null;
      }
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

    if (isRemotePath(path) && !RecentProjectsManager.getInstance().hasPath(PathUtil.toSystemIndependentName(path))) {
      if (!confirmLoadingFromRemotePath(path, "warning.load.project.from.share", "title.load.project.from.share")) {
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
    catch (JDOMException | InvalidDataException e) {
      LOG.info(e);
      Messages.showMessageDialog(IdeBundle.message("error.project.file.is.corrupted"), IdeBundle.message("title.cannot.load.project"),
                                 Messages.getErrorIcon());
    }
    return project;
  }

  public static boolean confirmLoadingFromRemotePath(@NotNull String path,
                                                     @NotNull @PropertyKey(resourceBundle = IdeBundle.BUNDLE) String msgKey,
                                                     @NotNull @PropertyKey(resourceBundle = IdeBundle.BUNDLE) String titleKey) {
    return showYesNoDialog(IdeBundle.message(msgKey, path), titleKey);
  }

  public static boolean showYesNoDialog(@NotNull String message, @NotNull @PropertyKey(resourceBundle = IdeBundle.BUNDLE) String titleKey) {
    final Window window = getActiveFrameOrWelcomeScreen();
    final Icon icon = Messages.getWarningIcon();
    String title = IdeBundle.message(titleKey);
    final int answer = window == null ? Messages.showYesNoDialog(message, title, icon) : Messages.showYesNoDialog(window, message, title, icon);
    return answer == Messages.YES;
  }

  public static Window getActiveFrameOrWelcomeScreen() {
    Window window = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
    if (window != null)  return window;

    for (Frame frame : Frame.getFrames()) {
      if (frame instanceof IdeFrame && frame.isVisible()) {
        return frame;
      }
    }

    return null;
  }

  public static boolean isRemotePath(@NotNull String path) {
    return path.contains("://") || path.contains("\\\\");
  }

  @Nullable
  public static Project findAndFocusExistingProjectForPath(@NotNull String path) {
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
   * @return {@link GeneralSettings#OPEN_PROJECT_SAME_WINDOW}
   *         {@link GeneralSettings#OPEN_PROJECT_NEW_WINDOW}
   *         {@link Messages#CANCEL} - if user canceled the dialog
   * @param isNewProject
   */
  public static int confirmOpenNewProject(boolean isNewProject) {
    final GeneralSettings settings = GeneralSettings.getInstance();
    int confirmOpenNewProject = ApplicationManager.getApplication().isUnitTestMode() ? GeneralSettings.OPEN_PROJECT_NEW_WINDOW : settings.getConfirmOpenNewProject();
    if (confirmOpenNewProject == GeneralSettings.OPEN_PROJECT_ASK) {
      if (isNewProject) {
        int exitCode = Messages.showYesNoDialog(IdeBundle.message("prompt.open.project.in.new.frame"),
                                                IdeBundle.message("title.new.project"),
                                                IdeBundle.message("button.existing.frame"),
                                                IdeBundle.message("button.new.frame"),
                                                Messages.getQuestionIcon(),
                                                new ProjectNewWindowDoNotAskOption());
        return exitCode == Messages.YES ? GeneralSettings.OPEN_PROJECT_SAME_WINDOW : GeneralSettings.OPEN_PROJECT_NEW_WINDOW;
      }
      else {
        int exitCode = Messages.showYesNoCancelDialog(IdeBundle.message("prompt.open.project.in.new.frame"),
                                                      IdeBundle.message("title.open.project"),
                                                      IdeBundle.message("button.existing.frame"),
                                                      IdeBundle.message("button.new.frame"),
                                                      CommonBundle.getCancelButtonText(),
                                                      Messages.getQuestionIcon(),
                                                      new ProjectNewWindowDoNotAskOption());
        return exitCode == Messages.YES ? GeneralSettings.OPEN_PROJECT_SAME_WINDOW :
               exitCode == Messages.NO ? GeneralSettings.OPEN_PROJECT_NEW_WINDOW : Messages.CANCEL;
      }
    }
    return confirmOpenNewProject;
  }

  /**
   * @return  0 == GeneralSettings.OPEN_PROJECT_NEW_WINDOW
   *          1 == GeneralSettings.OPEN_PROJECT_SAME_WINDOW
   *          2 == GeneralSettings.OPEN_PROJECT_SAME_WINDOW_ATTACH
   *         -1 == CANCEL
   */
  public static int confirmOpenOrAttachProject() {
    final String mode = PropertiesComponent.getInstance().getValue(MODE_PROPERTY);
    int exitCode = Messages.showDialog(IdeBundle.message("prompt.open.project.or.attach"), "Open Project",
                                       new String[]{"&This Window", "New &Window", "&Attach", CommonBundle.getCancelButtonText()},
                                       MODE_NEW.equals(mode) ? 1 : MODE_REPLACE.equals(mode) ? 0 : MODE_ATTACH.equals(mode) ? 2 : 0,
                                       Messages.getQuestionIcon());

    return  exitCode == 0 ? GeneralSettings.OPEN_PROJECT_SAME_WINDOW : exitCode == 1 ? GeneralSettings.OPEN_PROJECT_NEW_WINDOW: exitCode == 2? GeneralSettings.OPEN_PROJECT_SAME_WINDOW_ATTACH : -1;
  }

  public static boolean isSameProject(@Nullable String projectFilePath, @NotNull Project project) {
    if (projectFilePath == null) return false;

    IProjectStore projectStore = ProjectKt.getStateStore(project);
    String existingBaseDirPath = projectStore.getProjectBasePath();
    File projectFile = new File(projectFilePath);
    if (projectFile.isDirectory()) {
      return FileUtil.pathsEqual(projectFilePath, existingBaseDirPath);
    }

    if (projectStore.getStorageScheme() == StorageScheme.DEFAULT) {
      return FileUtil.pathsEqual(projectFilePath, projectStore.getProjectFilePath());
    }

    File parent = projectFile.getParentFile();
    if (parent.getName().equals(Project.DIRECTORY_STORE_FOLDER)) {
      parent = parent.getParentFile();
      return parent != null && FileUtil.pathsEqual(parent.getPath(), existingBaseDirPath);
    }
    return FileUtil.pathsEqual(parent.getPath(), existingBaseDirPath) &&
           ProjectFileType.DEFAULT_EXTENSION.equals(FileUtilRt.getExtension(projectFile.getName()));
  }

  public static void focusProjectWindow(final Project p, boolean executeIfAppInactive) {

    JFrame f = WindowManager.getInstance().getFrame(p);

    if (f != null) {
      if (executeIfAppInactive) {
        AppIcon.getInstance().requestFocus((IdeFrame)WindowManager.getInstance().getFrame(p));
        f.toFront();
      } else {
        IdeFocusManager.getInstance(p).requestFocus(f, true);
      }

    }
  }

  public static String getBaseDir() {
    String defaultDirectory = GeneralSettings.getInstance().getDefaultProjectDirectory();
    if (StringUtil.isNotEmpty(defaultDirectory)) {
      return defaultDirectory.replace('/', File.separatorChar);
    }
    final String lastProjectLocation = RecentProjectsManager.getInstance().getLastProjectCreationLocation();
    if (lastProjectLocation != null) {
      return lastProjectLocation.replace('/', File.separatorChar);
    }
    final String userHome = SystemProperties.getUserHome();
    String productName = ApplicationNamesInfo.getInstance().getLowercaseProductName();
    if (PlatformUtils.isCLion()) {
      productName = ApplicationNamesInfo.getInstance().getProductName();
    }
    //noinspection HardCodedStringLiteral
    return userHome.replace('/', File.separatorChar) + File.separator + productName +
           "Projects";
  }
}