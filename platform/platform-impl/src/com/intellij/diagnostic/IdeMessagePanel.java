// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.icons.AllIcons;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.notification.impl.NotificationsManagerImpl;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.wm.IconLikeCustomStatusBarWidget;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.ui.BalloonLayout;
import com.intellij.ui.BalloonLayoutData;
import com.intellij.ui.JBColor;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class IdeMessagePanel extends JPanel implements MessagePoolListener, IconLikeCustomStatusBarWidget {
  public static final String FATAL_ERROR = "FatalError";

  private final IdeErrorsIcon myIdeFatal;
  private final IdeFrame myFrame;
  private final MessagePool myMessagePool;

  private Balloon myBalloon;
  private IdeErrorsDialog myDialog;
  private boolean myOpeningInProgress;
  private boolean myNotificationPopupAlreadyShown;

  public IdeMessagePanel(@Nullable IdeFrame frame, @NotNull MessagePool messagePool) {
    super(new BorderLayout());

    myIdeFatal = new IdeErrorsIcon(e -> openErrorsDialog(null), frame != null);
    myIdeFatal.setVerticalAlignment(SwingConstants.CENTER);
    add(myIdeFatal, BorderLayout.CENTER);

    myFrame = frame;

    myMessagePool = messagePool;
    messagePool.addListener(this);

    updateFatalErrorsIcon();

    setOpaque(false);
  }

  @Override
  @NotNull
  public String ID() {
    return FATAL_ERROR;
  }

  @Override
  public WidgetPresentation getPresentation(@NotNull PlatformType type) {
    return null;
  }

  @Override
  public void dispose() {
    myMessagePool.removeListener(this);
  }

  @Override
  public void install(@NotNull StatusBar statusBar) { }

  @Override
  public JComponent getComponent() {
    return this;
  }

  /** @deprecated use {@link #openErrorsDialog(LogMessage)} (to be removed in IDEA 2019) */
  @SuppressWarnings("SpellCheckingInspection")
  public void openFatals(@Nullable LogMessage message) {
    openErrorsDialog(message);
  }

  public void openErrorsDialog(@Nullable LogMessage message) {
    if (myDialog != null) return;
    if (myOpeningInProgress) return;
    myOpeningInProgress = true;

    new Runnable() {
      @Override
      public void run() {
        if (!isOtherModalWindowActive()) {
          try {
            doOpenErrorsDialog(message);
          }
          finally {
            myOpeningInProgress = false;
          }
        }
        else if (myDialog == null) {
          EdtExecutorService.getScheduledExecutorInstance().schedule(this, 300L, TimeUnit.MILLISECONDS);
        }
      }
    }.run();
  }

  private void doOpenErrorsDialog(@Nullable LogMessage message) {
    if (isOtherModalWindowActive()) {
      return;
    }

    Project project = myFrame != null ? myFrame.getProject() : null;
    myDialog = new IdeErrorsDialog(myMessagePool, project, message) {
      @Override
      protected void dispose() {
        super.dispose();
        myDialog = null;
        updateFatalErrorsIcon();
      }

      @Override
      protected void updateOnSubmit() {
        super.updateOnSubmit();
        updateState(computeState());
      }
    };

    if (myBalloon != null) {
      myBalloon.hide();
    }

    myDialog.show();
  }

  private void updateState(IdeErrorsIcon.State state) {
    myIdeFatal.setState(state);
    UIUtil.invokeLaterIfNeeded(() -> setVisible(state != IdeErrorsIcon.State.NoErrors));
  }

  @Override
  public void newEntryAdded() {
    updateFatalErrorsIcon();
  }

  @Override
  public void poolCleared() {
    updateFatalErrorsIcon();
  }

  @Override
  public void entryWasRead() {
    updateFatalErrorsIcon();
  }

  private boolean isOtherModalWindowActive() {
    Window activeWindow = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
    return activeWindow instanceof JDialog &&
           ((JDialog)activeWindow).isModal() &&
           (myDialog == null || myDialog.getWindow() != activeWindow);
  }

  private IdeErrorsIcon.State computeState() {
    List<AbstractMessage> unsent = myMessagePool.getFatalErrors(true, false);
    if (unsent.isEmpty()) return IdeErrorsIcon.State.NoErrors;
    if (unsent.stream().allMatch(AbstractMessage::isRead)) return IdeErrorsIcon.State.ReadErrors;
    return IdeErrorsIcon.State.UnreadErrors;
  }

  private void updateFatalErrorsIcon() {
    IdeErrorsIcon.State state = computeState();
    updateState(state);

    if (state == IdeErrorsIcon.State.NoErrors) {
      myNotificationPopupAlreadyShown = false;
    }
    else if (state == IdeErrorsIcon.State.UnreadErrors && !myNotificationPopupAlreadyShown) {
      Project project = myFrame == null ? null : myFrame.getProject();
      if (project != null) {
        ApplicationManager.getApplication().invokeLater(() -> showErrorNotification(project), project.getDisposed());
        myNotificationPopupAlreadyShown = true;
      }
    }
  }

  private static final String ERROR_TITLE = DiagnosticBundle.message("error.new.notification.title");
  private static final String ERROR_LINK = DiagnosticBundle.message("error.new.notification.link");

  private void showErrorNotification(@NotNull Project project) {
    Notification notification = new Notification("", AllIcons.Ide.FatalError, ERROR_TITLE, null, null, NotificationType.ERROR, null);
    notification.addAction(new NotificationAction(ERROR_LINK) {
      @Override
      public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
        notification.expire();
        doOpenErrorsDialog(null);
      }
    });

    BalloonLayout layout = myFrame.getBalloonLayout();
    assert layout != null;

    BalloonLayoutData layoutData = BalloonLayoutData.createEmpty();
    layoutData.fadeoutTime = 5000;
    layoutData.fillColor = new JBColor(0XF5E6E7, 0X593D41);
    layoutData.borderColor = new JBColor(0XE0A8A9, 0X73454B);

    assert myBalloon == null;
    myBalloon = NotificationsManagerImpl.createBalloon(myFrame, notification, false, false, new Ref<>(layoutData), project);
    Disposer.register(myBalloon, () -> myBalloon = null);
    layout.add(myBalloon);
  }
}