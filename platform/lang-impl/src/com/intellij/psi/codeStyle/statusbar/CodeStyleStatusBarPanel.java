// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle.statusbar;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.impl.status.TextPanel;
import com.intellij.ui.ExperimentalUI;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

final class CodeStyleStatusBarPanel extends JPanel {
  private final TextPanel myLabel;
  private final JLabel myIconLabel;

  CodeStyleStatusBarPanel() {
    super();

    setOpaque(false);
    setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));
    setAlignmentY(Component.CENTER_ALIGNMENT);
    myLabel = new TextPanel() {};
    myLabel.setFont(SystemInfo.isMac ? JBUI.Fonts.label(11) : JBFont.label());
    myLabel.recomputeSize();
    add(myLabel);
    myIconLabel = new JLabel("");

    if (!ExperimentalUI.isNewUI()) {
      myIconLabel.setBorder(JBUI.Borders.empty(2, 2, 2, 0));
    }

    add(myIconLabel);
  }

  public void setText(@NotNull @Nls String text) {
    myLabel.setText(text);
  }

  @Nullable
  public String getText() {
    return myLabel.getText();
  }

  public void setIcon(@Nullable Icon icon) {
    myIconLabel.setIcon(icon);
    myIconLabel.setVisible(icon != null);
  }
}
