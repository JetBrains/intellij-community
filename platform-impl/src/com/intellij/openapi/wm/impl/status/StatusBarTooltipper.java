package com.intellij.openapi.wm.impl.status;

import com.intellij.openapi.wm.StatusBar;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * @author cdr
 */
public class StatusBarTooltipper {
  public static void install(@NotNull final StatusBarPatch patch, @NotNull final StatusBar statusBar) {
    final JComponent component = patch.getComponent();
    install(patch, component, statusBar);
  }
  public static void install(@NotNull final StatusBarPatch patch, @NotNull final JComponent component, @NotNull final StatusBar statusBar) {
    component.addMouseListener(new MouseAdapter() {
      public void mouseEntered(final MouseEvent e) {
        final String text = statusBar instanceof StatusBarImpl ? patch.updateStatusBar(((StatusBarImpl)statusBar).getEditor(), component) : null;
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
