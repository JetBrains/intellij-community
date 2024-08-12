// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tabs;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.ColorChooserService;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.FileColorManager;
import com.intellij.ui.JBColor;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.picker.ColorListener;
import com.intellij.util.ui.StartupUiUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.ButtonUI;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @author gregsh
 */
public final class ColorSelectionComponent extends JPanel {
  private final Map<String, ColorButton> myColorToButtonMap = new LinkedHashMap<>();
  private final ButtonGroup myButtonGroup = new ButtonGroup();

  private static final String CUSTOM_COLOR_ID = "Custom";

  public ColorSelectionComponent() {
    super(new GridLayout(1, 0, 5, 5));
    setOpaque(false);
  }

  public void setSelectedColor(@NlsContexts.Button String colorName) {
    AbstractButton button = myColorToButtonMap.get(colorName);
    if (button != null) {
      button.setSelected(true);
    }
  }

  private void addCustomColorButton() {
    CustomColorButton customButton = new CustomColorButton();
    myButtonGroup.add(customButton);
    add(customButton);
    myColorToButtonMap.put(CUSTOM_COLOR_ID, customButton);
  }

  private void addColorButton(@NotNull @NonNls String colorID, @NotNull @NlsContexts.Button String name, @NotNull Color color) {
    ColorButton colorButton = new ColorButton(name, color);
    myButtonGroup.add(colorButton);
    add(colorButton);
    myColorToButtonMap.put(colorID, colorButton);
  }

  public void setCustomButtonColor(@NotNull Color color) {
    CustomColorButton button = (CustomColorButton)myColorToButtonMap.get(CUSTOM_COLOR_ID);
    button.setColor(color);
    button.setSelected(true);
    button.repaint();
  }

  public @Nullable @NonNls String getSelectedColorName() {
    for (String name : myColorToButtonMap.keySet()) {
      ColorButton button = myColorToButtonMap.get(name);
      if (!button.isSelected()) continue;
      if (button instanceof CustomColorButton) {
        String colorHexString = ColorUtil.toHex(button.getColor());
        String colorID  = FileColorManagerImpl.getColorID(button.getColor());
        return colorID == null ? colorHexString : colorID;
      }
      return name;
    }
    return null;
  }

  public void initDefault(@NotNull FileColorManager manager, @Nullable @NlsContexts.Button String selectedColorName) {
    for (String id : manager.getColorIDs()) {
      addColorButton(id, manager.getColorName(id), Objects.requireNonNull(manager.getColor(id)));
    }
    addCustomColorButton();
    setSelectedColor(selectedColorName);
  }

  private static class ColorButton extends ColorButtonBase {
    protected ColorButton(@NlsContexts.Button String text, Color color) {
      super(text, color);
    }

    @Override
    protected void doPerformAction(ActionEvent e) {}
  }

  private static final class CustomColorButton extends ColorButton {
    private CustomColorButton() {
      super(IdeBundle.message("settings.file.color.custom.name"), JBColor.WHITE);
      myColor = null;
    }

    @Override
    protected void doPerformAction(ActionEvent e) {
      RelativePoint location = new RelativePoint(this, new Point(getWidth() / 2, getHeight()));
      ColorListener listener = new ColorListener() {
        @Override
        public void colorChanged(Color color, Object source) {
          if (color != null) {
            myColor = color;
          }
          setSelected(myColor != null);
          CustomColorButton.this.revalidate();
          CustomColorButton.this.repaint();
        }
      };
      ColorChooserService.getInstance().showPopup(null, myColor, listener, location);
    }

    @Override
    protected ButtonUI createUI() {
      return new ColorButtonUI() {
        @Override
        protected @Nullable Color getUnfocusedBorderColor(@NotNull ColorButtonBase button) {
          if (StartupUiUtil.isUnderDarcula()) return JBColor.GRAY;
          return super.getUnfocusedBorderColor(button);
        }
      };
    }

    @Override
    public Color getForeground() {
      return getModel().isSelected() ? JBColor.BLACK : JBColor.GRAY;
    }

    @NotNull
    @Override
    Color getColor() {
      return myColor == null ? JBColor.WHITE : myColor;
    }
  }
}
