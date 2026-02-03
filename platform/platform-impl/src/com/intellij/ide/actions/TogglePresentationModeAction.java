// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsUtils;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.impl.DesktopLayout;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import com.intellij.openapi.wm.impl.ProjectFrameHelper;
import com.intellij.ui.mac.MacFullScreenControlsManager;
import kotlin.Unit;
import kotlinx.coroutines.CompletableDeferredKt;
import kotlinx.coroutines.Job;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
public final class TogglePresentationModeAction extends AnAction implements DumbAware, ActionRemoteBehaviorSpecification.Frontend {
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
    setPresentationMode(e.getProject(), !UISettings.getInstance().getPresentationMode());
  }

  public static void setPresentationMode(@Nullable Project project, boolean inPresentation) {
    storeToolWindows(project);
    log(String.format("Will tweak full screen mode for presentation=%b", inPresentation));

    UISettings.getInstance().setPresentationMode(inPresentation);
    float oldScale = UISettingsUtils.getInstance().getCurrentIdeScale();
    UISettings.getInstance().fireUISettingsChanged();

    // If IDE scale hasn't been changed, we need to updateUI here
    if (oldScale == UISettingsUtils.getInstance().getCurrentIdeScale()) {
      LafManager.getInstance().updateUI();
    }

    Job callback = project == null ? CompletableDeferredKt.CompletableDeferred(Unit.INSTANCE) : tweakFrameFullScreen(project, inPresentation);
    callback.invokeOnCompletion(__ -> {
      SwingUtilities.invokeLater(() -> restoreToolWindows(project));
      return Unit.INSTANCE;
    });
  }

  private static @NotNull Job tweakFrameFullScreen(Project project, boolean inPresentation) {
    ProjectFrameHelper frame = ProjectFrameHelper.getFrameHelper(IdeFrameImpl.getActiveFrame());
    if (frame == null) {
      return CompletableDeferredKt.CompletableDeferred(Unit.INSTANCE);
    }

    if (SystemInfo.isMac) {
      MacFullScreenControlsManager.INSTANCE.updateForPresentationMode();
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
    return CompletableDeferredKt.CompletableDeferred(Unit.INSTANCE);
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

  private static void storeToolWindows(@Nullable Project project) {
     storeToolWindows(project, DistractionFreeModeController.isDistractionFreeModeEnabled());
  }

  static void storeToolWindows(@Nullable Project project, boolean inDistractionFree) {
    if (project == null) return;

    boolean inPresentation = UISettings.getInstance().getPresentationMode();
    ToolWindowManagerEx manager = ToolWindowManagerEx.getInstanceEx(project);

    DesktopLayout layout = manager.getLayout().copy();
    if (hideAllToolWindows(manager)) {
      if (shouldStoreOrRestoreToolWindowLayout(inPresentation, inDistractionFree)) {
        manager.setLayoutToRestoreLater(layout);
      }
      manager.activateEditorComponent();
    }
  }

  private static void restoreToolWindows(@Nullable Project project) {
    restoreToolWindows(project, DistractionFreeModeController.isDistractionFreeModeEnabled());
  }

  static void restoreToolWindows(@Nullable Project project, boolean inDistractionFree) {
    if (project == null) return;

    boolean inPresentation = UISettings.getInstance().getPresentationMode();
    log(String.format("Will restore tool windows for presentation=%b", inPresentation));

    ToolWindowManagerEx manager = ToolWindowManagerEx.getInstanceEx(project);
    DesktopLayout restoreLayout = manager.getLayoutToRestoreLater();
    if (shouldStoreOrRestoreToolWindowLayout(inPresentation, inDistractionFree) && restoreLayout != null) {
      manager.setLayout(restoreLayout);
      manager.setLayoutToRestoreLater(null);
    }
  }

  private static boolean shouldStoreOrRestoreToolWindowLayout(boolean inPresentation, boolean inDistractionFree) {
    return !inPresentation && !inDistractionFree;
  }

  private static void log(String message) {
    if (ApplicationManager.getApplication().isEAP()) LOG.info(message);
    else LOG.debug(message);
  }
}
