// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.impl;

import com.intellij.CommonBundle;
import com.intellij.configurationStore.StoreUtil;
import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector;
import com.intellij.ide.GeneralSettings;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.IdeCoreBundle;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.ide.actions.OpenFileAction;
import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.openapi.application.*;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.components.impl.stores.IProjectStore;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.*;
import com.intellij.platform.CommandLineProjectOpenProcessor;
import com.intellij.platform.PlatformProjectOpenProcessor;
import com.intellij.project.ProjectKt;
import com.intellij.projectImport.ProjectOpenProcessor;
import com.intellij.ui.AppIcon;
import com.intellij.util.*;
import com.intellij.util.io.PathKt;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.intellij.openapi.util.NullableLazyValue.lazyNullable;

public final class ProjectUtil extends ProjectUtilCore {
  private static final Logger LOG = Logger.getInstance(ProjectUtil.class);

  public static final String DEFAULT_PROJECT_NAME = "default";
  public static final String PROJECTS_DIR = "projects";
  public static final String PROPERTY_PROJECT_PATH = "%s.project.path";

  private static String ourProjectsPath;

  private ProjectUtil() { }

  /** @deprecated Use {@link #updateLastProjectLocation(Path)} */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
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

  /** @deprecated Use {@link ProjectManagerEx#closeAndDispose(Project)} */
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
    OpenResult openResult = tryOpenOrImport(file, options);
    if (openResult instanceof OpenResult.Success) {
      return ((OpenResult.Success)openResult).getProject();
    }
    return null;
  }

  @ApiStatus.Experimental
  public static @NotNull OpenResult tryOpenOrImport(@NotNull Path file, @NotNull OpenProjectTask options) {
    if (!options.getForceOpenInNewFrame()) {
      Project existing = findAndFocusExistingProjectForPath(file);
      if (existing != null) {
        return new OpenResult.Success(existing);
      }
    }

    NullableLazyValue<VirtualFile> lazyVirtualFile = lazyNullable(() -> getFileAndRefresh(file));

    if (!TrustedProjects.confirmOpeningAndSetProjectTrustedStateIfNeeded(file)) {
      return OpenResult.cancel();
    }

    for (ProjectOpenProcessor provider : ProjectOpenProcessor.EXTENSION_POINT_NAME.getIterable()) {
      if (!provider.isStrongProjectInfoHolder()) {
        continue;
      }

      // `PlatformProjectOpenProcessor` is not a strong project info holder, so there is no need to optimize (VFS not required)
      VirtualFile virtualFile = lazyVirtualFile.getValue();
      if (virtualFile == null) {
        return OpenResult.failure();
      }

      if (provider.canOpenProject(virtualFile)) {
        Project project = chooseProcessorAndOpen(Collections.singletonList(provider), virtualFile, options);
        return openResult(project, OpenResult.cancel());
      }
    }

    if (isValidProjectPath(file)) {
      // see OpenProjectTest.`open valid existing project dir with inability to attach using OpenFileAction` test about why `runConfigurators = true` is specified here
      Project project = ProjectManagerEx.getInstanceEx().openProject(file, options.withRunConfigurators());
      return openResult(project, OpenResult.failure());
    }

    if (options.checkDirectoryForFileBasedProjects && Files.isDirectory(file)) {
      try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(file)) {
        for (Path child : directoryStream) {
          String childPath = child.toString();
          if (childPath.endsWith(ProjectFileType.DOT_DEFAULT_EXTENSION)) {
            Project project = openProject(Paths.get(childPath), options);
            return openResult(project, OpenResult.failure());
          }
        }
      }
      catch (IOException ignore) { }
    }

    List<ProjectOpenProcessor> processors = computeProcessors(file, lazyVirtualFile);
    if (processors.isEmpty()) {
      return OpenResult.failure();
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
        return OpenResult.failure();
      }

      project = chooseProcessorAndOpen(processors, virtualFile, options);
    }

    if (project == null) {
      return OpenResult.failure();
    }

    postProcess(project);
    return new OpenResult.Success(project);
  }

  private static @NotNull OpenResult openResult(@Nullable Project project, @NotNull OpenResult alternative) {
    return project != null ? new OpenResult.Success(project) : alternative;
  }

  public static @NotNull CompletableFuture<@Nullable Project> openOrImportAsync(@NotNull Path file, @NotNull OpenProjectTask options) {
    if (!options.getForceOpenInNewFrame()) {
      Project existing = findAndFocusExistingProjectForPath(file);
      if (existing != null) {
        return CompletableFuture.completedFuture(existing);
      }
    }

    NullableLazyValue<VirtualFile> lazyVirtualFile = lazyNullable(() -> getFileAndRefresh(file));

    for (ProjectOpenProcessor provider : ProjectOpenProcessor.EXTENSION_POINT_NAME.getIterable()) {
      if (!provider.isStrongProjectInfoHolder()) {
        continue;
      }

      // `PlatformProjectOpenProcessor` is not a strong project info holder, so there is no need to optimize (VFS not required)
      VirtualFile virtualFile = lazyVirtualFile.getValue();
      if (virtualFile == null) {
        return CompletableFuture.completedFuture(null);
      }

      if (provider.canOpenProject(virtualFile)) {
        return chooseProcessorAndOpenAsync(Collections.singletonList(provider), virtualFile, options);
      }
    }

    if (isValidProjectPath(file)) {
      // see OpenProjectTest.`open valid existing project dir with inability to attach using OpenFileAction` test about why `runConfigurators = true` is specified here
      return ProjectManagerEx.getInstanceEx().openProjectAsync(file, options.withRunConfigurators());
    }

    if (options.checkDirectoryForFileBasedProjects && Files.isDirectory(file)) {
      try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(file)) {
        for (Path child : directoryStream) {
          String childPath = child.toString();
          if (childPath.endsWith(ProjectFileType.DOT_DEFAULT_EXTENSION)) {
            return CompletableFuture.completedFuture(openProject(Paths.get(childPath), options));
          }
        }
      }
      catch (IOException ignore) { }
    }

    List<ProjectOpenProcessor> processors = computeProcessors(file, lazyVirtualFile);
    if (processors.isEmpty()) {
      return CompletableFuture.completedFuture(null);
    }

    CompletableFuture<Project> projectFuture;
    if (processors.size() == 1 && processors.get(0) instanceof PlatformProjectOpenProcessor) {
      projectFuture = ProjectManagerEx.getInstanceEx().openProjectAsync(file, options.asNewProjectAndRunConfigurators().withBeforeOpenCallback(p -> {
        p.putUserData(PlatformProjectOpenProcessor.PROJECT_OPENED_BY_PLATFORM_PROCESSOR, Boolean.TRUE);
        return true;
      }));
    }
    else {
      VirtualFile virtualFile = lazyVirtualFile.getValue();
      if (virtualFile == null) {
        return CompletableFuture.completedFuture(null);
      }

      projectFuture = chooseProcessorAndOpenAsync(processors, virtualFile, options);
    }

    return projectFuture.thenApply(ProjectUtil::postProcess);
  }

  private static @NotNull List<ProjectOpenProcessor> computeProcessors(@NotNull Path file, @NotNull NullableLazyValue<? extends VirtualFile> lazyVirtualFile) {
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
    return processors;
  }

  private static @Nullable Project postProcess(@Nullable Project project) {
    if (project == null) {
      return null;
    }

    StartupManager.getInstance(project).runAfterOpened(() -> {
      ModalityUiUtil.invokeLaterIfNeeded(ModalityState.NON_MODAL, project.getDisposed(), () -> {
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.PROJECT_VIEW);
        if (toolWindow != null) {
          toolWindow.activate(null);
        }
      });
    });
    return project;
  }

  private static @Nullable Project chooseProcessorAndOpen(@NotNull List<? extends ProjectOpenProcessor> processors,
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
      else if (options.getOpenProcessorChooser() != null) {
        LOG.info("options.openProcessorChooser will handle the open processor dilemma");
        processor = options.getOpenProcessorChooser().invoke(processors);
      }
      else {
        Ref<ProjectOpenProcessor> ref = new Ref<>();
        ApplicationManager.getApplication().invokeAndWait(() -> {
          ref.set(SelectProjectOpenProcessorDialog.showAndGetChoice(processors, virtualFile));
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

  private static @NotNull CompletableFuture<@Nullable Project> chooseProcessorAndOpenAsync(@NotNull List<ProjectOpenProcessor> processors,
                                                                                           @NotNull VirtualFile virtualFile,
                                                                                           @NotNull OpenProjectTask options) {
    CompletableFuture<ProjectOpenProcessor> processorFuture;
    if (processors.size() == 1) {
      processorFuture = CompletableFuture.completedFuture(processors.get(0));
    }
    else {
      processors.removeIf(it -> it instanceof PlatformProjectOpenProcessor);
      if (processors.size() == 1) {
        processorFuture = CompletableFuture.completedFuture(processors.get(0));
      }
      else if (options.getOpenProcessorChooser() != null) {
        LOG.info("options.openProcessorChooser will handle the open processor dilemma");
        processorFuture = CompletableFuture.completedFuture(options.getOpenProcessorChooser().invoke(processors));
      }
      else {
        processorFuture = CompletableFuture.supplyAsync(() -> {
          return SelectProjectOpenProcessorDialog.showAndGetChoice(processors, virtualFile);
        }, ApplicationManager.getApplication()::invokeLater);
      }
    }

    return processorFuture.thenCompose(processor -> {
      if (processor == null) {
        return CompletableFuture.completedFuture(null);
      }

      CompletableFuture<Project> future =
        processor.openProjectAsync(virtualFile, options.getProjectToClose(), options.getForceOpenInNewFrame());
      if (future != null) {
        return future;
      }

      return CompletableFuture.supplyAsync(() -> {
        return processor.doOpenProject(virtualFile, options.getProjectToClose(), options.getForceOpenInNewFrame());
      }, ApplicationManager.getApplication()::invokeLater);
    });
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

    if (options.getUntrusted()) {
      if (!confirmLoadingFromRemotePath(file.toString(), "warning.open.file.from.untrusted.source", "title.open.file.from.untrusted.source")) {
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

  public static boolean showYesNoDialog(@NotNull @Nls String message, @NotNull @PropertyKey(resourceBundle = IdeBundle.BUNDLE) String titleKey) {
    return MessageDialogBuilder.yesNo(IdeBundle.message(titleKey), message)
      .icon(Messages.getWarningIcon())
      .ask(getActiveFrameOrWelcomeScreen());
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

  public static @Nullable Project findAndFocusExistingProjectForPath(@NotNull Path file) {
    Project[] openProjects = getOpenProjects();
    if (openProjects.length == 0) {
      return null;
    }

    for (Project project : openProjects) {
      if (isSameProject(file, project)) {
        focusProjectWindow(project, false);
        return project;
      }
    }
    return null;
  }

  /**
   * @return {@link GeneralSettings#OPEN_PROJECT_SAME_WINDOW} or
   *         {@link GeneralSettings#OPEN_PROJECT_NEW_WINDOW} or
   *         {@link Messages#CANCEL} (when a user cancels the dialog)
   */
  public static int confirmOpenNewProject(boolean isNewProject) {
    return confirmOpenNewProject(isNewProject, null);
  }

  /**
   * @param isNewProject true if the project is just created
   * @param projectName name of the project to open (can be displayed to the user)
   * @return {@link GeneralSettings#OPEN_PROJECT_SAME_WINDOW} or
   *         {@link GeneralSettings#OPEN_PROJECT_NEW_WINDOW} or
   *         {@link Messages#CANCEL} (when a user cancels the dialog)
   */
  public static int confirmOpenNewProject(boolean isNewProject, @Nullable String projectName) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return GeneralSettings.OPEN_PROJECT_NEW_WINDOW;
    }

    int mode = GeneralSettings.getInstance().getConfirmOpenNewProject();
    if (mode == GeneralSettings.OPEN_PROJECT_ASK) {
      String message = projectName == null ? 
                       IdeBundle.message("prompt.open.project.in.new.frame") :
                       IdeBundle.message("prompt.open.project.with.name.in.new.frame", projectName);
      if (isNewProject) {
        boolean openInExistingFrame =
          MessageDialogBuilder.yesNo(IdeCoreBundle.message("title.new.project"), message)
            .yesText(IdeBundle.message("button.existing.frame"))
            .noText(IdeBundle.message("button.new.frame"))
            .doNotAsk(new ProjectNewWindowDoNotAskOption())
            .guessWindowAndAsk();
        mode = openInExistingFrame ? GeneralSettings.OPEN_PROJECT_SAME_WINDOW : GeneralSettings.OPEN_PROJECT_NEW_WINDOW;
      }
      else {
        int exitCode =
          MessageDialogBuilder.yesNoCancel(IdeBundle.message("title.open.project"), message)
            .yesText(IdeBundle.message("button.existing.frame"))
            .noText(IdeBundle.message("button.new.frame"))
            .doNotAsk(new ProjectNewWindowDoNotAskOption())
            .guessWindowAndAsk();
        mode = exitCode == Messages.YES ? GeneralSettings.OPEN_PROJECT_SAME_WINDOW :
               exitCode == Messages.NO ? GeneralSettings.OPEN_PROJECT_NEW_WINDOW :
               Messages.CANCEL;
      }
      if (mode != Messages.CANCEL) {
        LifecycleUsageTriggerCollector.onProjectFrameSelected(mode);
      }
    }
    return mode;
  }

  /**
   * @return {@link GeneralSettings#OPEN_PROJECT_SAME_WINDOW} or
   *         {@link GeneralSettings#OPEN_PROJECT_NEW_WINDOW} or
   *         {@link GeneralSettings#OPEN_PROJECT_SAME_WINDOW_ATTACH} or
   *         {@code -1} (when a user cancels the dialog)
   */
  public static int confirmOpenOrAttachProject() {
    int mode = GeneralSettings.getInstance().getConfirmOpenNewProject();
    if (mode == GeneralSettings.OPEN_PROJECT_ASK) {
      int exitCode = Messages.showDialog(
        IdeBundle.message("prompt.open.project.or.attach"),
        IdeBundle.message("prompt.open.project.or.attach.title"),
        new String[]{
          IdeBundle.message("prompt.open.project.or.attach.button.this.window"),
          IdeBundle.message("prompt.open.project.or.attach.button.new.window"),
          IdeBundle.message("prompt.open.project.or.attach.button.attach"),
          CommonBundle.getCancelButtonText()
        },
        0,
        Messages.getQuestionIcon(),
        new ProjectNewWindowDoNotAskOption());
      mode = exitCode == 0 ? GeneralSettings.OPEN_PROJECT_SAME_WINDOW :
             exitCode == 1 ? GeneralSettings.OPEN_PROJECT_NEW_WINDOW :
             exitCode == 2 ? GeneralSettings.OPEN_PROJECT_SAME_WINDOW_ATTACH :
             -1;
      if (mode != -1) {
        LifecycleUsageTriggerCollector.onProjectFrameSelected(mode);
      }
    }
    return mode;
  }

  /** @deprecated Use {@link #isSameProject(Path, Project)} */
  @Deprecated
  public static boolean isSameProject(@Nullable String projectFilePath, @NotNull Project project) {
    return projectFilePath != null && isSameProject(Paths.get(projectFilePath), project);
  }

  public static boolean isSameProject(@NotNull Path projectFile, @NotNull Project project) {
    IProjectStore projectStore = ProjectKt.getStateStore(project);
    Path existingBaseDirPath = projectStore.getProjectBasePath();

    if (existingBaseDirPath.getFileSystem() != projectFile.getFileSystem()) {
      return false;
    }

    if (Files.isDirectory(projectFile)) {
      try {
        return Files.isSameFile(projectFile, existingBaseDirPath);
      }
      catch (IOException ignore) {
        return false;
      }
    }

    if (projectStore.getStorageScheme() == StorageScheme.DEFAULT) {
      try {
        return Files.isSameFile(projectFile, projectStore.getProjectFilePath());
      }
      catch (IOException ignore) {
        return false;
      }
    }

    Path parent = projectFile.getParent();
    if (parent == null) {
      return false;
    }

    Path parentFileName = parent.getFileName();
    if (parentFileName != null && parentFileName.toString().equals(Project.DIRECTORY_STORE_FOLDER)) {
      parent = parent.getParent();
      return parent != null && FileUtil.pathsEqual(parent.toString(), existingBaseDirPath.toString());
    }

    return projectFile.getFileName().toString().endsWith(ProjectFileType.DOT_DEFAULT_EXTENSION) &&
           FileUtil.pathsEqual(parent.toString(), existingBaseDirPath.toString());
  }

  /**
   * Focuses the specified project's window. If {@code stealFocusIfAppInactive} is {@code true} and corresponding logic is supported by OS
   * (making it work on Windows requires enabling focus stealing system-wise, see {@link com.intellij.ui.WinFocusStealer}), the window will
   * get the focus even if other application is currently active. Otherwise, there will be some indication that the target window requires
   * user attention. Focus stealing behaviour (enabled by {@code stealFocusIfAppInactive}) is generally not considered a proper application
   * behaviour, and should only be used in special cases, when we know that user definitely expects it.
   */
  public static void focusProjectWindow(@Nullable Project project, boolean stealFocusIfAppInactive) {
    JFrame frame = WindowManager.getInstance().getFrame(project);
    if (frame == null) {
      return;
    }

    boolean appIsActive = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow() != null;

    // On macOS, `j.a.Window#toFront` restores the frame if needed.
    // On X Window, restoring minimized frame can steal focus from an active application, so we do it only when the IDE is active.
    if (SystemInfo.isWindows || SystemInfo.isXWindow && appIsActive) {
      int state = frame.getExtendedState();
      if ((state & Frame.ICONIFIED) != 0) {
        frame.setExtendedState(state & ~Frame.ICONIFIED);
      }
    }

    if (stealFocusIfAppInactive) {
      AppIcon.getInstance().requestFocus((IdeFrame)frame);
    }
    else {
      if (!SystemInfo.isXWindow || appIsActive) {
        // some Linux window managers allow `j.a.Window#toFront` to steal focus, so we don't call it on Linux when the IDE is inactive
        frame.toFront();
      }
      if (!SystemInfo.isWindows) {
        // on Windows, `j.a.Window#toFront` will request attention if needed
        AppIcon.getInstance().requestAttention(project, true);
      }
    }
  }

  public static @NotNull String getBaseDir() {
    String defaultDirectory = GeneralSettings.getInstance().getDefaultProjectDirectory();
    if (Strings.isNotEmpty(defaultDirectory)) {
      return defaultDirectory.replace('/', File.separatorChar);
    }
    final String lastProjectLocation = RecentProjectsManager.getInstance().getLastProjectCreationLocation();
    if (lastProjectLocation != null) {
      return lastProjectLocation.replace('/', File.separatorChar);
    }
    return getUserHomeProjectDir();
  }

  public static String getUserHomeProjectDir() {
    String productName;
    if (PlatformUtils.isCLion() || PlatformUtils.isAppCode() || PlatformUtils.isDataGrip()) {
      productName = ApplicationNamesInfo.getInstance().getProductName();
    }
    else {
      productName = ApplicationNamesInfo.getInstance().getLowercaseProductName();
    }
    return SystemProperties.getUserHome().replace('/', File.separatorChar) + File.separator + productName + "Projects";
  }

  public static @Nullable Project tryOpenFiles(@Nullable Project project, @NotNull List<? extends Path> list, String location) {
    try {
      for (Path file : list) {
        OpenResult openResult = tryOpenOrImport(file.toAbsolutePath(), OpenProjectTask.withProjectToClose(project, true));
        if (openResult instanceof OpenResult.Success) {
          LOG.debug(location + ": load project from ", file);
          return ((OpenResult.Success)openResult).getProject();
        }
        else if (openResult instanceof OpenResult.Cancel) {
          LOG.debug(location + ": canceled project opening");
          return null;
        }
      }
    }
    catch (ProcessCanceledException ex) {
      LOG.debug(location + ": skip project opening");
      return null;
    }

    Project result = null;
    for (Path file : list) {
      if (!Files.exists(file)) {
        continue;
      }

      LOG.debug(location + ": open file ", file);
      if (project != null) {
        VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(FileUtilRt.toSystemIndependentName(file.toString()));
        if (virtualFile != null && virtualFile.isValid()) {
          OpenFileAction.openFile(virtualFile, project);
        }
        result = project;
      }
      else {
        CommandLineProjectOpenProcessor processor = CommandLineProjectOpenProcessor.getInstanceIfExists();
        if (processor != null) {
          Project opened = processor.openProjectAndFile(file, -1, -1, false);
          if (opened != null && result == null) {
            result = opened;
          }
        }
      }
    }

    return result;
  }

  @NotNull
  @SystemDependent
  public static String getProjectsPath() { //todo: merge somehow with getBaseDir
    Application application = ApplicationManager.getApplication();
    String fromSettings = application == null || application.isHeadlessEnvironment() ? null :
                          GeneralSettings.getInstance().getDefaultProjectDirectory();
    if (StringUtil.isNotEmpty(fromSettings)) {
      return PathManager.getAbsolutePath(fromSettings);
    }
    if (ourProjectsPath == null) {
      String produceName = ApplicationNamesInfo.getInstance().getProductName().toLowerCase(Locale.ENGLISH);
      String propertyName = String.format(PROPERTY_PROJECT_PATH, produceName);
      String propertyValue = System.getProperty(propertyName);
      ourProjectsPath = propertyValue != null
                        ? PathManager.getAbsolutePath(StringUtil.unquoteString(propertyValue, '\"'))
                        : getProjectsDirDefault();
    }
    return ourProjectsPath;
  }

  private static @NotNull String getProjectsDirDefault() {
    if (PlatformUtils.isDataGrip()) return getUserHomeProjectDir();
    return PathManager.getConfigPath() + File.separator + PROJECTS_DIR;
  }

  public static @NotNull Path getProjectPath(@NotNull String name) {
    return Paths.get(getProjectsPath(), name);
  }

  public static @Nullable Path getProjectFile(@NotNull String name) {
    Path projectDir = getProjectPath(name);
    return Files.isDirectory(projectDir.resolve(Project.DIRECTORY_STORE_FOLDER)) ? projectDir : null;
  }

  public static @Nullable Project openOrCreateProject(@NotNull String name) {
    return openOrCreateProject(name, null);
  }

  public static @Nullable Project openOrCreateProject(@NotNull String name, @Nullable ProjectCreatedCallback  projectCreatedCallback) {
    return ProgressManager.getInstance().computeInNonCancelableSection(() -> openOrCreateProjectInner(name, projectCreatedCallback));
  }

  public interface ProjectCreatedCallback {
    void projectCreated(Project project);
  }

  public static @NotNull Set<String> getExistingProjectNames() {
    Set<String> result = new LinkedHashSet<>();
    File file = new File(getProjectsPath());
    for (String name : ObjectUtils.notNull(file.list(), ArrayUtilRt.EMPTY_STRING_ARRAY)) {
      if (getProjectFile(name) != null) {
        result.add(name);
      }
    }
    return result;
  }

  private static @Nullable Project openOrCreateProjectInner(@NotNull String name, @Nullable ProjectCreatedCallback projectCreatedCallback) {
    Path existingFile = getProjectFile(name);
    if (existingFile != null) {
      Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
      for (Project p : openProjects) {
        if (!p.isDefault() && isSameProject(existingFile, p)) {
          focusProjectWindow(p, false);
          return p;
        }
      }
      return ProjectManagerEx.getInstanceEx().openProject(existingFile, new OpenProjectTask().withRunConfigurators());
    }

    Path file = getProjectPath(name);
    boolean created;
    try {
      created = (!Files.exists(file) && Files.createDirectories(file) != null) || Files.isDirectory(file);
    }
    catch (IOException e) {
      created = false;
    }

    Path projectFile = null;
    if (created) {
      Project project = ProjectManagerEx.getInstanceEx().newProject(file, OpenProjectTask.newProject(true).withProjectName(name));
      if (project != null) {
        if (projectCreatedCallback != null) {
          projectCreatedCallback.projectCreated(project);
        }
        saveAndDisposeProject(project);
        projectFile = getProjectFile(name);
      }
    }
    if (projectFile == null) {
      return null;
    }
    return ProjectManagerEx.getInstanceEx().openProject(projectFile, OpenProjectTask.fromWizardAndRunConfigurators());
  }

  private static void saveAndDisposeProject(@NotNull Project project) {
    StoreUtil.saveSettings(project, true);
    ApplicationManager.getApplication().invokeAndWait(() -> {
      WriteAction.run(() -> Disposer.dispose(project));
    });
  }
}
