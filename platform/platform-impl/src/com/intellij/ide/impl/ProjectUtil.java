// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.impl;

import com.intellij.CommonBundle;
import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector;
import com.intellij.ide.GeneralSettings;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.ide.actions.OpenFileAction;
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
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.*;
import com.intellij.platform.CommandLineProjectOpenProcessor;
import com.intellij.platform.PlatformProjectOpenProcessor;
import com.intellij.project.ProjectKt;
import com.intellij.projectImport.ProjectOpenProcessor;
import com.intellij.ui.AppIcon;
import com.intellij.ui.GuiUtils;
import com.intellij.util.PathUtil;
import com.intellij.util.PlatformUtils;
import com.intellij.util.SmartList;
import com.intellij.util.SystemProperties;
import com.intellij.util.io.PathKt;
import com.intellij.util.ui.FocusUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.List;

public final class ProjectUtil {
  private static final Logger LOG = Logger.getInstance(ProjectUtil.class);

  private static final String MODE_PROPERTY = "OpenOrAttachDialog.OpenMode";
  private static final String MODE_ATTACH = "attach";
  private static final String MODE_REPLACE = "replace";
  private static final String MODE_NEW = "new";

  private ProjectUtil() { }

  /**
   * @deprecated Use {@link #updateLastProjectLocation(Path)}
   */
  @Deprecated
  public static void updateLastProjectLocation(@NotNull String projectFilePath) {
    updateLastProjectLocation(Paths.get(projectFilePath));
  }

  public static void updateLastProjectLocation(@NotNull Path lastProjectLocation) {
    if (Files.isRegularFile(lastProjectLocation)) {
      // for directory-based project storage
      lastProjectLocation = lastProjectLocation.getParent();
    }

    if (lastProjectLocation == null) {
      // the immediate parent of the ipr file
      return;
    }

    // the candidate directory to be saved
    lastProjectLocation = lastProjectLocation.getParent();
    if (lastProjectLocation == null) {
      return;
    }

    String path = lastProjectLocation.toString();
    try {
      path = FileUtil.resolveShortWindowsName(path);
    }
    catch (IOException e) {
      LOG.info(e);
      return;
    }
    RecentProjectsManager.getInstance().setLastProjectCreationLocation(PathUtil.toSystemIndependentName(path));
  }

  /**
   * @deprecated Use {@link ProjectManagerEx#closeAndDispose(Project)}
   */
  @Deprecated
  public static boolean closeAndDispose(@NotNull Project project) {
    return ProjectManagerEx.getInstanceEx().closeAndDispose(project);
  }

  public static Project openOrImport(@NotNull Path path, Project projectToClose, boolean forceOpenInNewFrame) {
    return openOrImport(path, OpenProjectTask.withProjectToClose(projectToClose, forceOpenInNewFrame));
  }

  public static Project openOrImport(@NotNull Path path) {
    return openOrImport(path, new OpenProjectTask());
  }

  /**
   * @param path                project file path
   * @param projectToClose      currently active project
   * @param forceOpenInNewFrame forces opening in new frame
   * @return project by path if the path was recognized as IDEA project file or one of the project formats supported by
   * installed importers (regardless of opening/import result)
   * null otherwise
   */
  public static @Nullable Project openOrImport(@NotNull String path, @Nullable Project projectToClose, boolean forceOpenInNewFrame) {
    return openOrImport(Paths.get(path), OpenProjectTask.withProjectToClose(projectToClose, forceOpenInNewFrame));
  }

  public static @Nullable Project openOrImport(@NotNull Path file, @NotNull OpenProjectTask options) {
    if (!options.getForceOpenInNewFrame()) {
      Project existing = findAndFocusExistingProjectForPath(file);
      if (existing != null) {
        return existing;
      }
    }

    NullableLazyValue<VirtualFile> lazyVirtualFile = NullableLazyValue.createValue(() -> getFileAndRefresh(file));

    for (ProjectOpenProcessor provider : ProjectOpenProcessor.EXTENSION_POINT_NAME.getIterable()) {
      if (!provider.isStrongProjectInfoHolder()) {
        continue;
      }

      // PlatformProjectOpenProcessor is not a strong project info holder, so, no need implement optimized case for PlatformProjectOpenProcessor  (VFS not required)
      VirtualFile virtualFile = lazyVirtualFile.getValue();
      if (virtualFile == null) {
        return null;
      }

      if (provider.canOpenProject(virtualFile)) {
        return chooseProcessorAndOpen(Collections.singletonList(provider), virtualFile, options);
      }
    }

    if (isValidProjectPath(file)) {
      // see OpenProjectTest.`open valid existing project dir with inability to attach using OpenFileAction` test about why `runConfigurators = true` is specified here
      return ProjectManagerEx.getInstanceEx().openProject(file, options.withRunConfigurators());
    }

    if (options.checkDirectoryForFileBasedProjects && Files.isDirectory(file)) {
      try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(file)) {
        for (Path child : directoryStream) {
          String childPath = child.toString();
          if (childPath.endsWith(ProjectFileType.DOT_DEFAULT_EXTENSION)) {
            return openProject(Paths.get(childPath), options);
          }
        }
      }
      catch (IOException ignore) {
      }
    }

