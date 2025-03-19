// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.ClientProperty;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.function.Supplier;

@ApiStatus.Internal
public final class HelpTooltipManager extends HelpTooltip {
  public static final Key<Supplier<@NlsSafe String>> SHORTCUT_PROPERTY = Key.create("help-tooltip-shortcut");

  public HelpTooltipManager() {
    createMouseListeners();
  }

  public void showTooltip(@NotNull JComponent component, @NotNull MouseEvent event) {
    setTitle(component.getToolTipText(event));
    Supplier<String> shortcutSupplier = ClientProperty.get(component, SHORTCUT_PROPERTY);
    setShortcut(shortcutSupplier == null ? null : shortcutSupplier.get());

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