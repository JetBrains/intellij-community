// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.lightEdit.project.LightEditProjectManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.vfs.encoding.EncodingManagerImpl;
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.NonUrgentExecutor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.update.UiNotifyConnector;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.lang.management.ManagementFactory;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

@SuppressWarnings("SameParameterValue")
@State(name = "LightEdit", storages =  @Storage("lightEdit.xml"))
public final class LightEditServiceImpl implements LightEditService,
                                             Disposable,
                                             LightEditorListener,
                                             AppLifecycleListener,
                                             PersistentStateComponent<LightEditConfiguration> {
  private static final Logger LOG = Logger.getInstance(LightEditServiceImpl.class);

  private LightEditFrameWrapper myFrameWrapper;
  private final LightEditorManagerImpl myEditorManager;
  private final LightEditConfiguration myConfiguration = new LightEditConfiguration();
  private final LightEditProjectManager myLightEditProjectManager = new LightEditProjectManager();

  @Override
  public @NotNull LightEditConfiguration getState() {
    return myConfiguration;
  }

  @Override
  public void loadState(@NotNull LightEditConfiguration state) {
    XmlSerializerUtil.copyBean(state, myConfiguration);
  }

  public LightEditServiceImpl() {
    myEditorManager = new LightEditorManagerImpl(this);
    myEditorManager.addListener(this);
    MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect(this);
    connection.subscribe(AppLifecycleListener.TOPIC,this);
    Disposer.register(this, myEditorManager);
  }

  private void init() {
    if (myFrameWrapper == null) {
      myFrameWrapper = LightEditFrameWrapper.allocate(() -> closeEditorWindow());
      LOG.info("Frame created");
      restoreSession();
    }
    if (!myFrameWrapper.getFrame().isVisible()) {
      myFrameWrapper.getFrame().setVisible(true);
      LOG.info("Window opened");
    }
  }

  @Override
  public void showEditorWindow() {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      init();
      myFrameWrapper.setFrameTitle(getAppName());
    }
  }

  private static String getAppName() {
    return ApplicationInfo.getInstance().getVersionName();
  }

  @Override
  @Nullable
  public Project getProject() {
    return myLightEditProjectManager.getProject();
  }

  @Override
  public @NotNull Project getOrCreateProject() {
    return myLightEditProjectManager.getOrCreateProject();
  }

  @Override
  public boolean openFile(@NotNull VirtualFile file) {
    if (LightEditUtil.isLightEditEnabled()) {
      doWhenActionManagerInitialized(() -> {
        doOpenFile(file);
      });
      return true;
    }
    return false;
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

  private void doOpenFile(@NotNull VirtualFile file) {
    showEditorWindow();
    LightEditorInfo openEditorInfo = myEditorManager.findOpen(file);
    if (openEditorInfo == null) {
      LightEditorInfo newEditorInfo = myEditorManager.createEditor(file);
      if (newEditorInfo != null) {
        addEditorTab(newEditorInfo);
        LOG.info("Opened new tab for " + file.getPresentableUrl());
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
      ObjectUtils.consumeIfNotNull(
        getEditPanel().getTabs().getSelectedInfo(),
        tabInfo ->
          UiNotifyConnector
            .doWhenFirstShown(tabInfo.getComponent(), () -> ApplicationManager.getApplication().invokeLater(() -> {
              LOG.info("Startup took: " + ManagementFactory.getRuntimeMXBean().getUptime() + " ms");
            }))
      );
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
  public LightEditorInfo createNewFile(@Nullable String preferredName) {
    showEditorWindow();
    LightEditorInfo newEditorInfo = myEditorManager.createEmptyEditor(preferredName);
    addEditorTab(newEditorInfo);
    return newEditorInfo;
  }

  @Override
  public boolean closeEditorWindow() {
    if (canClose()) {
      myFrameWrapper.getFrame().setVisible(false);
      saveSession();
      myEditorManager.releaseEditors();
      LOG.info("Window closed");
      if (ProjectManager.getInstance().getOpenProjects().length == 0 && WelcomeFrame.getInstance() == null) {
        disposeFrameWrapper();
        LOG.info("No open projects or welcome frame, exiting");
        try {
          Disposer.dispose(myEditorManager);
          myLightEditProjectManager.close();
          ApplicationManager.getApplication().exit();
        }
        catch (Throwable t) {
          System.exit(1);
        }
      }
      else {
        myFrameWrapper.releaseFrame();
        myFrameWrapper = null;
      }
    }
    else {
      LOG.info("Close cancelled");
    }
    return false;
  }

  private boolean canClose() {
    final FileDocumentManager documentManager = FileDocumentManager.getInstance();
    return !myEditorManager.containsUnsavedDocuments() ||
           autosaveDocuments() ||
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
                 myEditorManager.getUnsavedEditors().forEach(editorInfo -> {
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

  private boolean autosaveDocuments() {
    if (isAutosaveMode()) {
      FileDocumentManager.getInstance().saveAllDocuments();
      return true;
    }
    return false;
  }

  public LightEditPanel getEditPanel() {
    assert !Disposer.isDisposed(myFrameWrapper.getLightEditPanel());
    return myFrameWrapper.getLightEditPanel();
  }

  @Override
  @Nullable
  public VirtualFile getSelectedFile() {
    LightEditFrameWrapper frameWrapper = myFrameWrapper;
    if (frameWrapper == null) return null;
    LightEditPanel panel = frameWrapper.getLightEditPanel();
    if (!Disposer.isDisposed(panel)) {
      return panel.getTabs().getSelectedFile();
    }
    return null;
  }

  @Override
  @Nullable
  public FileEditor getSelectedFileEditor() {
    LightEditFrameWrapper frameWrapper = myFrameWrapper;
    if (frameWrapper == null) return null;
    LightEditPanel panel = frameWrapper.getLightEditPanel();
    if (!Disposer.isDisposed(panel)) {
      return panel.getTabs().getSelectedFileEditor();
    }
    return null;
  }

  @Override
  public void updateFileStatus(@NotNull Collection<VirtualFile> files) {
    List<LightEditorInfo> editors = ContainerUtil.mapNotNull(files, myEditorManager::findOpen);
    if (!editors.isEmpty()) {
      myEditorManager.fireFileStatusChanged(editors);
    }
  }

  @Override
  public void dispose() {
    if (myFrameWrapper != null) {
      disposeFrameWrapper();
    }
  }

  private void disposeFrameWrapper() {
    Disposer.dispose(myFrameWrapper);
    myFrameWrapper = null;
    LOG.info("Frame disposed");
  }

  @Override
  public void afterSelect(@Nullable LightEditorInfo editorInfo) {
    if (myFrameWrapper != null) {
      myFrameWrapper.setFrameTitle(editorInfo == null ? getAppName() : getFileTitle(editorInfo.getFile()));
    }
  }

  private static String getFileTitle(@NotNull VirtualFile file) {
    StringBuilder titleBuilder = new StringBuilder();
    titleBuilder.append(file.getPresentableName());
    VirtualFile parent = file.getParent();
    if (parent != null) {
      titleBuilder.append(" - ").append(truncateUrl(parent.getPresentableUrl()));
    }
    return titleBuilder.toString();
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
    if (myEditorManager.getEditorCount() == 0) {
      closeEditorWindow();
    }
  }

  @Override
  @NotNull
  public LightEditorManager getEditorManager() {
    return myEditorManager;
  }

  @Override
  public void saveToAnotherFile(@NotNull VirtualFile file) {
    LightEditorInfo editorInfo = myEditorManager.getEditorInfo(file);
    if (editorInfo != null) {
      VirtualFile targetFile = LightEditUtil.chooseTargetFile(myFrameWrapper.getLightEditPanel(), editorInfo);
      if (targetFile != null) {
        LightEditorInfo newInfo = myEditorManager.saveAs(editorInfo, targetFile);
        getEditPanel().getTabs().replaceTab(editorInfo, newInfo);
      }
    }
  }

  @Override
  public boolean isAutosaveMode() {
    return myConfiguration.autosaveMode;
  }

  @Override
  public void setAutosaveMode(boolean autosaveMode) {
    myConfiguration.autosaveMode = autosaveMode;
    myEditorManager.fireAutosaveModeChanged(autosaveMode);
  }

  @TestOnly
  public void disposeCurrentSession() {
    myEditorManager.releaseEditors();
    myLightEditProjectManager.close();
  }

  private void saveSession() {
    LightEditTabs tabs = myFrameWrapper.getLightEditPanel().getTabs();
    List<VirtualFile> openFiles = tabs.getOpenFiles();
    myConfiguration.sessionFiles.clear();
    myConfiguration.sessionFiles.addAll(
      ContainerUtil.map(openFiles,
                        openFile -> VfsUtilCore.pathToUrl(openFile.getPath())));
  }

  private void restoreSession() {
    doWhenActionManagerInitialized(() -> {
      myFrameWrapper.setFrameTitleUpdateEnabled(false);
      myConfiguration.sessionFiles.forEach(
        path -> {
          VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(path);
          if (file != null) {
            doOpenFile(file);
          }
        }
      );
      myFrameWrapper.setFrameTitleUpdateEnabled(true);
    });
  }

  @Override
  public void appClosing() {
    ((EncodingManagerImpl)EncodingManager.getInstance()).clearDocumentQueue();
    if (myFrameWrapper != null) {
      myFrameWrapper.getFrame().setVisible(false);
      disposeFrameWrapper();
    }
    Disposer.dispose(myEditorManager);
    myLightEditProjectManager.close();
  }

  @Override
  public void saveNewDocuments() {
    for(VirtualFile virtualFile : myEditorManager.getOpenFiles()) {
      if (virtualFile instanceof LightVirtualFile) {
        saveToAnotherFile(virtualFile);
      }
    }
  }
}