    List<ProjectOpenProcessor> processors = new SmartList<>();
    ProjectOpenProcessor.EXTENSION_POINT_NAME.forEachExtensionSafe(processor -> {
      if (processor instanceof PlatformProjectOpenProcessor) {
        if (Files.isDirectory(file)) {
          processors.add(processor);
        }
      }
      else {
        VirtualFile virtualFile = lazyVirtualFile.getValue();
        if (virtualFile != null && processor.canOpenProject(virtualFile)) {
          processors.add(processor);
        }
      }
    });

    if (processors.isEmpty()) {
      return null;
    }

    Project project;
    if (processors.size() == 1 && processors.get(0) instanceof PlatformProjectOpenProcessor) {
      project = ProjectManagerEx.getInstanceEx().openProject(file, options.asNewProjectAndRunConfigurators().withBeforeOpenCallback(p -> {
        p.putUserData(PlatformProjectOpenProcessor.PROJECT_OPENED_BY_PLATFORM_PROCESSOR, Boolean.TRUE);
        return true;
      }));
    }
    else {
      VirtualFile virtualFile = lazyVirtualFile.getValue();
      if (virtualFile == null) {
        return null;
      }

      project = chooseProcessorAndOpen(processors, virtualFile, options);
    }

    if (project == null) {
      return null;
    }

