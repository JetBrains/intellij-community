// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit;

import com.intellij.diagnostic.IdeMessagePanel;
import com.intellij.ide.lightEdit.menuBar.LightEditMenuBar;
import com.intellij.ide.lightEdit.statusBar.LightEditAutosaveWidget;
import com.intellij.ide.lightEdit.statusBar.LightEditPositionWidget;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.impl.*;
import com.intellij.openapi.wm.impl.status.IdeStatusBarImpl;
import com.intellij.platform.ProjectFrameAllocatorKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;
import java.util.function.BooleanSupplier;

class LightEditFrameWrapper extends ProjectFrameHelper implements Disposable, LightEditFrame {

  private final BooleanSupplier myCloseHandler;

  private LightEditPanel myEditPanel;

  LightEditFrameWrapper(@NotNull IdeFrameImpl frame, @NotNull BooleanSupplier closeHandler) {
    super(frame, null);
    getFrame().setJMenuBar(new LightEditMenuBar());
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
    addWidget(this, statusBar, new LightEditPositionWidget(editorManager),
              StatusBar.Anchors.before(IdeMessagePanel.FATAL_ERROR));
    addWidget(this, statusBar, new LightEditAutosaveWidget(editorManager),
              StatusBar.Anchors.before(IdeMessagePanel.FATAL_ERROR));
    for (StatusBarWidgetProvider provider : StatusBarWidgetProvider.EP_NAME.getExtensionList()) {
      if (provider.isCompatibleWith(this)) {
        final StatusBarWidget widget = provider.getWidget(LightEditUtil.getProject());
        if (widget != null) {
          addWidget(this, statusBar, widget, provider.getAnchor());
        }
      }
    }
    statusBar.updateWidgets();
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

  static LightEditFrameWrapper allocate(@NotNull BooleanSupplier closeHandler) {
    return (LightEditFrameWrapper)((WindowManagerImpl)WindowManager.getInstance()).allocateFrame(
      LightEditUtil.getProject(),
      () -> new LightEditFrameWrapper(ProjectFrameAllocatorKt.createNewProjectFrame(), closeHandler));
  }
}
