// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.MouseEvent;

/**
 * @author Alexander Lobas
 */
public final class HelpTooltipManager extends HelpTooltip {
  public static final String SHORTCUT_PROPERTY = "helptooltip.shortcut";

  public HelpTooltipManager() {
    createMouseListeners();
  }

  public void showTooltip(@NotNull JComponent component, @NotNull MouseEvent event) {
    setTitle(component.getToolTipText(event));
    setShortcut((String)component.getClientProperty(SHORTCUT_PROPERTY));

    if (event.getID() == MouseEvent.MOUSE_ENTERED) {
      myMouseListener.mouseEntered(event);
    }
    else {
      myMouseListener.mouseMoved(event);
    }
  }

  public void hideTooltip() {
    hidePopup(true);
  }
}