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

import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import com.intellij.util.ui.UIUtil;

public class ColorPanel extends JPanel {
  public static final Color[] fixedColors;
  public static final Color DARK_MAGENTA = new Color(128, 0, 128);
  public static final Color DARK_BLUE = new Color(0, 0, 128);
  public static final Color DARK_GREEN = new Color(0, 128, 0);
  public static final Color BLUE_GREEN = new Color(0, 128, 128);
  public static final Color DARK_YELLOW = new Color(128, 128, 0);
  public static final Color DARK_RED = new Color(128, 0, 0);
  public static final Color DISABLED_COLOR = UIUtil.getPanelBackgound();
  private static Color[] myCustomColors;
  @NonNls private String myActionCommand = "colorPanelChanged";
  private boolean isFiringEvent = false;
  private int myBoxSize;
  private GridLayout myCustomGridLayout;
  private GridLayout myFixedGridLayout;
  private ColorBox[] myFgFixedColorBoxes;
  private ColorBox[] myFgCustomColorBoxes;
  private ColorBox myFgSelectedColorBox;

  static {
    fixedColors = new Color[]{Color.white, Color.lightGray, Color.red, Color.yellow, Color.green, Color.cyan, Color.blue, Color.magenta,
                              Color.black, Color.gray, DARK_RED, DARK_YELLOW, DARK_GREEN, BLUE_GREEN, DARK_BLUE, DARK_MAGENTA};
  }

  public ColorPanel() {
    this(null, 10);
  }

  public ColorPanel(Color[] colors, int boxSize) {
    myBoxSize = boxSize;
    myFgSelectedColorBox = new ColorBox(null, (myBoxSize + 2) * 2, true);
    myFgSelectedColorBox.setSelectColorAction(
      new Runnable() {
        public void run() {
          fireActionEvent();
        }
      }
    );

    myFgFixedColorBoxes = new ColorBox[16];
    myFixedGridLayout = new GridLayout(2, 0, 2, 2);
    myCustomGridLayout = new GridLayout(2, 0, 2, 2);
    myFgCustomColorBoxes = new ColorBox[8];
    if (colors == null){
      if (myCustomColors == null){
        myCustomColors =
          new Color[]{
            Color.lightGray, Color.lightGray, Color.lightGray, Color.lightGray, Color.lightGray, Color.lightGray, Color.lightGray, Color.lightGray
            };
      }
    }
    else{
      myCustomColors = colors;
    }

    JPanel selectedColorPanel = new JPanel(new GridBagLayout());
    selectedColorPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
    myFgSelectedColorBox.setBorder(BorderFactory.createEtchedBorder());
    selectedColorPanel.add(myFgSelectedColorBox, new GridBagConstraints());

    initializeColorBoxes();
    JPanel fixedColorsPanel = new JPanel(myFixedGridLayout);
    for(int i = 0; i < myFgFixedColorBoxes.length; i++){
      JPanel jpanel2 = new JPanel(new BorderLayout());
      jpanel2.setBorder(BorderFactory.createEtchedBorder());
      jpanel2.add(myFgFixedColorBoxes[i], BorderLayout.CENTER);
      fixedColorsPanel.add(jpanel2);
    }

    JPanel customColorsPanel = new JPanel(myCustomGridLayout);
    customColorsPanel.setBorder(BorderFactory.createCompoundBorder(IdeBorderFactory.createBorder(SideBorder.LEFT),
                                                                   BorderFactory.createEmptyBorder(0, 2, 0, 0)));
    for(int k = 0; k < myFgCustomColorBoxes.length; k++){
      JPanel jpanel4 = new JPanel(new BorderLayout());
      jpanel4.setBorder(BorderFactory.createEtchedBorder());
      jpanel4.add(myFgCustomColorBoxes[k], BorderLayout.CENTER);
      customColorsPanel.add(jpanel4);
    }

    JPanel allColorsPanel = new JPanel(new GridBagLayout());
    allColorsPanel.setBorder(BorderFactory.createEtchedBorder());
    allColorsPanel.add(fixedColorsPanel, new GridBagConstraints(1, 0, 1, 1, 0.1D, 0.1D, 10, 0, new Insets(2, 2, 2, 1), 0, 0));
    allColorsPanel.add(customColorsPanel, new GridBagConstraints(2, 0, 1, 1, 0.1D, 0.1D, 17, 0, new Insets(2, 2, 2, 2), 0, 0));
    setLayout(new BorderLayout());
    add(selectedColorPanel, BorderLayout.WEST);
    add(allColorsPanel, BorderLayout.CENTER);
  }

  public void setEnabled(boolean enabled) {
    for(int i = 0; i < myFgFixedColorBoxes.length; i++){
      ColorBox colorBox = myFgFixedColorBoxes[i];
      colorBox.setEnabled(enabled);
    }
    for(int i = 0; i < myFgCustomColorBoxes.length; i++){
      ColorBox colorBox = myFgCustomColorBoxes[i];
      colorBox.setEnabled(enabled);
    }
    myFgSelectedColorBox.setEnabled(enabled);
    super.setEnabled(enabled);
    repaint();
  }

  void updateColorBoxes() {
    for(int i = 0; i < myFgCustomColorBoxes.length; i++){
      myFgCustomColorBoxes[i].setColor(myCustomColors[i]);
    }
  }

  protected void initializeColorBoxes() {
    for(int i = 0; i < myFgFixedColorBoxes.length; i++){
      myFgFixedColorBoxes[i] = new ColorBox(fixedColors[i], myBoxSize, false);
    }
    for(int i = 0; i < myFgCustomColorBoxes.length; i++){
      myFgCustomColorBoxes[i] = new ColorBox(Color.orange, myBoxSize, true);
      final int customColorIndex = i;
      final ColorBox customColorBox = myFgCustomColorBoxes[i];
      myFgCustomColorBoxes[i].setSelectColorAction(
        new Runnable() {
          public void run() {
            myCustomColors[customColorIndex] = customColorBox.getColor();
          }
        }
      );
    }
    updateColorBoxes();
  }

  public void setActionCommand(String actionCommand) {
    myActionCommand = actionCommand;
  }

  public String getActionCommand() {
    return myActionCommand;
  }

  private void fireActionEvent() {
    if (!isFiringEvent){
      isFiringEvent = true;
      ActionEvent actionevent = null;
      Object[] listeners = listenerList.getListenerList();
      for(int i = listeners.length - 2; i >= 0; i -= 2){
        if (listeners[i] != ActionListener.class) continue;
        if (actionevent == null){
          actionevent = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, getActionCommand());
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

  public void setCustomColor(int index, Color color1) {
    myCustomColors[index - myFgFixedColorBoxes.length] = color1;
  }

  public Color getSelectedColor() {
    return myFgSelectedColorBox.getColor();
  }

  public void setSelectedColor(Color color) {
    myFgSelectedColorBox.setColor(color);
  }

  public Color[] getCustomColors() {
    return myCustomColors;
  }

  public void setPanelGridHeight(int i) {
    myFixedGridLayout.setRows(i);
    myCustomGridLayout.setRows(i);
    doLayout();
  }

  public void setCustomColors(Color[] colors) {
    myCustomColors = colors;
    if (myCustomColors == null){
      myCustomColors = fixedColors;
    }
    if (colors != null){
      updateColorBoxes();
    }
  }

  private class ColorBox extends JComponent {
    private final Dimension mySize;
    private final boolean isSelectable;
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
      StringBuffer buffer = new StringBuffer(64);
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
  }
}
