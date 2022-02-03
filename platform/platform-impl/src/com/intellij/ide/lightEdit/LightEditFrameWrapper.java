// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.lightEdit;

import com.intellij.diagnostic.IdeMessagePanel;
import com.intellij.ide.lightEdit.menuBar.LightEditMainMenuHelper;
import com.intellij.ide.lightEdit.statusBar.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
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
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;

final class LightEditFrameWrapper extends ProjectFrameHelper implements Disposable, LightEditFrame {
  private final Project myProject;
  private final BooleanSupplier myCloseHandler;

  private LightEditPanel myEditPanel;

  private boolean myFrameTitleUpdateEnabled = true;

  LightEditFrameWrapper(@NotNull Project project, @NotNull IdeFrameImpl frame, @NotNull BooleanSupplier closeHandler) {
    super(frame, null);
    myProject = project;
    myCloseHandler = closeHandler;
  }

  @Override
  public @NotNull Project getProject() {
    return myProject;
  }

  @NotNull
  LightEditPanel getLightEditPanel() {
    return myEditPanel;
  }

  @Override
  protected @NotNull IdeRootPane createIdeRootPane() {
    return new LightEditRootPane(requireNotNullFrame(), this, this);
  }

  @Override
  protected void installDefaultProjectStatusBarWidgets(@NotNull Project project) {
    LightEditorManager editorManager = LightEditService.getInstance().getEditorManager();
    IdeStatusBarImpl statusBar = Objects.requireNonNull(getStatusBar());
    statusBar.addWidgetToLeft(new LightEditModeNotificationWidget(), this);
    statusBar.addWidget(new LightEditPositionWidget(project, editorManager), StatusBar.Anchors.before(IdeMessagePanel.FATAL_ERROR), this);
    statusBar.addWidget(new LightEditAutosaveWidget(editorManager), StatusBar.Anchors.before(IdeMessagePanel.FATAL_ERROR), this);
    statusBar.addWidget(new LightEditEncodingWidgetWrapper(project), StatusBar.Anchors.after(StatusBar.StandardWidgets.POSITION_PANEL), this);
    statusBar.addWidget(new LightEditLineSeparatorWidgetWrapper(project), StatusBar.Anchors.before(LightEditEncodingWidgetWrapper.WIDGET_ID),
                        this);

    PopupHandler.installPopupMenu(statusBar, StatusBarWidgetsActionGroup.GROUP_ID, ActionPlaces.STATUS_BAR_PLACE);
    StatusBarWidgetsManager statusBarWidgetsManager = project.getService(StatusBarWidgetsManager.class);
    ApplicationManager.getApplication().invokeLater(() -> {
      statusBarWidgetsManager.installPendingWidgets();
    });
    Disposer.register(statusBar, () -> statusBarWidgetsManager.disableAllWidgets());
  }

  @Override
  protected @NotNull List<TitleInfoProvider> getTitleInfoProviders() {
    return Collections.emptyList();
  }

  @Override
  protected @NotNull CloseProjectWindowHelper createCloseProjectWindowHelper() {
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

  public void closeAndDispose(@NotNull LightEditServiceImpl lightEditServiceImpl) {
    IdeFrameImpl frame = requireNotNullFrame();
    FrameInfo frameInfo = ProjectFrameBounds.getInstance(myProject).getActualFrameInfoInDeviceSpace(
      this, frame, (WindowManagerImpl)WindowManager.getInstance()
    );
    lightEditServiceImpl.setFrameInfo(frameInfo);

    frame.setVisible(false);
    Disposer.dispose(this);
  }

  private class LightEditRootPane extends IdeRootPane {
    LightEditRootPane(@NotNull JFrame frame, @NotNull IdeFrame frameHelper, @NotNull Disposable parentDisposable) {
      super(frame, frameHelper, parentDisposable);
    }

    @Override
    protected @NotNull Component createCenterComponent(@NotNull JFrame frame, @NotNull Disposable parentDisposable) {
      myEditPanel = new LightEditPanel(LightEditUtil.requireProject());
      return myEditPanel;
    }

    @Override
    public @NotNull ToolWindowsPane getToolWindowPane() {
      throw new IllegalStateException("Tool windows are unavailable in LightEdit");
    }

    @Override
    protected @Nullable ActionGroup getMainMenuActionGroup() {
      return new LightEditMainMenuHelper().getMainMenuActionGroup();
    }

    @Override
    protected @NotNull IdeStatusBarImpl createStatusBar(@NotNull IdeFrame frame) {
      return new IdeStatusBarImpl(frame, false) {
        @Override
        public void updateUI() {
          setUI(new LightEditStatusBarUI());
        }

        @Override
        public Dimension getPreferredSize() {
          return LightEditStatusBarUI.withHeight(super.getPreferredSize());
        }
      };
    }

    @Override
    public void updateNorthComponents() {
    }

    @Override
    protected void installNorthComponents(@NotNull Project project) {
    }

    @Override
    protected void deinstallNorthComponents() {
    }
  }

  static @NotNull LightEditFrameWrapper allocate(@NotNull Project project,
                                                 @Nullable FrameInfo frameInfo,
                                                 @NotNull BooleanSupplier closeHandler) {
    return (LightEditFrameWrapper)((WindowManagerImpl)WindowManager.getInstance()).allocateFrame(project, () -> {
      return new LightEditFrameWrapper(project, ProjectFrameAllocatorKt.createNewProjectFrame(false, frameInfo), closeHandler);
    });
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
