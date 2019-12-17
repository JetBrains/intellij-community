// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.update.UiNotifyConnector;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.management.ManagementFactory;

@State(name = "LightEdit", storages =  @Storage("lightEdit.xml"))
public class LightEditService implements Disposable, LightEditorListener, PersistentStateComponent<LightEditConfiguration> {
  private static final Logger LOG = Logger.getInstance(LightEditService.class);

  private LightEditFrameWrapper myFrameWrapper;
  private boolean myWrapperIsStale;
  private final LightEditorManager myEditorManager;
  private final LightEditConfiguration myConfiguration = new LightEditConfiguration();

  @Nullable
  @Override
  public LightEditConfiguration getState() {
    return myConfiguration;
  }

  @Override
  public void loadState(@NotNull LightEditConfiguration state) {
    XmlSerializerUtil.copyBean(state, myConfiguration);
  }

  public static LightEditService getInstance() {
    return ServiceManager.getService(LightEditService.class);
  }

  public LightEditService() {
    myEditorManager = new LightEditorManager();
    myEditorManager.addListener(this);
    Disposer.register(this, myEditorManager);
  }

  private void init() {
    if (myFrameWrapper == null || myWrapperIsStale) {
      final LightEditPanel editorPanel = new LightEditPanel(myEditorManager);
      myFrameWrapper = new LightEditFrameWrapper(editorPanel);
      myFrameWrapper.setOnCloseHandler(()-> closeEditorWindow());
      myWrapperIsStale = false;
    }
  }

  public void showEditorWindow() {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      init();
      myFrameWrapper.setTitle(getAppName());
    }
  }

  private static String getAppName() {
    return ApplicationInfo.getInstance().getVersionName();
  }

  public void openFile(@NotNull VirtualFile file) {
    showEditorWindow();
    LightEditorInfo openEditorInfo = myEditorManager.findOpen(file);
    if (openEditorInfo == null) {
      LightEditorInfo newEditorInfo = myEditorManager.createEditor(file);
      if (newEditorInfo != null) {
        addEditorTab(newEditorInfo);
      }
    }
    else {
      selectEditorTab(openEditorInfo);
    }

    logStartupTime();
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

  private void addEditorTab(LightEditorInfo newEditorInfo) {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      getEditPanel().getTabs().addEditorTab(newEditorInfo);
    }
  }

  public void createNewFile() {
    showEditorWindow();
    LightEditorInfo newEditorInfo = myEditorManager.createEditor();
    addEditorTab(newEditorInfo);
  }

  public boolean closeEditorWindow() {
    if (canClose()) {
      disposeEditorPanel();
      myWrapperIsStale = true;
      Disposer.dispose(myEditorManager);
      if (ProjectManager.getInstance().getOpenProjects().length == 0 && WelcomeFrame.getInstance() == null) {
        try {
          ApplicationManager.getApplication().exit();
        }
        catch (Throwable t) {
          System.exit(1);
        }
      }
      return true;
    }
    else {
      return false;
    }
  }

  private boolean canClose() {
    return !myEditorManager.containsUnsavedDocuments() ||
           autosaveDocuments() ||
           LightEditUtil.confirmClose(
             ApplicationBundle.message("light.edit.exit.message"),
             ApplicationBundle.message("light.edit.exit.title"),
             () -> FileDocumentManager.getInstance().saveAllDocuments()
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
    return myFrameWrapper.getLightEditPanel();
  }

  private void disposeEditorPanel() {
    LightEditPanel editorPanel = getEditPanel();
    Disposer.dispose(editorPanel);
  }

  @Override
  public void dispose() {
    if (myFrameWrapper != null) {
      disposeEditorPanel();
      Disposer.dispose(myFrameWrapper);
    }
  }

  @Override
  public void afterSelect(@Nullable LightEditorInfo editorInfo) {
    if (myFrameWrapper != null && !myWrapperIsStale) {
      myFrameWrapper.setTitle(getAppName() + (editorInfo != null ? ": " + editorInfo.getFile().getPresentableUrl() : ""));
    }
  }

  @Override
  public void afterClose(@NotNull LightEditorInfo editorInfo) {
    if (myEditorManager.getEditorCount() == 0) {
      closeEditorWindow();
    }
  }

  @NotNull
  public LightEditorManager getEditorManager() {
    return myEditorManager;
  }

  public void saveToAnotherFile(@NotNull Editor editor) {
    LightEditorInfo editorInfo = myEditorManager.getEditorInfo(editor);
    if (editorInfo != null) {
      VirtualFile targetFile = LightEditUtil.chooseTargetFile(myFrameWrapper.getLightEditPanel(), editorInfo);
      if (targetFile != null) {
        LightEditorInfo newInfo = myEditorManager.saveAs(editorInfo, targetFile);
        getEditPanel().getTabs().replaceTab(editorInfo, newInfo);
      }
    }
  }

  public boolean isAutosaveMode() {
    return myConfiguration.autosaveMode;
  }

  public void setAutosaveMode(boolean autosaveMode) {
    myConfiguration.autosaveMode = autosaveMode;
    myEditorManager.fireAutosaveModeChanged(autosaveMode);
  }
}
