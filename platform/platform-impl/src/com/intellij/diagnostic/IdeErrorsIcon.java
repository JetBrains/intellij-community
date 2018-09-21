// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic;

import com.intellij.icons.AllIcons;
import com.intellij.ui.AnimatedIcon.Blinking;

import javax.swing.*;
import java.awt.Cursor;

import static com.intellij.util.ui.EmptyIcon.ICON_16;

class IdeErrorsIcon extends JLabel {
  private final Icon myReadIcon;
  private final Icon myUnreadIcon;

  IdeErrorsIcon(boolean enableBlink) {
    myReadIcon = AllIcons.Ide.FatalError_read;
    myUnreadIcon = !enableBlink ? AllIcons.Ide.FatalError : new Blinking(AllIcons.Ide.FatalError);
    setBorder(BorderFactory.createEmptyBorder(0, 1, 0, 1));
  }

  void setState(MessagePool.State state) {
    if (state != null && state != MessagePool.State.NoErrors) {
      setIcon(state == MessagePool.State.ReadErrors ? myReadIcon : myUnreadIcon);
      setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      setToolTipText(DiagnosticBundle.message("error.notification.tooltip"));
    }
    else {
      setIcon(ICON_16);
      setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
      setToolTipText(null);
    }
  }
}