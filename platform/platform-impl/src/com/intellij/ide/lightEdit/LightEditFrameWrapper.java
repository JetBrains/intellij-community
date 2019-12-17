// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit;

import com.intellij.diagnostic.IdeMessagePanel;
import com.intellij.ide.lightEdit.menuBar.LightEditMenuBar;
import com.intellij.ide.lightEdit.statusBar.LightEditAutosaveWidget;
import com.intellij.ide.lightEdit.statusBar.LightEditPositionWidget;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.*;
import com.intellij.openapi.wm.impl.status.IdeStatusBarImpl;
import com.intellij.platform.ProjectFrameAllocatorKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;
import java.util.function.BooleanSupplier;

class LightEditFrameWrapper implements Disposable {

  private final LightEditPanel myLightEditPanel;
  private final ProjectFrameHelper myProjectFrameHelper;
  private BooleanSupplier myCloseHandler;

  LightEditFrameWrapper(@NotNull LightEditPanel lightEditPanel) {
    myLightEditPanel = lightEditPanel;
    myProjectFrameHelper = allocateFrame();
    Disposer.register(this, myProjectFrameHelper);
    myProjectFrameHelper.getFrame().setJMenuBar(new LightEditMenuBar());
  }

  @NotNull
  LightEditPanel getLightEditPanel() {
    return myLightEditPanel;
  }

  @NotNull
  private ProjectFrameHelper allocateFrame() {
    return ((WindowManagerImpl)WindowManager.getInstance()).allocateFrame(LightEditUtil.getProject(), () -> {
      return new ProjectFrameHelper(ProjectFrameAllocatorKt.createNewProjectFrame(), null) {
        @NotNull
        @Override
        protected IdeRootPane createIdeRootPane() {
          return new LightEditRootPane(getFrame(), this, this);
        }

        @Override
        protected void installDefaultProjectStatusBarWidgets(@NotNull Project project) {
          IdeStatusBarImpl statusBar = Objects.requireNonNull(getStatusBar());
          addWidget(project, statusBar, new LightEditPositionWidget(myLightEditPanel.getEditorManager()),
                    StatusBar.Anchors.before(IdeMessagePanel.FATAL_ERROR));
          addWidget(project, statusBar, new LightEditAutosaveWidget(myLightEditPanel.getEditorManager()),
                    StatusBar.Anchors.before(IdeMessagePanel.FATAL_ERROR));
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
      };
    });
  }

  public void setTitle(@NotNull String title) {
    IdeFrameImpl frame = myProjectFrameHelper.getFrame();
    frame.setTitle(title);
  }

  public void setOnCloseHandler(@NotNull BooleanSupplier closeHandler) {
    myCloseHandler = closeHandler;
  }

  @Override
  public void dispose() {
  }

  private class LightEditRootPane extends IdeRootPane {
    LightEditRootPane(@NotNull JFrame frame, @NotNull IdeFrame frameHelper, @NotNull Disposable parentDisposable) {
      super(frame, frameHelper, parentDisposable);
    }

    @NotNull
    @Override
    protected Component getCenterComponent(@NotNull JFrame frame, @NotNull Disposable parentDisposable) {
      return myLightEditPanel;
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
}
