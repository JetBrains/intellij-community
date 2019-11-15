// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit;

import com.intellij.idea.SplashManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.WindowWrapper;
import com.intellij.openapi.ui.WindowWrapperBuilder;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LightEditService implements Disposable, LightEditorListener {
  private WindowWrapper myWrapper;
  private boolean myWrapperIsStale;
  private final LightEditorManager myEditorManager;

  static LightEditService getInstance() {
    return ServiceManager.getService(LightEditService.class);
  }

  public LightEditService() {
    myEditorManager = new LightEditorManager();
    Disposer.register(this, myEditorManager);
    myEditorManager.addListener(this);
  }

  private void init() {
    if (myWrapper == null || myWrapperIsStale) {
      final LightEditPanel editorPanel = new LightEditPanel(myEditorManager);
      myWrapper =
        new WindowWrapperBuilder(WindowWrapper.Mode.FRAME, editorPanel)
          .setOnCloseHandler(()->handleClose())
          .build();
      SplashManager.hideBeforeShow(myWrapper.getWindow());
    }
  }

  public void showEditorWindow() {
    init();
    myWrapper.show();
    myWrapper.setTitle(getAppName());
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
        getEditPanel().getTabs().addEditorTab(newEditorInfo);
      }
    }
    else {
      getEditPanel().getTabs().selectTab(openEditorInfo);
    }
  }

  private boolean handleClose() {
    disposeEditorPanel();
    myWrapperIsStale = true;
    if (ProjectManager.getInstance().getOpenProjects().length == 0 && WelcomeFrame.getInstance() == null) {
      Disposer.dispose(myWrapper);
      ApplicationManager.getApplication().exit();
    }
    return true;
  }

  public LightEditPanel getEditPanel() {
    return (LightEditPanel)myWrapper.getComponent();
  }

  private void disposeEditorPanel() {
    LightEditPanel editorPanel = getEditPanel();
    Disposer.dispose(editorPanel);
  }

  @Override
  public void dispose() {
    if (myWrapper != null && !myWrapperIsStale) {
      disposeEditorPanel();
      Disposer.dispose(myWrapper);
    }
  }

  @Override
  public void afterSelect(@Nullable LightEditorInfo editorInfo) {
    if (myWrapper != null && !myWrapperIsStale ) {
      myWrapper.setTitle(getAppName() + (editorInfo != null ? ": " + editorInfo.getFile().getPresentableUrl() : ""));
    }
  }

  @Override
  public void afterClose(@NotNull LightEditorInfo editorInfo) {
    if (myEditorManager.getEditorCount() == 0) {
      handleClose();
    }
  }
}
