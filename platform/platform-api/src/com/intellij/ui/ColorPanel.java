/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui;

import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static java.beans.EventHandler.create;
import static java.util.Locale.ENGLISH;

public class ColorPanel extends JComponent {
  private static final RelativeFont MONOSPACED_FONT = RelativeFont.SMALL.family(Font.MONOSPACED);
  private final List<ActionListener> myListeners = new CopyOnWriteArrayList<>();
  private final JTextField myTextField = new JTextField(8);
  private boolean myEditable;
  private ActionEvent myEvent;
  private Color myColor;

  public ColorPanel() {
    addImpl(myTextField, null, 0);
    setEditable(true);
    setMinimumSize(JBUI.size(10, 10));
    myTextField.addMouseListener(create(MouseListener.class, this, "onPressed", null, "mousePressed"));
    myTextField.addKeyListener(create(KeyListener.class, this, "onPressed", "keyCode", "keyPressed"));
    myTextField.setEditable(false);
    MONOSPACED_FONT.install(myTextField);
    Painter.BACKGROUND.install(myTextField, true);
  }

  @SuppressWarnings("unused") // used from event handler
  public void onPressed(int keyCode) {
    if (keyCode == KeyEvent.VK_SPACE) {
      onPressed();
    }
  }

  public void onPressed() {
    if (myEditable && isEnabled()) {
      Color color = ColorChooser.chooseColor(this, UIBundle.message("color.panel.select.color.dialog.description"), myColor);
      if (color != null) {
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

  @Nullable
  public Color getSelectedColor() {
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
      myTextField.setText(' ' + ColorUtil.toHex(color).toUpperCase(ENGLISH) + ' ');
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

  @Override
  public void repaint() {
    super.repaint();
  }

  private static class Painter implements Highlighter.HighlightPainter, PropertyChangeListener {
    private static final String PROPERTY = "highlighter";
    private static final Painter BACKGROUND = new Painter();

    @Override
    public void paint(Graphics g, int p0, int p1, Shape shape, JTextComponent component) {
      Color color = component.getBackground();
      if (color != null) {
        g.setColor(color);
        Rectangle bounds = shape instanceof Rectangle ? (Rectangle)shape : shape.getBounds();
        g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
      }
    }

    @Override
    public void propertyChange(PropertyChangeEvent event) {
      Object source = event.getSource();
      if ((source instanceof JTextComponent) && PROPERTY.equals(event.getPropertyName())) {
        install((JTextComponent)source, false);
      }
    }

    private void install(JTextComponent component, boolean listener) {
      try {
        Highlighter highlighter = component.getHighlighter();
        if (highlighter != null) highlighter.addHighlight(0, 0, this);
      }
      catch (BadLocationException ignored) {
      }
      if (listener) {
        component.addPropertyChangeListener(PROPERTY, this);
      }
    }
  }
}
