// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.wm;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.Painter;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

public final class IdeGlassPaneUtil {

  private IdeGlassPaneUtil() {
  }

  @NotNull
  public static IdeGlassPane find(@NotNull Component component) {
    if (!(component instanceof JComponent)) {
      throw new IllegalArgumentException("Component must be instance of JComponent");
    }

    JRootPane root = ((JComponent)component).getRootPane();
    if (root == null) {
      throw new IllegalArgumentException("Component must be visible in order to find glass pane for it");
    }

    Component gp = root.getGlassPane();
    if (!(gp instanceof IdeGlassPane)) {
      throw new IllegalArgumentException("Glass pane should be " + IdeGlassPane.class.getName());
    }
    return (IdeGlassPane)gp;
  }

  public static void installPainter(@NotNull JComponent target, @NotNull Painter painter, @NotNull Disposable parent) {
    final UiNotifyConnector connector = new UiNotifyConnector(target, new Activatable() {
      private IdeGlassPane myPane;
      private Disposable myPanePainterListeners = Disposer.newDisposable();

      @Override
      public void showNotify() {
        IdeGlassPane pane = find(target);
        if (myPane != null && myPane != pane) {
          Disposer.dispose(myPanePainterListeners);
        }
        myPane = pane;
        myPanePainterListeners = Disposer.newDisposable("PanePainterListeners");
        Disposer.register(parent, myPanePainterListeners);
        myPane.addPainter(target, painter, myPanePainterListeners);
      }

      @Override
      public void hideNotify() {
        Disposer.dispose(myPanePainterListeners);
      }
    });
    Disposer.register(parent, connector);
  }

  public static boolean canBePreprocessed(@NotNull MouseEvent e) {
    Component c = UIUtil.getDeepestComponentAt(e.getComponent(), e.getX(), e.getY());

    if (JBPopupFactory.getInstance().getParentBalloonFor(c) != null && e.getID() != MouseEvent.MOUSE_DRAGGED) {
      return false;
    }

    if (c instanceof IdeGlassPane.TopComponent) {
      return ((IdeGlassPane.TopComponent)c).canBePreprocessed(e);
    }

    return true;
  }

}
