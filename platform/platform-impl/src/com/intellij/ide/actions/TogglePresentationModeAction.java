// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.ui.UISettings;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.impl.DesktopLayout;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.openapi.wm.impl.ProjectFrameHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * @author Konstantin Bulenkov
 */
public final class TogglePresentationModeAction extends AnAction implements DumbAware {
  private static final Logger LOG = Logger.getInstance(TogglePresentationModeAction.class);

  @Override
  public void update(@NotNull AnActionEvent e) {
    boolean selected = UISettings.getInstance().getPresentationMode();
    e.getPresentation().setText(selected ? ActionsBundle.message("action.TogglePresentationMode.exit")
                                         : ActionsBundle.message("action.TogglePresentationMode.enter"));
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e){
    UISettings settings = UISettings.getInstance();
    Project project = e.getProject();

    setPresentationMode(project, !settings.getPresentationMode());
  }

  public static void setPresentationMode(@Nullable Project project, boolean inPresentation) {
    boolean layoutStored = project != null && storeToolWindows(project);
    log(String.format("Will tweak full screen mode for presentation=%b", inPresentation));

    UISettings settings = UISettings.getInstance();
    float fontSize = inPresentation
                     ? settings.getPresentationModeFontSize()
                     : EditorColorsManager.getInstance().getGlobalScheme().getEditorFontSize2D();

    IdeScaleTransformer.scaleToEditorFontSize(fontSize, () -> {
      settings.setPresentationMode(inPresentation);
      return null;
    });

    CompletableFuture<?> callback = project == null ? CompletableFuture.completedFuture(null) : tweakFrameFullScreen(project, inPresentation);
    callback.whenComplete((o, throwable) -> {
      if (layoutStored) {
        restoreToolWindows(project, inPresentation);
      }
    });
  }

  private static CompletableFuture<?> tweakFrameFullScreen(Project project, boolean inPresentation) {
    ProjectFrameHelper frame = ProjectFrameHelper.getFrameHelper(IdeFrameImpl.getActiveFrame());
    if (frame == null) {
      return CompletableFuture.completedFuture(null);
    }

    PropertiesComponent propertiesComponent = PropertiesComponent.getInstance(project);
    if (inPresentation) {
      propertiesComponent.setValue("full.screen.before.presentation.mode", String.valueOf(frame.isInFullScreen()));
      return frame.toggleFullScreen(true);
    }
    else if (frame.isInFullScreen()) {
      final String value = propertiesComponent.getValue("full.screen.before.presentation.mode");
      return frame.toggleFullScreen("true".equalsIgnoreCase(value));
    }
    return CompletableFuture.completedFuture(null);
  }

  private static boolean hideAllToolWindows(@NotNull ToolWindowManagerEx manager) {
    // to clear windows stack
    manager.clearSideStack();

    boolean hasVisible = false;
    for (ToolWindow toolWindow : manager.getToolWindows()) {
      if (toolWindow.isVisible()) {
        toolWindow.hide();
        hasVisible = true;
      }
    }
    return hasVisible;
  }

  static boolean storeToolWindows(@NotNull Project project) {
    ToolWindowManagerEx manager = ToolWindowManagerEx.getInstanceEx(project);

    DesktopLayout layout = manager.getLayout().copy();
    boolean hasVisible = hideAllToolWindows(manager);
    if (hasVisible) {
      manager.setLayoutToRestoreLater(layout);
      manager.activateEditorComponent();
    }
    return hasVisible;
  }

  static void restoreToolWindows(@NotNull Project project, boolean inPresentation) {
    log(String.format("Will restore tool windows for presentation=%b", inPresentation));

    ToolWindowManagerEx manager = ToolWindowManagerEx.getInstanceEx(project);
    DesktopLayout restoreLayout = manager.getLayoutToRestoreLater();
    if (!inPresentation && restoreLayout != null) {
      manager.setLayout(restoreLayout);
    }
  }

  private static void log(String message) {
    if (ApplicationManager.getApplication().isEAP()) LOG.info(message);
    else LOG.debug(message);
  }
}
