/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class ColorPanel extends JPanel {
  public static final Color DISABLED_COLOR = UIUtil.getPanelBackground();
  private boolean isFiringEvent = false;
  private ColorBox myFgSelectedColorBox;
  private boolean isEditable = true;

  public ColorPanel() {
    this(10);
  }

  public ColorPanel(int boxSize) {
    myFgSelectedColorBox = new ColorBox(null, (boxSize + 2) * 2, true);
    myFgSelectedColorBox.setSelectColorAction(
      new Runnable() {
        public void run() {
          fireActionEvent();
        }
      }
    );

    JPanel selectedColorPanel = new JPanel(new GridBagLayout());
    selectedColorPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
    myFgSelectedColorBox.setBorder(BorderFactory.createEtchedBorder());
    selectedColorPanel.add(myFgSelectedColorBox, new GridBagConstraints());

    setLayout(new BorderLayout());
    add(selectedColorPanel, BorderLayout.WEST);
  }

  public void setEnabled(boolean enabled) {
    myFgSelectedColorBox.setEnabled(enabled);
    super.setEnabled(enabled);
    repaint();
  }

  private void fireActionEvent() {
    if (!isEditable) return;
    if (!isFiringEvent){
      isFiringEvent = true;
      ActionEvent actionevent = null;
      Object[] listeners = listenerList.getListenerList();
      for(int i = listeners.length - 2; i >= 0; i -= 2){
        if (listeners[i] != ActionListener.class) continue;
        if (actionevent == null){
          actionevent = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "colorPanelChanged");
        }
        ((ActionListener)listeners[i + 1]).actionPerformed(actionevent);
      }
      isFiringEvent = false;
    }
  }

  public void removeActionListener(ActionListener actionlistener) {
    listenerList.remove(ActionListener.class, actionlistener);
  }

  public void addActionListener(ActionListener actionlistener) {
    listenerList.add(ActionListener.class, actionlistener);
  }

  public Color getSelectedColor() {
    return myFgSelectedColorBox.getColor();
  }

  public void setSelectedColor(Color color) {
    myFgSelectedColorBox.setColor(color);
  }

  public void setEditable(boolean isEditable) {
    this.isEditable = isEditable;
    myFgSelectedColorBox.setSelectable(isEditable);
  }

  private class ColorBox extends JComponent {
    private final Dimension mySize;
    private boolean isSelectable;
    private Runnable mySelectColorAction = null;
    private Color myColor;
    @NonNls public static final String RGB = "RGB";

    public ColorBox(Color color, int size, boolean isSelectable) {
      mySize = new Dimension(size, size);
      this.isSelectable = isSelectable;
      myColor = color;
      updateToolTip();
      //TODO[anton,vova] investigate
      addMouseListener(new MouseAdapter(){
        public void mouseReleased(MouseEvent mouseevent) {
          if (!isEnabled()){
            return;
          }
          if (mouseevent.isPopupTrigger()){
            selectColor();
          }
        }

        public void mousePressed(MouseEvent mouseevent) {
          if (!isEnabled()){
            return;
          }
          if (mouseevent.getClickCount() == 2){
            selectColor();
          }
          else{
            if (SwingUtilities.isLeftMouseButton(mouseevent)){
              setSelectedColor(myColor);
              fireActionEvent();
            }
            else{
              if (mouseevent.isPopupTrigger()){
                selectColor();
              }
            }
          }
        }
      });
    }

    public void setSelectColorAction(Runnable selectColorAction) {
      mySelectColorAction = selectColorAction;
    }

    private void selectColor() {
      if (isSelectable){
        Color color = ColorChooser.chooseColor(ColorPanel.this, UIBundle.message("color.panel.select.color.dialog.description"), myColor);
        if (color != null){
          setColor(color);
          if (mySelectColorAction != null){
            mySelectColorAction.run();
          }
        }
      }
    }

    public Dimension getMinimumSize() {
      return mySize;
    }

    public Dimension getMaximumSize() {
      return mySize;
    }

    public Dimension getPreferredSize() {
      return mySize;
    }

    public void paintComponent(Graphics g) {
      if (isEnabled()){
        g.setColor(myColor);
      }
      else{
        g.setColor(DISABLED_COLOR);
      }
      g.fillRect(0, 0, getWidth(), getHeight());
    }

    private void updateToolTip() {
      if (myColor == null){
        return;
      }
      StringBuilder buffer = new StringBuilder(64);
      buffer.append(RGB + ": ");
      buffer.append(myColor.getRed());
      buffer.append(", ");
      buffer.append(myColor.getGreen());
      buffer.append(", ");
      buffer.append(myColor.getBlue());

      if (isSelectable) {
        buffer.append(" (" + UIBundle.message("color.panel.right.click.to.customize.tooltip.suffix") + ")");
      }
      setToolTipText(buffer.toString());
    }

    public void setColor(Color color) {
      myColor = color;
      updateToolTip();
      repaint();
    }

    public Color getColor() {
      return myColor;
    }

    private void setSelectable(boolean selectable) {
      isSelectable = selectable;
    }
  }
}
