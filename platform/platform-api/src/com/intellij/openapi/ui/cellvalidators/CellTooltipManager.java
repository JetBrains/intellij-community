// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui.cellvalidators;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.ComponentValidator;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.ui.popup.ComponentPopupBuilder;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.Alarm;
import com.intellij.util.ui.JBInsets;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeListener;
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
  private Rectangle             cellRect;
  private Dimension             popupSize;
  private boolean               isOverPopup;
  private boolean               isClosing;

  @ApiStatus.Experimental
  public CellTooltipManager(@NotNull Disposable parentDisposable) {
    this.parentDisposable = parentDisposable;
  }

  @ApiStatus.Experimental
  public CellTooltipManager withCellComponentProvider(@NotNull CellComponentProvider cellComponentProvider) {
    this.cellComponentProvider = cellComponentProvider;
    return this;
  }

  @ApiStatus.Experimental
  public CellTooltipManager withHyperlinkListener(@NotNull HyperlinkListener hyperlinkListener) {
    this.hyperlinkListener = hyperlinkListener;
    return this;
  }

  @ApiStatus.Experimental
  public void installOn(@NotNull JComponent component) {
    MouseAdapter mouseListener = new ValidationMouseListener();
    PropertyChangeListener propertyChangeListener = e -> {
      if (cellComponentProvider != null && cellComponentProvider.isEditingStarted(e)) {
        hidePopup(true, null);
      }
    };

    component.addMouseListener(mouseListener);
    component.addMouseMotionListener(mouseListener);
    component.addPropertyChangeListener(propertyChangeListener);

    Disposer.register(parentDisposable, () -> {
      hidePopup(true, null);

      component.removeMouseListener(mouseListener);
      component.removeMouseMotionListener(mouseListener);
      component.removePropertyChangeListener(propertyChangeListener);

      cellComponentProvider = null;
      validationInfo = null;
      popupBuilder = null;
    });
  }

  private void handleMouseEvent(MouseEvent e) {
    if (cellComponentProvider != null) {
      JComponent cellRenderer = cellComponentProvider.getCellRendererComponent(e);
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
          }).setCancelOnMouseOutCallback(me -> me.getID() == MouseEvent.MOUSE_PRESSED &&
                                              !ComponentValidator.withinComponent(validationInfo, me) &&
                                              (cellRect == null || !cellRect.contains(me.getPoint())));

          hidePopup(false, () -> showPopup(e));
        }
        else if (!isShowing()) {
          showPopup(e);
        }
        else if (!isClosing) { // Move popup to new location
          Rectangle cellRect = cellComponentProvider.getCellRect(e);
          if (!cellRect.equals(this.cellRect)) {
            this.cellRect = cellRect;
            Point point = new Point(this.cellRect.x + JBUIScale.scale(40), this.cellRect.y - JBUIScale.scale(6) - popupSize.height);
            SwingUtilities.convertPointToScreen(point, cellComponentProvider.getOwner());
            cellPopup.setLocation(point);
          }
        }
      } else {
        validationInfo = null;
        hidePopup(false, null);
      }
    }
  }

  private static boolean hasATag(@NotNull String string) {
    return HTML_A_TAG_PATTERN.matcher(string).find();
  }

  private void showPopup(MouseEvent e) {
    if (!cellComponentProvider.isEditing(e)) {
      cellPopup = popupBuilder.createPopup();
      cellRect = cellComponentProvider.getCellRect(e);
      JComponent c = cellComponentProvider.getCellRendererComponent(e);

      Insets i = c != null ? c.getInsets() : JBInsets.emptyInsets();
      Point point = new Point(cellRect.x + JBUIScale.scale(40), cellRect.y + i.top - JBUIScale.scale(6) - popupSize.height);
      cellPopup.show(new RelativePoint(cellComponentProvider.getOwner(), point));
    }
  }

  private void hidePopup(boolean now, @Nullable Runnable onHidden) {
    if (isShowing()) {
      if (now || hyperlinkListener == null || !closeWithDelay) {
        cellPopup.cancel();
        cellPopup = null;
        cellRect = null;

        if (onHidden != null) {
          onHidden.run();
        }
      } else if (!isClosing) {
        isClosing = true;
        popupAlarm.addRequest(() -> {
          isClosing = false;
          if (!isOverPopup) {
            hidePopup(true, onHidden);
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
      hidePopup(false, null);
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
      hidePopup(true, null);
    }
  }
}
