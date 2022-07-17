// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.AnimatedIcon.Blinking;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;

import javax.swing.*;
import java.awt.*;

import static com.intellij.util.ui.EmptyIcon.ICON_16;

final class IdeErrorsIcon extends JLabel {
  private static final int ICON_BLINKING_TIMEOUT_MILLIS = 5_000;
  private final boolean myEnableBlink;

  private final MergingUpdateQueue myIconBlinkingTimeoutQueue = new MergingUpdateQueue(
    "ide-errors-icon-blinking-timeouts",
    ICON_BLINKING_TIMEOUT_MILLIS,
    true,
    null);

  IdeErrorsIcon(boolean enableBlink) {
    myEnableBlink = enableBlink;
  }

  void setState(MessagePool.State state) {
    Icon myUnreadIcon = !myEnableBlink ? AllIcons.Ide.FatalError : new Blinking(AllIcons.Ide.FatalError);
    if (state != null && state != MessagePool.State.NoErrors) {
      setIcon(state == MessagePool.State.ReadErrors ? AllIcons.Ide.FatalErrorRead : myUnreadIcon);
      setToolTipText(DiagnosticBundle.message("error.notification.tooltip"));
      getAccessibleContext().setAccessibleDescription(StringUtil.removeHtmlTags(DiagnosticBundle.message("error.notification.tooltip")));
      if (!myEnableBlink) {
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      }
    }
    else {
      setIcon(ICON_16);
      setToolTipText(null);
      if (!myEnableBlink) {
        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
      }
    }

    setupBlinkingTimeout(state);
  }

  /**
   This method disables blinking of Error icon after {@link IdeErrorsIcon#ICON_BLINKING_TIMEOUT_MILLIS} timeout.
   Works only if {@link IdeMessagePanel#NO_DISTRACTION_MODE} is set to true.
   @see <a href="https://youtrack.jetbrains.com/issue/RIDER-79376">RIDER-79376</a>
   */
  private void setupBlinkingTimeout(MessagePool.State state) {
    if (!IdeMessagePanel.NO_DISTRACTION_MODE) {
      return;
    }
    if (state == MessagePool.State.UnreadErrors) {
      myIconBlinkingTimeoutQueue.queue(new Update(myIconBlinkingTimeoutQueue) {
        @Override
        public void run() {
          setIcon(AllIcons.Ide.FatalError);
        }
      });
    }
    else {
      myIconBlinkingTimeoutQueue.cancelAllUpdates();
    }
  }
}
