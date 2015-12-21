/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.concurrency.JobScheduler;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.wm.IconLikeCustomStatusBarWidget;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.ui.LightColors;
import com.intellij.ui.popup.NotificationPopup;
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

  static final String INTERNAL_ERROR_NOTICE = DiagnosticBundle.message("error.notification.tooltip");

  private IdeErrorsDialog myDialog;
  private boolean myOpeningInProgress;
  private final MessagePool myMessagePool;
  private boolean myNotificationPopupAlreadyShown = false;

  public IdeMessagePanel(@NotNull MessagePool messagePool) {
    super(new BorderLayout());
    myIdeFatal = new IdeFatalErrorsIcon(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        openFatals(null);
      }
    });

    myIdeFatal.setVerticalAlignment(SwingConstants.CENTER);

    add(myIdeFatal, BorderLayout.CENTER);

    myMessagePool = messagePool;
    messagePool.addListener(this);

    updateFatalErrorsIcon();

    setOpaque(false);
  }

  @NotNull
  public String ID() {
    return FATAL_ERROR;
  }

  public WidgetPresentation getPresentation(@NotNull PlatformType type) {
    return null;
  }

  public void dispose() {
    myMessagePool.removeListener(this);
  }

  public void install(@NotNull StatusBar statusBar) {
  }

  public JComponent getComponent() {
    return this;
  }

  public void openFatals(@Nullable final LogMessage message) {
    if (myDialog != null) return;
    if (myOpeningInProgress) return;
    myOpeningInProgress = true;

    final Runnable task = new Runnable() {
      public void run() {
        if (isOtherModalWindowActive()) {
          if (myDialog == null) {
            JobScheduler.getScheduler().schedule(this, (long)300, TimeUnit.MILLISECONDS);
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
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        myDialog = new IdeErrorsDialog(myMessagePool, message) {
          public void doOKAction() {
            super.doOKAction();
            disposeDialog(this);
          }

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
          myDialog.show();
        }
        else {
          myDialog.close(0);
          disposeDialog(myDialog);
        }
      }
    });
  }

  private void updateState(final IdeFatalErrorsIcon.State state) {
    myIdeFatal.setState(state);
    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        setVisible(state != IdeFatalErrorsIcon.State.NoErrors);
      }
    });
  }

  private void disposeDialog(final IdeErrorsDialog listDialog) {
    myMessagePool.removeListener(listDialog);
    updateFatalErrorsIcon();
    myDialog = null;
  }

  public void newEntryAdded() {
    updateFatalErrorsIcon();

  }

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
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          String notificationText = tryGetFromMessages(myMessagePool.getFatalErrors(false, false));
          if (notificationText == null) {
            notificationText = INTERNAL_ERROR_NOTICE;
          }
          final JLabel label = new JLabel(notificationText);
          label.setIcon(AllIcons.Ide.FatalError);
          new NotificationPopup(IdeMessagePanel.this, label, LightColors.RED, false, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
              _openFatals(null);
            }
          }, true);
        }
      });
      myNotificationPopupAlreadyShown = true;
    }
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
