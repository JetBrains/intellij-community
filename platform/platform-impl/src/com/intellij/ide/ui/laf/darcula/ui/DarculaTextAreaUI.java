// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf.darcula.ui;

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.openapi.client.ClientSystemInfo;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicTextAreaUI;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

public class DarculaTextAreaUI extends BasicTextAreaUI {

  private RoundedBorderSupportHandler helper;

  @SuppressWarnings("MethodOverridesStaticMethodOfSuperclass")
  public static ComponentUI createUI(final JComponent c) {
    return new DarculaTextAreaUI();
  }

  @Override
  protected void installListeners() {
    super.installListeners();
    helper = new RoundedBorderSupportHandler(getComponent());
  }

  @Override
  protected void uninstallListeners() {
    super.uninstallListeners();
    if (helper != null) {
      helper.dispose();
      helper = null;
    }
  }

  @Override
  protected void installKeyboardActions() {
    super.installKeyboardActions();
    if (ClientSystemInfo.isMac()) {
      InputMap inputMap = getComponent().getInputMap();
      inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), DefaultEditorKit.upAction);
      inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), DefaultEditorKit.downAction);
      inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, 0), DefaultEditorKit.pageUpAction);
      inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, 0), DefaultEditorKit.pageDownAction);
    }
  }

  @Override
  public int getNextVisualPositionFrom(JTextComponent t, int pos, Position.Bias b, int direction, Position.Bias[] biasRet)
    throws BadLocationException {
    int position = DarculaUIUtil.getPatchedNextVisualPositionFrom(t, pos, direction);
    return position != -1 ? position : super.getNextVisualPositionFrom(t, pos, b, direction, biasRet);
  }

  @Override
  protected Caret createCaret() {
    return new TextFieldWithPopupHandlerUI.MouseDragAwareCaret();
  }

  @Override
  protected void paintSafely(Graphics g) {
    if (SystemInfo.isMacOSCatalina) {
      ((Graphics2D)g).setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
    }
    super.paintSafely(g);
  }

  private static class RoundedBorderSupportHandler {

    private final JTextComponent component;

    private final FocusListener focusListener = new FocusListener() {
      @Override
      public void focusGained(FocusEvent e) {
        repaintParentScrollPane();
      }

      @Override
      public void focusLost(FocusEvent e) {
        repaintParentScrollPane();
      }
    };

    private final DocumentListener documentListener = new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        repaintParentScrollPane();
      }
    };

    private final PropertyChangeListener propertyChangeListener = new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        if ("JComponent.outline".equals(evt.getPropertyName())) {
          repaintParentScrollPane();
        }

        if ("document".equals(evt.getPropertyName())) {
          if (evt.getOldValue() instanceof Document document) {
            document.removeDocumentListener(documentListener);
          }
          if (evt.getNewValue() instanceof Document document) {
            document.addDocumentListener(documentListener);
          }
        }
      }
    };

    private RoundedBorderSupportHandler(JTextComponent component) {
      this.component = component;

      component.addFocusListener(focusListener);
      component.addPropertyChangeListener(propertyChangeListener);
      component.getDocument().addDocumentListener(documentListener);
    }

    public void dispose() {
      component.removeFocusListener(focusListener);
      component.removePropertyChangeListener(propertyChangeListener);
      component.getDocument().removeDocumentListener(documentListener);
    }

    private void repaintParentScrollPane() {
      if (component.getParent() instanceof JViewport viewport && viewport.getParent() instanceof JBScrollPane scrollPane) {
        scrollPane.repaint();
      }
    }
  }
}
