package com.intellij.openapi.wm;

import com.intellij.openapi.ui.Painter;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ui.update.UiNotifyConnector;
import com.intellij.util.ui.update.Activatable;

import javax.swing.*;
import java.awt.*;

public class IdeGlassPaneUtil {

  private IdeGlassPaneUtil() {
  }

  public static IdeGlassPane find(Component component) {
    if (!(component instanceof JComponent)) throw new IllegalArgumentException("Component must be instance of JComponent");

    final JRootPane root = ((JComponent)component).getRootPane();
    if (root == null) new IllegalArgumentException("Component must be visible in order to find glass pane for it");

    final Component gp = root.getGlassPane();
    if (!(gp instanceof IdeGlassPane)) {
      throw new IllegalArgumentException("Glass pane should be " + IdeGlassPane.class.getName());
    }
    return (IdeGlassPane)gp;
  }

  public static void installPainter(final JComponent target, final Painter painter, final Disposable parent) {
    final UiNotifyConnector connector = new UiNotifyConnector(target, new Activatable() {

      IdeGlassPane myPane;

      public void showNotify() {
        IdeGlassPane pane = find(target);
        if (myPane != null && myPane != pane) {
          myPane.removePainter(painter);
        }
        myPane = pane;
        myPane.addPainter(target, painter, parent);
      }

      public void hideNotify() {
        if (myPane != null) {
          myPane.removePainter(painter);
        }
      }
    });
    Disposer.register(parent, connector);
  }

}
