/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class IdeMessagePanel extends JPanel implements MessagePoolListener, IconLikeCustomStatusBarWidget {
  public static final String FATAL_ERROR = "FatalError";
  private final IdeFatalErrorsIcon myIdeFatal;
  private Balloon myBalloon;

  static final String INTERNAL_ERROR_NOTICE = DiagnosticBundle.message("error.notification.tooltip");

  private IdeErrorsDialog myDialog;
  private boolean myOpeningInProgress;
  private final IdeFrame myFrame;
  private final MessagePool myMessagePool;
  private boolean myNotificationPopupAlreadyShown = false;

  public IdeMessagePanel(@Nullable IdeFrame frame, @NotNull MessagePool messagePool) {
    super(new BorderLayout());
    myIdeFatal = new IdeFatalErrorsIcon(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        openFatals(null);
      }
    }, frame != null);

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
  public void install(@NotNull StatusBar statusBar) {
  }

  @Override
  public JComponent getComponent() {
    return this;
  }

  public void openFatals(@Nullable final LogMessage message) {
    if (myDialog != null) return;
    if (myOpeningInProgress) return;
    myOpeningInProgress = true;

    final Runnable task = new Runnable() {
      @Override
      public void run() {
        if (isOtherModalWindowActive()) {
          if (myDialog == null) {
            EdtExecutorService.getScheduledExecutorInstance().schedule(this, (long)300, TimeUnit.MILLISECONDS);
          }
          return;
        }

        try {
          _openFatals(message);
        }
        finally {
          myOpeningInProgress = false;
        }
      }
    };

    task.run();
  }

  private void _openFatals(@Nullable final LogMessage message) {
    myDialog = new IdeErrorsDialog(myMessagePool, message) {
      @Override
      public void doOKAction() {
        super.doOKAction();
        disposeDialog(this);
      }

      @Override
      public void doCancelAction() {
        super.doCancelAction();
        disposeDialog(this);
      }

      @Override
      protected void updateOnSubmit() {
        super.updateOnSubmit();
        updateState(computeState());
      }
    };

    myMessagePool.addListener(myDialog);
    if (!isOtherModalWindowActive()) {
      if (myBalloon != null) {
        myBalloon.hide();
      }
      myDialog.show();
    }
    else {
      myDialog.close(0);
      disposeDialog(myDialog);
    }
  }

  private void updateState(final IdeFatalErrorsIcon.State state) {
    myIdeFatal.setState(state);
    UIUtil.invokeLaterIfNeeded(() -> setVisible(state != IdeFatalErrorsIcon.State.NoErrors));
  }

  private void disposeDialog(final IdeErrorsDialog listDialog) {
    myMessagePool.removeListener(listDialog);
    updateFatalErrorsIcon();
    myDialog = null;
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
    final Window window = getActiveModalWindow();
    if (window == null) return false;

    return myDialog == null || myDialog.getWindow() != window;

  }

  private static Window getActiveModalWindow() {
    final KeyboardFocusManager manager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
    final Window activeWindow = manager.getActiveWindow();
    if (activeWindow instanceof JDialog) {
      if (((JDialog) activeWindow).isModal()) {
        return activeWindow;
      }
    }

    return null;
  }

  private IdeFatalErrorsIcon.State computeState() {
    final List<AbstractMessage> errors = myMessagePool.getFatalErrors(true, false);
    if (errors.isEmpty()) {
      return IdeFatalErrorsIcon.State.NoErrors;
    }
    else {
      for (AbstractMessage error : errors) {
        if (!error.isRead()) {
          return IdeFatalErrorsIcon.State.UnreadErrors;
        }
      }
      return IdeFatalErrorsIcon.State.ReadErrors;
    }
  }

  void updateFatalErrorsIcon() {
    final IdeFatalErrorsIcon.State state = computeState();
    updateState(state);

    if (state == IdeFatalErrorsIcon.State.NoErrors) {
      myNotificationPopupAlreadyShown = false;
    }
    else if (state == IdeFatalErrorsIcon.State.UnreadErrors && !myNotificationPopupAlreadyShown) {
      Project project = myFrame == null ? null : myFrame.getProject();
      if (project != null) {
        ApplicationManager.getApplication().invokeLater(() -> {
          String notificationText = tryGetFromMessages(myMessagePool.getFatalErrors(false, false));
          showErrorNotification(notificationText, project);
        }, project.getDisposed());
        myNotificationPopupAlreadyShown = true;
      }
    }
  }

  private static final String ERROR_TITLE = DiagnosticBundle.message("error.new.notification.title");
  private static final String ERROR_LINK = DiagnosticBundle.message("error.new.notification.link");

  private void showErrorNotification(@Nullable String notificationText, @NotNull Project project) {
    Notification notification = new Notification("", AllIcons.Ide.FatalError, notificationText == null ? ERROR_TITLE : "", null,
                                                 notificationText == null ? "" : notificationText, NotificationType.ERROR, null);

    if (notificationText == null) {
      notification.addAction(new NotificationAction(ERROR_LINK) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e, @NotNull Notification notification) {
          notification.expire();
          _openFatals(null);
        }
      });
    }

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

  private static String tryGetFromMessages(List<AbstractMessage> messages) {
    String result = null;
    for (AbstractMessage message : messages) {
      String s;
      if (message instanceof LogMessageEx) {
        s = ((LogMessageEx)message).getNotificationText();
      }
      else if (message instanceof GroupedLogMessage) {
        s = tryGetFromMessages(((GroupedLogMessage)message).getMessages());
      }
      else {
        return null;
      }

      if (result == null) {
        result = s;
      }
      else if (!result.equals(s)) {
        // if texts are different, show default
        return null;
      }
    }
    return result;
  }
}
