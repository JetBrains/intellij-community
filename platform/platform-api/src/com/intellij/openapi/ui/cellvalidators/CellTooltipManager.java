// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui.cellvalidators;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.ComponentValidator;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.ui.popup.ComponentPopupBuilder;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Alarm;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.regex.Pattern;

public final class CellTooltipManager {
  private static final Pattern HTML_A_TAG_PATTERN = Pattern.compile("(?i)<a([^>]+)>(.+?)</a>");

  private final Disposable parentDisposable;

  private CellComponentProvider cellComponentProvider;
  private HyperlinkListener hyperlinkListener;

  private final Alarm popupAlarm = new Alarm();

  private ValidationInfo        validationInfo;
  private boolean               closeWithDelay;

  private ComponentPopupBuilder popupBuilder;
  private JBPopup               cellPopup;
  private Dimension             popupSize;
  private boolean               isOverPopup;
  private boolean               isClosing;

  public CellTooltipManager(@NotNull Disposable parentDisposable) {
    this.parentDisposable = parentDisposable;
  }

  public CellTooltipManager withCellComponentProvider(@NotNull CellComponentProvider cellComponentProvider) {
    this.cellComponentProvider = cellComponentProvider;
    return this;
  }

  public CellTooltipManager withHyperlinkListener(@NotNull HyperlinkListener hyperlinkListener) {
    this.hyperlinkListener = hyperlinkListener;
    return this;
  }

  public void installOn(@NotNull JComponent component) {
    MouseAdapter mouseListener = new ValidationMouseListener();

    component.addMouseListener(mouseListener);
    component.addMouseMotionListener(mouseListener);

    Disposer.register(parentDisposable, () -> {
      closePopup(true, null);

      component.removeMouseListener(mouseListener);
      component.removeMouseMotionListener(mouseListener);

      cellComponentProvider = null;
      validationInfo = null;
      popupBuilder = null;
    });
  }

  private void handleMouseEvent(MouseEvent e) {
    if (cellComponentProvider != null) {
      JComponent cellRenderer = cellComponentProvider.getCellRendererComponent(e.getPoint());
      ValidationInfo info = cellRenderer != null ? (ValidationInfo)cellRenderer.getClientProperty(ValidatingTableCellRendererWrapper.CELL_VALIDATION_PROPERTY) : null;

      if (info != null) {
        if (!info.equals(validationInfo)) {
          validationInfo = info;
          closeWithDelay = hasATag(validationInfo.message);

          popupBuilder = ComponentValidator.createPopupBuilder(validationInfo, tipComponent -> {
            if (closeWithDelay) {
              tipComponent.addHyperlinkListener(hyperlinkListener);
              tipComponent.addMouseListener(new TipComponentMouseListener());
            }
            popupSize = tipComponent.getPreferredSize();
          });

          closePopup(false, () -> showPopup(e));
        } else if (!isShowing()) {
          showPopup(e);
        }
      } else {
        validationInfo = null;
        closePopup(false, null);
      }
    }
  }

  private static boolean hasATag(@NotNull String string) {
    return HTML_A_TAG_PATTERN.matcher(string).find();
  }

  private void showPopup(MouseEvent e) {
    cellPopup = popupBuilder.createPopup();

    Point cellLocation = cellComponentProvider.getCellRect(e).getLocation();
    Point point = new Point(cellLocation.x + JBUI.scale(40), cellLocation.y - JBUI.scale(6) - popupSize.height);
    cellPopup.show(new RelativePoint(cellComponentProvider.getOwner(), point));
  }

  private void closePopup(boolean now, @Nullable Runnable onHidden) {
    if (isShowing()) {
      if (now || hyperlinkListener == null || !closeWithDelay) {
        cellPopup.cancel();
        cellPopup = null;

        if (onHidden != null) {
          onHidden.run();
        }
      } else if (!isClosing) {
        isClosing = true;
        popupAlarm.addRequest(() -> {
          isClosing = false;
          if (!isOverPopup) {
            closePopup(true, onHidden);
          }
        }, Registry.intValue("ide.tooltip.initialDelay.highlighter"));
      }
    } else if (onHidden != null) {
      onHidden.run();
    }
  }

  private boolean isShowing() {
    return cellPopup != null && cellPopup.isVisible();
  }

  private class ValidationMouseListener extends MouseAdapter {
    @Override
    public void mouseEntered(MouseEvent e) {
      handleMouseEvent(e);
    }

    @Override
    public void mouseExited(MouseEvent e) {
      closePopup(false, null);
    }

    @Override
    public void mouseMoved(MouseEvent e) {
      handleMouseEvent(e);
    }
  }

  private class TipComponentMouseListener extends MouseAdapter {
    @Override
    public void mouseEntered(MouseEvent e) {
      isOverPopup = true;
    }

    @Override
    public void mouseExited(MouseEvent e) {
      isOverPopup = false;
      closePopup(true, null);
    }
  }
}
