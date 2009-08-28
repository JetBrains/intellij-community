package com.intellij.openapi.ui;

import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class DescriptionLabel extends JLabel {

  public DescriptionLabel(@Nullable String text) {
    setText(text);
  }

  @Override
  public void updateUI() {
    super.updateUI();
    setForeground(UIManager.getColor("Panel.background").darker());
    int size = getFont().getSize();
    if (size >= 12) {
      size -= 2;
    }
    setFont(getFont().deriveFont((float)size));
  }
}