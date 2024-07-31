// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.Painter;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

public final class IdeGlassPaneUtil {
  private IdeGlassPaneUtil() {
  }

  public static @NotNull IdeGlassPane find(@NotNull Component component) {
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
    Activatable listeners = createPainterActivatable(target, painter);
    UiNotifyConnector connector = UiNotifyConnector.installOn(target, listeners);
    Disposer.register(parent, connector);
  }

  @ApiStatus.Internal
  public static @NotNull Activatable createPainterActivatable(@NotNull JComponent target, @NotNull Painter painter) {
    return new Activatable() {
      private Disposable panePainterListeners;

      @Override
      public void showNotify() {
        IdeGlassPane pane = find(target);
        if (panePainterListeners != null) {
          Disposer.dispose(panePainterListeners);
        }

        panePainterListeners = Disposer.newDisposable("PanePainterListeners");
        pane.addPainter(target, painter, panePainterListeners);
      }

      @Override
      public void hideNotify() {
        if (panePainterListeners != null) {
          Disposer.dispose(panePainterListeners);
        }
      }
    };
  }

  public static boolean canBePreprocessed(@NotNull MouseEvent e) {
    Component component = UIUtil.getDeepestComponentAt(e.getComponent(), e.getX(), e.getY());
    if (component == null) {
      return true;
    }

    if (e.getID() != MouseEvent.MOUSE_DRAGGED) {
      JBPopupFactory popupFactory = ApplicationManager.getApplication().getServiceIfCreated(JBPopupFactory.class);
      if (popupFactory != null && popupFactory.getParentBalloonFor(component) != null) {
        return false;
      }
    }

    if (component instanceof IdeGlassPane.TopComponent) {
      return ((IdeGlassPane.TopComponent)component).canBePreprocessed(e);
    }

    return true;
  }
}
