package com.intellij.ui.tabs;

import com.intellij.notification.impl.ui.StickyButton;
import com.intellij.notification.impl.ui.StickyButtonUI;
import com.intellij.ui.ColorChooser;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.FileColorManager;
import com.intellij.ui.JBColor;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.UIUtil;
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
  private Map<String, ColorButton> myColorToButtonMap = new LinkedHashMap<String, ColorButton>();
  private final ButtonGroup myButtonGroup = new ButtonGroup();
  private final JPanel myInnerPanel;
  private ChangeListener myChangeListener;

  public ColorSelectionComponent() {
    setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

    myInnerPanel = new JPanel();
    myInnerPanel.setLayout(new BoxLayout(myInnerPanel, BoxLayout.X_AXIS));
    myInnerPanel.setBorder(
      BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1), BorderFactory.createEmptyBorder(5, 5, 5, 5)));
    if (!UIUtil.isUnderDarcula()) {
      myInnerPanel.setBackground(Color.WHITE);
    }
    add(myInnerPanel, BorderLayout.CENTER);
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
    myInnerPanel.add(customButton);
    myColorToButtonMap.put(customButton.getText(), customButton);
    myInnerPanel.add(Box.createHorizontalStrut(5));
  }

  public void addColorButton(@NotNull String name, @NotNull Color color) {
    ColorButton colorButton = new ColorButton(name, color);
    myButtonGroup.add(colorButton);
    myInnerPanel.add(colorButton);
    myColorToButtonMap.put(name, colorButton);
    myInnerPanel.add(Box.createHorizontalStrut(5));
  }

  public void setCustomButtonColor(@NotNull Color color) {
    CustomColorButton button = (CustomColorButton)myColorToButtonMap.get(CUSTOM_COLOR_NAME);
    button.setColor(color);
    button.setSelected(true);
    button.repaint();
  }

  @Nullable
  private ColorButton getSelectedButtonInner() {
    for (String name : myColorToButtonMap.keySet()) {
      ColorButton button = myColorToButtonMap.get(name);
      if (button.isSelected()) return button;
    }
    return null;
  }

  @Nullable
  public String getSelectedColorName() {
    ColorButton button = getSelectedButtonInner();
    return button == null? null : button instanceof CustomColorButton ? ColorUtil.toHex(button.getColor()) : button.getText();
  }

  @Nullable
  public Color getSelectedColor() {
    ColorButton button = getSelectedButtonInner();
    return button == null? null : button.getColor();
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

      setBackground(new JBColor(Color.WHITE, UIUtil.getControlColor()));
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

  private class ColorButtonUI extends StickyButtonUI<ColorButton> {

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
