/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ui.tabs;

import com.intellij.notification.impl.ui.StickyButton;
import com.intellij.notification.impl.ui.StickyButtonUI;
import com.intellij.ui.ColorChooser;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.FileColorManager;
import com.intellij.ui.JBColor;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.plaf.ButtonUI;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author gregsh
 */
public class ColorSelectionComponent extends JPanel {
  private static final String CUSTOM_COLOR_NAME = "Custom";
  private Map<String, ColorButton> myColorToButtonMap = new LinkedHashMap<>();
  private final ButtonGroup myButtonGroup = new ButtonGroup();
  private ChangeListener myChangeListener;

  public ColorSelectionComponent() {
    super(new GridLayout(1, 0, 5, 5));
    setOpaque(false);
  }

  public void setChangeListener(ChangeListener changeListener) {
    myChangeListener = changeListener;
  }

  public void setSelectedColor(String colorName) {
    AbstractButton button = myColorToButtonMap.get(colorName);
    if (button != null) {
      button.setSelected(true);
    }
  }

  @NotNull
  public Collection<String> getColorNames() {
    return myColorToButtonMap.keySet();
  }

  @Nullable
  public String getColorName(@Nullable Color color) {
    if (color == null) return null;
    for (String name : myColorToButtonMap.keySet()) {
      if (color.getRGB() == myColorToButtonMap.get(name).getColor().getRGB()) {
        return name;
      }
    }
    return null;
  }

  public void addCustomColorButton() {
    CustomColorButton customButton = new CustomColorButton();
    myButtonGroup.add(customButton);
    add(customButton);
    myColorToButtonMap.put(customButton.getText(), customButton);
  }

  public void addColorButton(@NotNull String name, @NotNull Color color) {
    ColorButton colorButton = new ColorButton(name, color);
    myButtonGroup.add(colorButton);
    add(colorButton);
    myColorToButtonMap.put(name, colorButton);
  }

  public void setCustomButtonColor(@NotNull Color color) {
    CustomColorButton button = (CustomColorButton)myColorToButtonMap.get(CUSTOM_COLOR_NAME);
    button.setColor(color);
    button.setSelected(true);
    button.repaint();
  }

  @Nullable
  public String getSelectedColorName() {
    for (String name : myColorToButtonMap.keySet()) {
      ColorButton button = myColorToButtonMap.get(name);
      if (!button.isSelected()) continue;
      if (button instanceof CustomColorButton) {
        final String color = ColorUtil.toHex(button.getColor());
        String colorName  = findColorName(button.getColor());
        return colorName == null ? color : colorName;
      }
      return name;
    }
    return null;
  }

  @Nullable
  public static String findColorName(Color color) {
    final String hex = ColorUtil.toHex(color);
    if ("ffffe4".equals(hex) || "494539".equals(hex)) {
      return "Yellow";
    }

    if ("e7fadb".equals(hex) || "2a3b2c".equals(hex)) {
      return "Green";
    }

    return null;
  }

  @Nullable
  public Color getSelectedColor() {
    for (String name : myColorToButtonMap.keySet()) {
      ColorButton button = myColorToButtonMap.get(name);
      if (!button.isSelected()) continue;
      return button.getColor();
    }
    return null;
  }

  public void initDefault(@NotNull FileColorManager manager, @Nullable String selectedColorName) {
    for (String name : manager.getColorNames()) {
      addColorButton(name, ObjectUtils.assertNotNull(manager.getColor(name)));
    }
    addCustomColorButton();
    setSelectedColor(selectedColorName);
  }

  private class ColorButton extends StickyButton {
    protected Color myColor;

    protected ColorButton(final String text, final Color color) {
      super(FileColorManagerImpl.getAlias(text));
      setUI(new ColorButtonUI());
      myColor = color;
      addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          doPerformAction(e);
        }
      });

      setOpaque(false);
      setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
    }

    protected void doPerformAction(ActionEvent e) {
      stateChanged();
    }

    Color getColor() {
      return myColor;
    }

    public void setColor(Color color) {
      myColor = color;
    }

    @Override
    public Color getForeground() {
      if (getModel().isSelected()) {
        return JBColor.foreground();
      }
      else if (getModel().isRollover()) {
        return JBColor.GRAY;
      }
      else {
        return getColor();
      }
    }

    @Override
    protected ButtonUI createUI() {
      return new ColorButtonUI();
    }
  }

  public void stateChanged() {
    if (myChangeListener != null) {
      myChangeListener.stateChanged(new ChangeEvent(this));
    }
  }

  private class CustomColorButton extends ColorButton {
    private CustomColorButton() {
      super(CUSTOM_COLOR_NAME, Color.WHITE);
      myColor = null;
    }

    @Override
    protected void doPerformAction(ActionEvent e) {
      final Color color = ColorChooser.chooseColor(this, "Choose Color", myColor);
      if (color != null) {
        myColor = color;
      }
      setSelected(myColor != null);
      stateChanged();
    }

    @Override
    public Color getForeground() {
      return getModel().isSelected() ? Color.BLACK : JBColor.GRAY;
    }

    @Override
    Color getColor() {
      return myColor == null ? Color.WHITE : myColor;
    }
  }

  private static class ColorButtonUI extends StickyButtonUI<ColorButton> {

    @Override
    protected Color getBackgroundColor(final ColorButton button) {
      return button.getColor();
    }

    @Override
    protected Color getFocusColor(ColorButton button) {
      return button.getColor().darker();
    }

    @Override
    protected Color getSelectionColor(ColorButton button) {
      return button.getColor();
    }

    @Override
    protected Color getRolloverColor(ColorButton button) {
      return button.getColor();
    }

    @Override
    protected int getArcSize() {
      return 20;
    }
  }
}
