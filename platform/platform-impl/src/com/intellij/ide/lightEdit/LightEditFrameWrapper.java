// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit;

import com.intellij.diagnostic.IdeMessagePanel;
import com.intellij.ide.lightEdit.menuBar.LightEditMenuBar;
import com.intellij.ide.lightEdit.statusBar.LightEditAutosaveWidget;
import com.intellij.ide.lightEdit.statusBar.LightEditEncodingWidgetWrapper;
import com.intellij.ide.lightEdit.statusBar.LightEditLineSeparatorWidgetWrapper;
import com.intellij.ide.lightEdit.statusBar.LightEditPositionWidget;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.impl.ProjectFrameAllocatorKt;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.LightEditFrame;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.*;
import com.intellij.openapi.wm.impl.status.IdeStatusBarImpl;
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsActionGroup;
import com.intellij.openapi.wm.impl.status.widget.StatusBarWidgetsManager;
import com.intellij.ui.PopupHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;
import java.util.function.BooleanSupplier;

final class LightEditFrameWrapper extends ProjectFrameHelper implements Disposable, LightEditFrame {
  private final BooleanSupplier myCloseHandler;

  private LightEditPanel myEditPanel;

  private boolean myFrameTitleUpdateEnabled = true;

  LightEditFrameWrapper(@NotNull IdeFrameImpl frame, @NotNull BooleanSupplier closeHandler) {
    super(frame, null);
    myCloseHandler = closeHandler;
  }

  @NotNull
  LightEditPanel getLightEditPanel() {
    return myEditPanel;
  }

  @NotNull
  @Override
  protected IdeRootPane createIdeRootPane() {
    return new LightEditRootPane(getFrame(), this, this);
  }

  @Override
  protected void installDefaultProjectStatusBarWidgets(@NotNull Project project) {
    LightEditorManager editorManager = LightEditService.getInstance().getEditorManager();
    IdeStatusBarImpl statusBar = Objects.requireNonNull(getStatusBar());
    statusBar.addWidget(new LightEditPositionWidget(editorManager), StatusBar.Anchors.before(IdeMessagePanel.FATAL_ERROR), this);
    statusBar.addWidget(new LightEditAutosaveWidget(editorManager), StatusBar.Anchors.before(IdeMessagePanel.FATAL_ERROR), this);
    statusBar.addWidget(new LightEditEncodingWidgetWrapper(), StatusBar.Anchors.after(StatusBar.StandardWidgets.POSITION_PANEL), this);
    statusBar.addWidget(new LightEditLineSeparatorWidgetWrapper(), StatusBar.Anchors.before(LightEditEncodingWidgetWrapper.WIDGET_ID), this);

    PopupHandler.installPopupHandler(statusBar, StatusBarWidgetsActionGroup.GROUP_ID, ActionPlaces.STATUS_BAR_PLACE);
    ApplicationManager.getApplication().invokeLater(() -> {
      // instantiate StatusBarWidgetsManager and create widgets provided by extensions
      project.getService(StatusBarWidgetsManager.class);
    });
  }

  @Override
  protected void initTitleInfoProviders(@NotNull Project project) {
  }

  @NotNull
  @Override
  protected CloseProjectWindowHelper createCloseProjectWindowHelper() {
    return new CloseProjectWindowHelper() {
      @Override
      public void windowClosing(@Nullable Project project) {
        if (myCloseHandler.getAsBoolean()) {
          super.windowClosing(project);
        }
      }
    };
  }

  @Override
  public void dispose() {
    Disposer.dispose(myEditPanel);
  }

  private class LightEditRootPane extends IdeRootPane {
    LightEditRootPane(@NotNull JFrame frame, @NotNull IdeFrame frameHelper, @NotNull Disposable parentDisposable) {
      super(frame, frameHelper, parentDisposable);
    }

    @NotNull
    @Override
    protected Component getCenterComponent(@NotNull JFrame frame, @NotNull Disposable parentDisposable) {
      myEditPanel = new LightEditPanel();
      return myEditPanel;
    }

    @NotNull
    @Override
    public ToolWindowsPane getToolWindowPane() {
      throw new IllegalStateException("Tool windows are unavailable in LightEdit");
    }

    @Override
    protected @NotNull IdeMenuBar createMenuBar() {
      return new LightEditMenuBar();
    }

    @NotNull
    @Override
    protected IdeStatusBarImpl createStatusBar(@NotNull IdeFrame frame) {
      return new IdeStatusBarImpl(frame, false);
    }

    @Override
    protected void updateNorthComponents() {
    }

    @Override
    protected void installNorthComponents(@NotNull Project project) {
    }

    @Override
    protected void deinstallNorthComponents() {
    }
  }

  static @NotNull LightEditFrameWrapper allocate(@NotNull BooleanSupplier closeHandler) {
    return (LightEditFrameWrapper)((WindowManagerImpl)WindowManager.getInstance()).allocateFrame(
      LightEditUtil.getProject(),
      () -> new LightEditFrameWrapper(ProjectFrameAllocatorKt.createNewProjectFrame(false), closeHandler));
  }

  void setFrameTitleUpdateEnabled(boolean frameTitleUpdateEnabled) {
    myFrameTitleUpdateEnabled = frameTitleUpdateEnabled;
  }

  @Override
  public void setFrameTitle(String text) {
    if (myFrameTitleUpdateEnabled) {
      super.setFrameTitle(text);
    }
  }
}