    StartupManager.getInstance(project).runAfterOpened(() -> {
      GuiUtils.invokeLaterIfNeeded(() -> {
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.PROJECT_VIEW);
        if (toolWindow != null) {
          toolWindow.activate(null);
        }
      }, ModalityState.NON_MODAL, project.getDisposed());
    });
    return project;
  }

  private static @Nullable Project chooseProcessorAndOpen(@NotNull List<ProjectOpenProcessor> processors,
                                                          @NotNull VirtualFile virtualFile,
                                                          @NotNull OpenProjectTask options) {
    ProjectOpenProcessor processor;
    if (processors.size() == 1) {
      processor = processors.get(0);
    }
    else {
      processors.removeIf(it -> it instanceof PlatformProjectOpenProcessor);
      if (processors.size() == 1) {
        processor = processors.get(0);
      }
      else {
        Ref<ProjectOpenProcessor> ref = new Ref<>();
        ApplicationManager.getApplication().invokeAndWait(() -> {
          ref.set(new SelectProjectOpenProcessorDialog(processors, virtualFile).showAndGetChoice());
        });
        processor = ref.get();
        if (processor == null) {
          return null;
        }
      }
    }

    Ref<Project> result = new Ref<>();
    ApplicationManager.getApplication().invokeAndWait(() -> {
      result.set(processor.doOpenProject(virtualFile, options.getProjectToClose(), options.getForceOpenInNewFrame()));
    });
    return result.get();
  }

  @ApiStatus.Internal
  public static @Nullable VirtualFile getFileAndRefresh(@NotNull Path file) {
    VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(FileUtil.toSystemIndependentName(file.toString()));
    if (virtualFile == null || !virtualFile.isValid()) {
      return null;
    }

    virtualFile.refresh(false, false);
    return virtualFile;
  }

  public static @Nullable Project openProject(@NotNull String path, @Nullable Project projectToClose, boolean forceOpenInNewFrame) {
    return openProject(Paths.get(path), OpenProjectTask.withProjectToClose(projectToClose, forceOpenInNewFrame));
  }

  public static @Nullable Project openProject(@NotNull Path file, @NotNull OpenProjectTask options) {
    BasicFileAttributes fileAttributes = PathKt.basicAttributesIfExists(file);
    if (fileAttributes == null) {
      Messages.showErrorDialog(IdeBundle.message("error.project.file.does.not.exist", file.toString()), CommonBundle.getErrorTitle());
      return null;
    }

    Project existing = findAndFocusExistingProjectForPath(file);
    if (existing != null) {
      return existing;
    }

    if (isRemotePath(file.toString()) && !RecentProjectsManager.getInstance().hasPath(FileUtil.toSystemIndependentName(file.toString()))) {
      if (!confirmLoadingFromRemotePath(file.toString(), "warning.load.project.from.share", "title.load.project.from.share")) {
        return null;
      }
    }

    if (fileAttributes.isDirectory()) {
      Path dir = file.resolve(Project.DIRECTORY_STORE_FOLDER);
      if (!Files.isDirectory(dir)) {
        Messages.showErrorDialog(IdeBundle.message("error.project.file.does.not.exist", dir.toString()), CommonBundle.getErrorTitle());
        return null;
      }
    }

    try {
      return ProjectManagerEx.getInstanceEx().openProject(file, options);
    }
    catch (Exception e) {
      Messages.showMessageDialog(IdeBundle.message("error.cannot.load.project", e.getMessage()),
                                 IdeBundle.message("title.cannot.load.project"), Messages.getErrorIcon());
    }
    return null;
  }

  public static boolean confirmLoadingFromRemotePath(@NotNull String path,
                                                     @NotNull @PropertyKey(resourceBundle = IdeBundle.BUNDLE) String msgKey,
                                                     @NotNull @PropertyKey(resourceBundle = IdeBundle.BUNDLE) String titleKey) {
    return showYesNoDialog(IdeBundle.message(msgKey, path), titleKey);
  }

  public static boolean showYesNoDialog(@NotNull String message, @NotNull @PropertyKey(resourceBundle = IdeBundle.BUNDLE) String titleKey) {
    Window window = getActiveFrameOrWelcomeScreen();
    Icon icon = Messages.getWarningIcon();
    String title = IdeBundle.message(titleKey);
    return (window == null ? Messages.showYesNoDialog(message, title, icon) : Messages.showYesNoDialog(window, message, title, icon)) == Messages.YES;
  }

  public static Window getActiveFrameOrWelcomeScreen() {
    Window window = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
    if (window != null) {
      return window;
    }

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

  public static @NotNull Project @NotNull [] getOpenProjects() {
    ProjectManager projectManager = ProjectManager.getInstanceIfCreated();
    return projectManager == null ? new Project[0] : projectManager.getOpenProjects();
  }

  public static @Nullable Project findAndFocusExistingProjectForPath(@NotNull Path file) {
    Project[] openProjects = getOpenProjects();
    if (openProjects.length == 0) {
      return null;
    }

    String path = FileUtil.toSystemIndependentName(file.toString());
    for (Project project : openProjects) {
      if (isSameProject(path, project)) {
        focusProjectWindow(project, false);
        return project;
      }
    }
    return null;
  }

  /**
   * @return {@link GeneralSettings#OPEN_PROJECT_SAME_WINDOW}
   * {@link GeneralSettings#OPEN_PROJECT_NEW_WINDOW}
   * {@link Messages#CANCEL} - if user canceled the dialog
   */
  public static int confirmOpenNewProject(boolean isNewProject) {
    final GeneralSettings settings = GeneralSettings.getInstance();
    int confirmOpenNewProject =
      ApplicationManager.getApplication().isUnitTestMode() ? GeneralSettings.OPEN_PROJECT_NEW_WINDOW : settings.getConfirmOpenNewProject();
    if (confirmOpenNewProject == GeneralSettings.OPEN_PROJECT_ASK) {
      if (isNewProject) {
        int exitCode = Messages.showYesNoDialog(IdeBundle.message("prompt.open.project.in.new.frame"),
                                                IdeBundle.message("title.new.project"),
                                                IdeBundle.message("button.existing.frame"),
                                                IdeBundle.message("button.new.frame"),
                                                Messages.getQuestionIcon(),
                                                new ProjectNewWindowDoNotAskOption());
        LifecycleUsageTriggerCollector.onProjectFrameSelected(exitCode);
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
        LifecycleUsageTriggerCollector.onProjectFrameSelected(exitCode);
        return exitCode == Messages.YES ? GeneralSettings.OPEN_PROJECT_SAME_WINDOW :
               exitCode == Messages.NO ? GeneralSettings.OPEN_PROJECT_NEW_WINDOW : Messages.CANCEL;
      }
    }
    return confirmOpenNewProject;
  }

  /**
   * @return 0 == GeneralSettings.OPEN_PROJECT_NEW_WINDOW
   * 1 == GeneralSettings.OPEN_PROJECT_SAME_WINDOW
   * 2 == GeneralSettings.OPEN_PROJECT_SAME_WINDOW_ATTACH
   * -1 == CANCEL
   */
  public static int confirmOpenOrAttachProject() {
    final String mode = PropertiesComponent.getInstance().getValue(MODE_PROPERTY);
    int exitCode = Messages.showDialog(
      IdeBundle.message("prompt.open.project.or.attach"),
      IdeBundle.message("prompt.open.project.or.attach.title"),
      new String[]{
        IdeBundle.message("prompt.open.project.or.attach.button.this.window"),
        IdeBundle.message("prompt.open.project.or.attach.button.new.window"),
        IdeBundle.message("prompt.open.project.or.attach.button.attach"),
        CommonBundle.getCancelButtonText()
      },
      MODE_NEW.equals(mode) ? 1 : MODE_REPLACE.equals(mode) ? 0 : MODE_ATTACH.equals(mode) ? 2 : 0,
      Messages.getQuestionIcon());
    int returnValue = exitCode == 0 ? GeneralSettings.OPEN_PROJECT_SAME_WINDOW :
            exitCode == 1 ? GeneralSettings.OPEN_PROJECT_NEW_WINDOW :
            exitCode == 2 ? GeneralSettings.OPEN_PROJECT_SAME_WINDOW_ATTACH :
            -1;
    if (returnValue != -1) {
      LifecycleUsageTriggerCollector.onProjectFrameSelected(returnValue);
    }
    return returnValue;
  }

  public static boolean isSameProject(@Nullable String projectFilePath, @NotNull Project project) {
    if (projectFilePath == null) {
      return false;
    }

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
    if (parent == null) return false;
    if (parent.getName().equals(Project.DIRECTORY_STORE_FOLDER)) {
      parent = parent.getParentFile();
      return parent != null && FileUtil.pathsEqual(parent.getPath(), existingBaseDirPath);
    }

    return FileUtil.pathsEqual(parent.getPath(), existingBaseDirPath) &&
           ProjectFileType.DEFAULT_EXTENSION.equals(FileUtilRt.getExtension(projectFile.getName()));
  }

  public static void focusProjectWindow(@Nullable Project project, boolean executeIfAppInactive) {
    JFrame frame = WindowManager.getInstance().getFrame(project);
    if (frame == null) {
      return;
    }

    Component mostRecentFocusOwner = frame.getMostRecentFocusOwner();
    if (executeIfAppInactive) {
      AppIcon.getInstance().requestFocus((IdeFrame)WindowManager.getInstance().getFrame(project));
      frame.toFront();
      if (!SystemInfo.isMac && !frame.isAutoRequestFocus()) {
        if (mostRecentFocusOwner != null) {
          IdeFocusManager.getInstance(project).requestFocus(mostRecentFocusOwner, true);
        }
        else {
          LOG.warn("frame.getMostRecentFocusOwner() is null");
        }
      }
    }
    else {
      if (mostRecentFocusOwner != null) {
        IdeFocusManager.getInstance(project).requestFocusInProject(mostRecentFocusOwner, project);
      }
      else {
        Component defaultFocusComponentInPanel = FocusUtil.getDefaultComponentInPanel(frame.getFocusCycleRootAncestor());
        if (defaultFocusComponentInPanel != null) {
          IdeFocusManager.getInstance(project).requestFocusInProject(defaultFocusComponentInPanel, project);
        }
      }
    }
  }

  public static String getBaseDir() {
    String defaultDirectory = GeneralSettings.getInstance().getDefaultProjectDirectory();
    if (Strings.isNotEmpty(defaultDirectory)) {
      return defaultDirectory.replace('/', File.separatorChar);
    }
    final String lastProjectLocation = RecentProjectsManager.getInstance().getLastProjectCreationLocation();
    if (lastProjectLocation != null) {
      return lastProjectLocation.replace('/', File.separatorChar);
    }
    final String userHome = SystemProperties.getUserHome();
    String productName = ApplicationNamesInfo.getInstance().getLowercaseProductName();
    if (PlatformUtils.isCLion() || PlatformUtils.isAppCode()) {
      productName = ApplicationNamesInfo.getInstance().getProductName();
    }
    return userHome.replace('/', File.separatorChar) + File.separator + productName + "Projects";
  }

  public static @Nullable Project tryOpenFileList(@Nullable Project project, @NotNull List<? extends File> list, String location) {
    Project result = null;

    for (File file : list) {
      result = openOrImport(file.toPath().toAbsolutePath(), OpenProjectTask.withProjectToClose(project, true));
      if (result != null) {
        LOG.debug(location + ": load project from ", file);
        return result;
      }
    }

    for (File file : list) {
      if (!file.exists()) {
        continue;
      }

      LOG.debug(location + ": open file ", file);
      String path = file.getAbsolutePath();
      if (project != null) {
        OpenFileAction.openFile(path, project);
        result = project;
      }
      else {
        CommandLineProjectOpenProcessor processor = CommandLineProjectOpenProcessor.getInstanceIfExists();
        if (processor != null) {
          Project opened = processor.openProjectAndFile(file.toPath(), -1, -1, false);
          if (opened != null && result == null) {
            result = opened;
          }
        }
      }
    }

    return result;
  }

  public static boolean isValidProjectPath(@NotNull Path file) {
    return Files.isDirectory(file.resolve(Project.DIRECTORY_STORE_FOLDER)) ||
           (Strings.endsWith(file.toString(), ProjectFileType.DOT_DEFAULT_EXTENSION) && Files.isRegularFile(file));
  }
}