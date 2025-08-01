// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.dsl.builder.DslComponentProperty;
import com.intellij.ui.dsl.gridLayout.UnscaledGapsKt;
import com.intellij.ui.picker.ColorListener;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static java.beans.EventHandler.create;

public class ColorPanel extends JComponent {
  private static final RelativeFont MONOSPACED_FONT = RelativeFont.SMALL.family(Font.MONOSPACED);
  private final List<ActionListener> myListeners = new CopyOnWriteArrayList<>();
  private final JTextField myTextField = new JBTextField(9);
  private boolean myEditable;
  private ActionEvent myEvent;
  private Color myColor;
  private boolean mySupportTransparency;

  public ColorPanel() {
    addImpl(myTextField, null, 0);
    setEditable(true);
    setMinimumSize(JBUI.size(10, 10));
    myTextField.addMouseListener(create(MouseListener.class, this, "onPressed", null, "mousePressed"));
    myTextField.addKeyListener(create(KeyListener.class, this, "onPressed", "keyCode", "keyPressed"));
    myTextField.setEditable(false);
    myTextField.putClientProperty(JBTextField.IS_FORCE_INNER_BACKGROUND_PAINT, true);
    MONOSPACED_FONT.install(myTextField);

    putClientProperty(DslComponentProperty.VISUAL_PADDINGS, UnscaledGapsKt.toUnscaledGaps(myTextField.getInsets()));
  }

  @SuppressWarnings("unused") // used from event handler
  public void onPressed(int keyCode) {
    if (keyCode == KeyEvent.VK_SPACE) {
      onPressed();
    }
  }

  public void onPressed() {
    if (myEditable && isEnabled()) {
      RelativePoint location = new RelativePoint(this, new Point(getWidth() / 2, getHeight()));
      ColorChooserService.getInstance().showPopup(null, myColor, new ColorListener() {
        @Override
        public void colorChanged(Color color, Object source) {
          setSelectedColor(color);
          if (!myListeners.isEmpty() && (myEvent == null)) {
            try {
              myEvent = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "colorPanelChanged");
              for (ActionListener listener : myListeners) {
                listener.actionPerformed(myEvent);
              }
            }
            finally {
              myEvent = null;
            }
          }
        }
      }, location, mySupportTransparency);
    }
  }

  @Override
  public void doLayout() {
    Rectangle bounds = new Rectangle(getWidth(), getHeight());
    JBInsets.removeFrom(bounds, getInsets());
    myTextField.setBounds(bounds);
  }

  @Override
  public Dimension getPreferredSize() {
    if (isPreferredSizeSet()) {
      return super.getPreferredSize();
    }
    Dimension size = myTextField.getPreferredSize();
    JBInsets.addTo(size, getInsets());
    return size;
  }

  @Override
  public String getToolTipText() {
    return myTextField.getToolTipText();
  }

  public void removeActionListener(ActionListener actionlistener) {
    myListeners.remove(actionlistener);
  }

  public void addActionListener(ActionListener actionlistener) {
    myListeners.add(actionlistener);
  }

  public @Nullable Color getSelectedColor() {
    return myColor;
  }

  public void setSelectedColor(@Nullable Color color) {
    myColor = color;
    updateSelectedColor();
  }

  @SuppressWarnings("UseJBColor")
  private void updateSelectedColor() {
    boolean enabled = isEnabled();
    if (enabled && myEditable) {
      myTextField.setEnabled(true);
      myTextField.setToolTipText(UIBundle.message("color.panel.select.color.tooltip.text"));
    }
    else {
      myTextField.setEnabled(false);
      myTextField.setToolTipText(null);
    }
    Color color = enabled ? myColor : null;
    if (color != null) {
      myTextField.setText(StringUtil.toUpperCase(ColorUtil.toHex(color)));
    }
    else {
      myTextField.setText(null);
      color = getBackground();
    }
    myTextField.setBackground(color);
    myTextField.setSelectedTextColor(color);
    if (color != null) {
      int gray = (int)(0.212656 * color.getRed() + 0.715158 * color.getGreen() + 0.072186 * color.getBlue());
      int delta = gray < 0x20 ? 0x60 : gray < 0x50 ? 0x40 : gray < 0x80 ? 0x20 : gray < 0xB0 ? -0x20 : gray < 0xE0 ? -0x40 : -0x60;
      gray += delta;
      color = new Color(gray, gray, gray);
      myTextField.setDisabledTextColor(color);
      myTextField.setSelectionColor(color);
      gray += delta;
      color = new Color(gray, gray, gray);
      myTextField.setForeground(color);
    }
  }

  public void setEditable(boolean editable) {
    myEditable = editable;
    updateSelectedColor();
  }

  @Override
  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    updateSelectedColor();
  }

  public void setSupportTransparency(boolean supportTransparency) {
    mySupportTransparency = supportTransparency;
  }
}
