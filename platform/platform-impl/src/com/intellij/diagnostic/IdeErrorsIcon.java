// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.CoroutinesKt;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.AnimatedIcon.Blinking;
import com.intellij.util.ui.update.DebouncedUpdates;
import com.intellij.util.ui.update.UpdateQueue;
import kotlinx.coroutines.Dispatchers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import javax.swing.JLabel;
import java.awt.Cursor;
import java.util.concurrent.TimeUnit;

import static com.intellij.util.ui.EmptyIcon.ICON_16;

final class IdeErrorsIcon extends JLabel {
  private static final int TIMEOUT = (int)TimeUnit.SECONDS.toMillis(Registry.intValue("ea.indicator.blinking.timeout", -1));

  private final boolean myEnableBlink;
  private final @Nullable UpdateQueue<MessagePool.State> myBlinkTimeoutQueue;

  IdeErrorsIcon(boolean canBlink) {
    myEnableBlink = canBlink && TIMEOUT != 0;
    myBlinkTimeoutQueue = myEnableBlink && TIMEOUT > 0
      ? DebouncedUpdates.<MessagePool.State>forComponent(this, "ide-error-icon-blink-timeout", TIMEOUT)
          .withContext(CoroutinesKt.getUI(Dispatchers.INSTANCE))
          .restartTimerOnAdd(true)
          .runLatest(state -> stopBlinking(state))
      : null;
  }

  private void stopBlinking(MessagePool.State state) {
    if (state == MessagePool.State.UnreadErrors) {
      setIcon(AllIcons.Ide.FatalError);
    }
  }

  private static @NotNull Icon getUnreadIcon() {
    return ApplicationInfo.getInstance().isEAP() ?
           AllIcons.Ide.FatalError : AllIcons.Ide.FatalErrorRead; // let's be less annoying in releases
  }

  void setState(@NotNull MessagePool.State state) {
    Icon myUnreadIcon = myEnableBlink ? new Blinking(AllIcons.Ide.FatalError) : getUnreadIcon();
    if (state != MessagePool.State.NoErrors) {
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

    if (myBlinkTimeoutQueue != null) {
      myBlinkTimeoutQueue.queue(state);
    }
  }
}
