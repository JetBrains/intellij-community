package com.intellij.openapi.wm.impl.status;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * @author cdr
 */
public class StatusBarTooltipper {
  public static void install(@NotNull final StatusBarPatch patch, @NotNull final StatusBarImpl statusBar) {
    final JComponent component = patch.getComponent();
    install(patch, component, statusBar);
  }
  public static void install(@NotNull final StatusBarPatch patch, @NotNull final JComponent component, @NotNull final StatusBarImpl statusBar) {
    component.addMouseListener(new MouseAdapter() {
      public void mouseEntered(final MouseEvent e) {
        final String text = patch.updateStatusBar(statusBar.getEditor(), component);
        statusBar.setInfo(text);
        component.setToolTipText(text);
      }

      public void mouseExited(final MouseEvent e) {
        statusBar.setInfo(null);
        component.setToolTipText(null);
      }
    });
  }
}
