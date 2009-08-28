package com.intellij.openapi.ui;

import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class CheckBoxWithDescription extends JPanel {

  private JCheckBox myCheckBox;

  public CheckBoxWithDescription(JCheckBox box, @Nullable String description) {
    myCheckBox = box;

    setLayout(new BorderLayout());
    add(myCheckBox, BorderLayout.NORTH);

    if (description != null) {
      final int iconSize = box.getPreferredSize().height;

      final DescriptionLabel desc = new DescriptionLabel(description);
      desc.setBorder(new EmptyBorder(0, iconSize + UIManager.getInt("CheckBox.textIconGap"), 0, 0));
      add(desc, BorderLayout.CENTER);
    }
  }

  public JCheckBox getCheckBox() {
    return myCheckBox;
  }
}