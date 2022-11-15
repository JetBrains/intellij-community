// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.codeWithMe.ClientId;
import com.intellij.icons.AllIcons;
import com.intellij.notification.*;
import com.intellij.notification.impl.NotificationsManagerImpl;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IconLikeCustomStatusBarWidget;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.impl.ProjectFrameHelper;
import com.intellij.ui.BalloonLayout;
import com.intellij.ui.BalloonLayoutData;
import com.intellij.ui.BalloonLayoutImpl;
import com.intellij.ui.ClickListener;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.concurrent.TimeUnit;

public final class IdeMessagePanel extends NonOpaquePanel implements MessagePoolListener, IconLikeCustomStatusBarWidget {
  public static final String FATAL_ERROR = "FatalError";

  private static final boolean NOTIFICATIONS_ENABLED = Registry.intValue("ea.indicator.blinking.timeout", -1) < 0;
  private static final String GROUP_ID = "IDE-errors";

  private Balloon myBalloon;
  private IdeErrorsDialog myDialog;
  private boolean myOpeningInProgress;

  public IdeMessagePanel(@Nullable IdeFrame frame, @NotNull MessagePool messagePool) {
    super(new BorderLayout());
  }

  @Override
  public @NotNull String ID() {
    return FATAL_ERROR;
  }

  @Override
  public WidgetPresentation getPresentation() {
    return null;
  }

  @Override
  public void dispose() {
  }

  @Override
  public void install(@NotNull StatusBar statusBar) { }

  @Override
  public JComponent getComponent() {
    return this;
  }

  @Override
  public void newEntryAdded() {

  }

  @Override
  public void poolCleared() {

  }

  @Override
  public void entryWasRead() {

  }

  private boolean isOtherModalWindowActive() {
    Window activeWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
    return activeWindow instanceof JDialog &&
           ((JDialog)activeWindow).isModal() &&
           (myDialog == null || myDialog.getWindow() != activeWindow);
  }

  private static boolean isActive(@Nullable IdeFrame frame) {
    if (frame instanceof ProjectFrameHelper) {
      frame = ((ProjectFrameHelper)frame).getFrame();
    }
    return frame instanceof Window && ((Window)frame).isActive();
  }

}
