// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.lightEdit;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.lightEdit.intentions.openInProject.LightEditOpenInProjectIntention;
import com.intellij.ide.lightEdit.project.LightEditProjectManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.encoding.EncodingManagerImpl;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.openapi.wm.impl.FrameInfo;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.tabs.TabInfo;
import com.intellij.util.concurrency.NonUrgentExecutor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.update.UiNotifyConnector;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

@ApiStatus.Internal
@SuppressWarnings("SameParameterValue")
@State(name = "LightEdit", storages =  @Storage(value = "lightEdit.xml", roamingType = RoamingType.DISABLED))
public final class LightEditServiceImpl implements LightEditService,
                                                   Disposable,
                                                   LightEditorListener,
                                                   PersistentStateComponent<LightEditConfiguration> {
  private static final Logger LOG = Logger.getInstance(LightEditServiceImpl.class);

  private LightEditFrameWrapper frameWrapper;
  private final LightEditorManagerImpl editorManager;
  private final LightEditConfiguration configuration = new LightEditConfiguration();
  private final LightEditProjectManager lightEditProjectManager = new LightEditProjectManager();
  private boolean editorWindowClosing = false;
  private boolean saveSession;

  @Override
  public @NotNull LightEditConfiguration getState() {
    return configuration;
  }

  @Override
  public void loadState(@NotNull LightEditConfiguration state) {
    XmlSerializerUtil.copyBean(state, configuration);
  }

  public LightEditServiceImpl() {
    editorManager = new LightEditorManagerImpl(this);
    editorManager.addListener(this);
    MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect(this);
    connection.subscribe(AppLifecycleListener.TOPIC, new AppLifecycleListener() {
      @Override
      public void appClosing() {
        ((EncodingManagerImpl)EncodingManager.getInstance()).clearDocumentQueue();
        if (frameWrapper != null) {
          closeAndDisposeFrame();
        }
        Disposer.dispose(editorManager);
      }
    });
    Disposer.register(this, editorManager);
  }

  private void init(boolean restoreSession) {
    Project project = getOrCreateProject();
    invokeOnEdt(() -> {
      boolean notify = false;
      if (frameWrapper == null) {
        saveSession = restoreSession;
        frameWrapper = LightEditFrameWrapperKt.allocateLightEditFrame(project, configuration.frameInfo);
        LOG.info("Frame created");
        if (restoreSession) {
          restoreSession();
        }
        notify = true;
      }
      IdeFrameImpl frame = frameWrapper.getFrame();
      if (!frame.isVisible()) {
        frame.setVisible(true);
        LOG.info("Window opened");
        notify = true;
      }
      frameWrapper.setFrameTitle(getAppName());
      if (notify) {
        ApplicationManager.getApplication().getMessageBus().syncPublisher(LightEditServiceListener.TOPIC).lightEditWindowOpened(project);
      }
    });
  }

  @Override
  public void showEditorWindow() {
    doShowEditorWindow(true);
  }

  private void doShowEditorWindow(boolean restoreSession) {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      init(restoreSession);
    }
  }

  private static String getAppName() {
    return ApplicationInfo.getInstance().getVersionName();
  }

  @Override
  public @Nullable Project getProject() {
    return lightEditProjectManager.getProject();
  }

  public @NotNull Project getOrCreateProject() {
    return lightEditProjectManager.getOrCreateProject();
  }

  @Override
  public @NotNull Project openFile(@NotNull VirtualFile file) {
    Project project = lightEditProjectManager.getOrCreateProject();
    LightEditUtil.LightEditCommandLineOptions commandLineOptions = LightEditUtil.getCommandLineOptions();
    doWhenActionManagerInitialized(() -> {
      doOpenFile(file, commandLineOptions == null || !commandLineOptions.shouldWait());
    });
    return project;
  }

  private static void doWhenActionManagerInitialized(@NotNull Runnable callback) {
    ActionManager created = ApplicationManager.getApplication().getServiceIfCreated(ActionManager.class);
    if (created == null) {
      NonUrgentExecutor.getInstance().execute(() -> {
        ActionManager.getInstance();
        invokeOnEdt(callback);
      });
    }
    else {
      invokeOnEdt(callback);
    }
  }

  private static void invokeOnEdt(@NotNull Runnable callback) {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      callback.run();
    }
    else {
      ApplicationManager.getApplication().invokeLater(callback);
    }
  }

  private void doOpenFile(@NotNull VirtualFile file, boolean restoreSession) {
    doShowEditorWindow(restoreSession);
    LightEditorInfo openEditorInfo = editorManager.findOpen(file);
    if (openEditorInfo == null) {
      LightEditorInfo newEditorInfo = editorManager.createEditor(file);
      if (newEditorInfo != null) {
        addEditorTab(newEditorInfo);
        LOG.info("Opened new tab for " + file.getPresentableUrl());
        if (Boolean.TRUE.equals(file.getUserData(LightEditUtil.SUGGEST_SWITCH_TO_PROJECT))) {
          file.putUserData(LightEditUtil.SUGGEST_SWITCH_TO_PROJECT, null);
          if (!LightEditConfiguration.PreferredMode.LightEdit.equals(configuration.preferredMode)) {
            suggestSwitchToProject(getOrCreateProject(), file);
          }
        }
      }
      else {
        processNotOpenedFile(file);
      }
    }
    else {
      selectEditorTab(openEditorInfo);
      LOG.info("Selected tab for " + file.getPresentableUrl());
    }

    logStartupTime();
  }

  private void suggestSwitchToProject(@NotNull Project project, @NotNull VirtualFile file) {
    LightEditConfirmationDialog dialog = new LightEditConfirmationDialog(project);
    dialog.show();
    if (dialog.isDontAsk()) {
      switch (dialog.getExitCode()) {
        case LightEditConfirmationDialog.STAY_IN_LIGHT_EDIT ->
          configuration.preferredMode = LightEditConfiguration.PreferredMode.LightEdit;
        case LightEditConfirmationDialog.PROCEED_TO_PROJECT -> configuration.preferredMode = LightEditConfiguration.PreferredMode.Project;
      }
    }
    if (dialog.getExitCode() == LightEditConfirmationDialog.PROCEED_TO_PROJECT) {
      LightEditOpenInProjectIntention.performOn(getOrCreateProject(), file);
    }
  }

  private void processNotOpenedFile(@NotNull VirtualFile file) {
    FileType fileType = file.getFileType();
    Project project = Objects.requireNonNull(getProject());
    Messages.showWarningDialog(project,
                               ApplicationBundle.message("light.edit.unableToOpenFile.text", file.getPresentableName()),
                               ApplicationBundle.message("light.edit.unableToOpenFile.title"));
    LOG.info("Failed to open " + file.getPresentableUrl() + ", binary: " + fileType.isBinary());
  }

  private void logStartupTime() {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      if (frameWrapper != null) {
        TabInfo info = getEditPanel().getTabs().getSelectedInfo();
        if (info != null) {
          UiNotifyConnector.doWhenFirstShown(info.getComponent(), () -> ApplicationManager.getApplication().invokeLater(() -> {
            LOG.info("Startup took: " + ManagementFactory.getRuntimeMXBean().getUptime() + " ms");
          }));
        }
      }
    }
  }

  private void selectEditorTab(LightEditorInfo openEditorInfo) {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      getEditPanel().getTabs().selectTab(openEditorInfo);
    }
  }

  private void addEditorTab(@NotNull LightEditorInfo newEditorInfo) {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      getEditPanel().getTabs().addEditorTab(newEditorInfo);
    }
  }

  public void closeEditor(@NotNull LightEditorInfo editorInfo) {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      getEditPanel().getTabs().closeTab(editorInfo);
    }
  }

  @Override
  public LightEditorInfo createNewDocument(@Nullable Path preferredSavePath) {
    showEditorWindow();
    String preferredName = preferredSavePath == null ? null : preferredSavePath.getFileName().toString();
    LightEditorInfo newEditorInfo = editorManager.createEmptyEditor(preferredName);
    newEditorInfo.setPreferredSavePath(preferredSavePath);
    addEditorTab(newEditorInfo);
    return newEditorInfo;
  }

  @Override
  public boolean closeEditorWindow() {
    if (!canClose()) {
      LOG.info("Close cancelled");
      return false;
    }

    Project project = frameWrapper.getProject();
    frameWrapper.getFrame().setVisible(false);
    saveSession();
    editorWindowClosing = true;
    try {
      editorManager.closeAllEditors();
    }
    finally {
      editorWindowClosing = false;
    }

    LOG.info("Window closed");
    ApplicationManager.getApplication().getMessageBus().syncPublisher(LightEditServiceListener.TOPIC).lightEditWindowClosed(project);
    if (ProjectManager.getInstance().getOpenProjects().length == 0 && WelcomeFrame.getInstance() == null) {
      closeAndDisposeFrame();
      LOG.info("No open projects or welcome frame, exiting");
      try {
        Disposer.dispose(editorManager);
        ApplicationManager.getApplication().exit();
      }
      catch (Throwable t) {
        System.exit(1);
      }
    }
    else {
      WindowManagerEx.getInstanceEx().releaseFrame(frameWrapper);
      frameWrapper = null;
    }
    return false;
  }

  private boolean canClose() {
    final FileDocumentManager documentManager = FileDocumentManager.getInstance();
    return !editorManager.containsUnsavedDocuments() ||
           autoSaveDocuments() ||
           LightEditUtil.confirmClose(
             ApplicationBundle.message("light.edit.exit.message"),
             ApplicationBundle.message("light.edit.exit.title"),
             new LightEditSaveConfirmationHandler() {

               @Override
               public void onSave() {
                 documentManager.saveAllDocuments();
               }

               @Override
               public void onDiscard() {
                 editorManager.getUnsavedEditors().forEach(editorInfo -> {
                   VirtualFile file = editorInfo.getFile();
                   Document document = documentManager.getDocument(file);
                   if (document != null) {
                     documentManager.reloadFromDisk(document);
                   }
                 });
               }
             }
           );
  }

  private boolean autoSaveDocuments() {
    if (isAutosaveMode()) {
      FileDocumentManager.getInstance().saveAllDocuments();
      return true;
    }
    return false;
  }

  public LightEditPanel getEditPanel() {
    assert !Disposer.isDisposed(frameWrapper.getLightEditPanel());
    return frameWrapper.getLightEditPanel();
  }

  @Override
  public @Nullable VirtualFile getSelectedFile() {
    LightEditFrameWrapper frameWrapper = this.frameWrapper;
    if (frameWrapper == null) return null;
    LightEditPanel panel = frameWrapper.getLightEditPanel();
    if (!Disposer.isDisposed(panel)) {
      return panel.getTabs().getSelectedFile();
    }
    return null;
  }

  @Override
  public @Nullable FileEditor getSelectedFileEditor() {
    LightEditFrameWrapper frameWrapper = this.frameWrapper;
    if (frameWrapper == null) return null;
    LightEditPanel panel = frameWrapper.getLightEditPanel();
    if (!Disposer.isDisposed(panel)) {
      return panel.getTabs().getSelectedFileEditor();
    }
    return null;
  }

  @Override
  public void updateFileStatus(@NotNull Collection<? extends VirtualFile> files) {
    List<LightEditorInfo> editors = ContainerUtil.mapNotNull(files, editorManager::findOpen);
    if (!editors.isEmpty()) {
      editorManager.fireFileStatusChanged(editors);
    }
  }

  @Override
  public void dispose() {
    if (frameWrapper != null) {
      closeAndDisposeFrame();
    }
  }

  private void closeAndDisposeFrame() {
    if (frameWrapper != null) {
      Disposer.dispose(frameWrapper);
      LOG.info("Frame disposed");
    }
  }

  void frameDisposed() {
    frameWrapper = null;
  }

  @Override
  public void afterSelect(@Nullable LightEditorInfo editorInfo) {
    if (frameWrapper != null) {
      frameWrapper.setFrameTitle(editorInfo == null ? getAppName() : getFileTitle(editorInfo));
    }
  }

  private static String getFileTitle(@NotNull LightEditorInfo editorInfo) {
    VirtualFile file = editorInfo.getFile();
    StringBuilder titleBuilder = new StringBuilder();
    titleBuilder.append(file.getPresentableName());
    String parentPath = getPresentablePath(editorInfo);
    if (parentPath != null) {
      titleBuilder.append(" - ").append(truncateUrl(parentPath));
    }
    return titleBuilder.toString();
  }

  private static @Nullable String getPresentablePath(@NotNull LightEditorInfo editorInfo) {
    VirtualFile file = editorInfo.getFile();
    if (file instanceof LightVirtualFile) {
      Path preferredPath = editorInfo.getPreferredSavePath();
      if (preferredPath != null && preferredPath.getParent() != null) {
        return preferredPath.getParent().toString();
      }
    }
    else {
      VirtualFile parent = file.getParent();
      if (parent != null) {
        return parent.getPresentableUrl();
      }
    }
    return null;
  }

  private static String truncateUrl(@NotNull String url) {
    int slashPos = Math.max(url.lastIndexOf('\\'), url.lastIndexOf('/'));
    if (slashPos >= 0) {
      String withoutLast = url.substring(0, slashPos);
      int prevSlashPos = Math.max(withoutLast.lastIndexOf('\\'), withoutLast.lastIndexOf('/'));
      if (prevSlashPos >= 0) {
        String truncated = url.substring(prevSlashPos);
        if (!url.equals(truncated)) {
          return "..." + url.substring(prevSlashPos);
        }
      }
    }
    return url;
  }

  @Override
  public void afterClose(@NotNull LightEditorInfo editorInfo) {
    if (editorManager.getEditorCount() == 0 && !editorWindowClosing) {
      closeEditorWindow();
    }
  }

  @Override
  public @NotNull LightEditorManager getEditorManager() {
    return editorManager;
  }

  private void saveEditorAs(@NotNull LightEditorInfo editorInfo, @NotNull VirtualFile targetFile) {
    LightEditorInfo newInfo = editorManager.saveAs(editorInfo, targetFile);
    getEditPanel().getTabs().replaceTab(editorInfo, newInfo);
  }

  @Override
  public void saveToAnotherFile(@NotNull VirtualFile file) {
    LightEditorInfo editorInfo = editorManager.getEditorInfo(file);
    if (editorInfo != null) {
      VirtualFile targetFile = LightEditUtil.chooseTargetFile(frameWrapper.getLightEditPanel(), editorInfo);
      if (targetFile != null) {
        saveEditorAs(editorInfo, targetFile);
      }
    }
  }

  @Override
  public boolean isAutosaveMode() {
    return configuration.autosaveMode;
  }

  @Override
  public void setAutosaveMode(boolean autosaveMode) {
    configuration.autosaveMode = autosaveMode;
    editorManager.fireAutosaveModeChanged(autosaveMode);
  }

  @TestOnly
  public void disposeCurrentSession() {
    editorManager.releaseEditors();
    Project project = lightEditProjectManager.getProject();
    if (project != null) {
      ProjectManagerEx.getInstanceEx().forceCloseProject(project);
    }
  }

  private void saveSession() {
    if (saveSession) {
      LightEditTabs tabs = frameWrapper.getLightEditPanel().getTabs();
      List<VirtualFile> openFiles = tabs.getOpenFiles();
      configuration.sessionFiles.clear();
      configuration.sessionFiles.addAll(ContainerUtil.map(openFiles, openFile -> VfsUtilCore.pathToUrl(openFile.getPath())));
    }
  }

  private void restoreSession() {
    doWhenActionManagerInitialized(() -> {
      frameWrapper.setFrameTitleUpdateEnabled(false);
      configuration.sessionFiles.forEach(
        path -> {
          VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(path);
          if (file != null) {
            doOpenFile(file, false);
          }
        }
      );
      frameWrapper.setFrameTitleUpdateEnabled(true);
    });
  }

  void setFrameInfo(@NotNull FrameInfo frameInfo) {
    configuration.frameInfo = frameInfo;
  }

  @Override
  public void saveNewDocuments() {
    for(VirtualFile virtualFile : editorManager.getOpenFiles()) {
      LightEditorInfo editorInfo = Objects.requireNonNull(editorManager.getEditorInfo(virtualFile));
      if (editorInfo.isNew()) {
        VirtualFile preferredTarget = LightEditUtil.getPreferredSaveTarget(editorInfo);
        if (preferredTarget != null) {
          saveEditorAs(editorInfo, preferredTarget);
        }
        else {
          saveToAnotherFile(virtualFile);
        }
      }
    }
  }

  @Override
  public boolean isTabNavigationAvailable(@NotNull AnAction navigationAction) {
    return getEditPanel().getTabs().isTabNavigationAvailable(navigationAction);
  }

  @Override
  public void navigateToTab(@NotNull AnAction navigationAction) {
    getEditPanel().getTabs().navigateToTab(navigationAction);
  }

  @Override
  public boolean isPreferProjectMode() {
    return configuration.preferredMode != null && LightEditConfiguration.PreferredMode.Project.equals(configuration.preferredMode);
  }

  @Override
  public boolean isLightEditEnabled() {
    return LightEditUtil.isLightEditEnabled();
  }

  @Override
  public @Nullable Project openFile(@NotNull Path path, boolean suggestSwitchToProject) {
    return LightEditUtil.openFile(path, suggestSwitchToProject);
  }

  @Override
  public boolean isForceOpenInLightEditMode() {
    return LightEditUtil.isForceOpenInLightEditMode();
  }
}
