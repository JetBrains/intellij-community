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
import com.intellij.openapi.wm.impl.ProjectFrameHelper;
import com.intellij.ui.BalloonLayout;
import com.intellij.ui.BalloonLayoutData;
import com.intellij.ui.BalloonLayoutImpl;
import com.intellij.ui.ClickListener;
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

public final class IdeMessagePanel implements MessagePoolListener, IconLikeCustomStatusBarWidget {
  public static final String FATAL_ERROR = "FatalError";

  private static final boolean NOTIFICATIONS_ENABLED = Registry.intValue("ea.indicator.blinking.timeout", -1) < 0;
  private static final String GROUP_ID = "IDE-errors";

  private IdeErrorsIcon icon;
  private final IdeFrame frame;
  private final MessagePool messagePool;

  private Balloon balloon;
  private IdeErrorsDialog dialog;
  private boolean isOpeningInProgress;

  private final JPanel component;

  public IdeMessagePanel(@Nullable IdeFrame frame, @NotNull MessagePool messagePool) {
    component = new JPanel(new BorderLayout());
    component.setOpaque(false);

    this.frame = frame;

    this.messagePool = messagePool;
    messagePool.addListener(this);

    updateIconAndNotify();
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
    messagePool.removeListener(this);
  }

  @Override
  public JComponent getComponent() {
    return component;
  }

  public void openErrorsDialog(@Nullable LogMessage message) {
    if (dialog != null || isOpeningInProgress) {
      return;
    }

    isOpeningInProgress = true;

    new Runnable() {
      @Override
      public void run() {
        if (!isOtherModalWindowActive()) {
          try (AccessToken ignored = ClientId.withClientId(ClientId.getLocalId())) {
            // always show IDE errors to the host
            doOpenErrorsDialog(message);
          }
          finally {
            isOpeningInProgress = false;
          }
        }
        else if (dialog == null) {
          EdtExecutorService.getScheduledExecutorInstance().schedule(this, 300L, TimeUnit.MILLISECONDS);
        }
      }
    }.run();
  }

  private void doOpenErrorsDialog(@Nullable LogMessage message) {
    Project project = frame == null ? null : frame.getProject();
    dialog = new IdeErrorsDialog(messagePool, project, message) {
      @Override
      protected void dispose() {
        super.dispose();
        dialog = null;
        updateIconAndNotify();
      }

      @Override
      protected void updateOnSubmit() {
        super.updateOnSubmit();
        updateIcon(messagePool.getState());
      }
    };
    dialog.show();
  }

  private void updateIcon(MessagePool.State state) {
    UIUtil.invokeLaterIfNeeded(() -> {
      IdeErrorsIcon icon = this.icon;
      if (icon == null) {
        icon = new IdeErrorsIcon(frame != null);
        icon.setVerticalAlignment(SwingConstants.CENTER);
        new ClickListener() {
          @Override
          public boolean onClick(@NotNull MouseEvent event, int clickCount) {
            openErrorsDialog(null);
            return true;
          }
        }.installOn(icon);

        this.icon = icon;
        component.add(icon, BorderLayout.CENTER);
      }

      icon.setState(state);
      component.setVisible(state != MessagePool.State.NoErrors);
    });
  }

  @Override
  public void newEntryAdded() {
    updateIconAndNotify();
  }

  @Override
  public void poolCleared() {
    updateIconAndNotify();
  }

  @Override
  public void entryWasRead() {
    updateIconAndNotify();
  }

  private boolean isOtherModalWindowActive() {
    Window activeWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
    return activeWindow instanceof JDialog &&
           ((JDialog)activeWindow).isModal() &&
           (dialog == null || dialog.getWindow() != activeWindow);
  }

  private void updateIconAndNotify() {
    MessagePool.State state = messagePool.getState();
    updateIcon(state);

    if (state == MessagePool.State.NoErrors) {
      if (balloon != null) {
        Disposer.dispose(balloon);
      }
    }
    else if (state == MessagePool.State.UnreadErrors && balloon == null && isActive(frame) && NOTIFICATIONS_ENABLED) {
      Project project = frame.getProject();
      if (project != null) {
        ApplicationManager.getApplication().invokeLater(() -> showErrorNotification(project), project.getDisposed());
      }
    }
  }

  private static boolean isActive(@Nullable IdeFrame frame) {
    if (frame instanceof ProjectFrameHelper) {
      frame = ((ProjectFrameHelper)frame).getFrame();
    }
    return frame instanceof Window && ((Window)frame).isActive();
  }

  @RequiresEdt
  private void showErrorNotification(@NotNull Project project) {
    if (balloon != null) {
      return;
    }

    NotificationDisplayType displayType = NotificationsConfiguration.getNotificationsConfiguration().getDisplayType(GROUP_ID);
    if (displayType == NotificationDisplayType.NONE) {
      return;
    }

    BalloonLayout layout = frame.getBalloonLayout();
    if (layout == null) {
      Logger.getInstance(IdeMessagePanel.class).error("frame=" + frame + " (" + frame.getClass() + ')');
      return;
    }

    Notification notification = new Notification(GROUP_ID, DiagnosticBundle.message("error.new.notification.title"), NotificationType.ERROR)
      .setIcon(AllIcons.Ide.FatalError)
      .addAction(NotificationAction.createSimpleExpiring(DiagnosticBundle.message("error.new.notification.link"), () -> openErrorsDialog(null)));

    BalloonLayoutData layoutData = BalloonLayoutData.createEmpty();
    layoutData.fadeoutTime = displayType == NotificationDisplayType.STICKY_BALLOON ? 300000 : 10000;
    layoutData.textColor = JBUI.CurrentTheme.Notification.Error.FOREGROUND;
    layoutData.fillColor = JBUI.CurrentTheme.Notification.Error.BACKGROUND;
    layoutData.borderColor = JBUI.CurrentTheme.Notification.Error.BORDER_COLOR;
    layoutData.closeAll = () -> ((BalloonLayoutImpl)layout).closeAll();
    layoutData.showSettingButton = true;

    balloon = NotificationsManagerImpl.createBalloon(frame, notification, false, false, new Ref<>(layoutData), project);
    Disposer.register(balloon, () -> balloon = null);
    layout.add(balloon);
  }
}
