// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle.statusbar;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.impl.status.StatusBarAccessibilityUtil;
import com.intellij.openapi.wm.impl.status.TextPanel;
import com.intellij.ui.ExperimentalUI;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.AccessibleAction;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Component;

final class CodeStyleStatusBarPanel extends JPanel {
  private final TextPanel myLabel;
  private final JLabel myIconLabel;

  CodeStyleStatusBarPanel() {
    super();

    setOpaque(false);
    setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));
    setAlignmentY(Component.CENTER_ALIGNMENT);

    myIconLabel = new JLabel("");
    if (!ExperimentalUI.isNewUI()) {
      myIconLabel.setBorder(JBUI.Borders.empty(2, 2, 2, 0));
    }
    add(myIconLabel);

    add(Box.createHorizontalStrut(4));

    myLabel = new TextPanel() {};
    myLabel.setFont(SystemInfo.isMac ? JBUI.Fonts.label(11) : JBFont.label());
    myLabel.recomputeSize();
    add(myLabel);
  }

  public void setText(@NotNull @Nls String text) {
    myLabel.setText(text);
  }

  public @Nullable @Nls String getText() {
    return myLabel.getText();
  }

  public void setIcon(@Nullable Icon icon) {
    myIconLabel.setIcon(icon);
    myIconLabel.setVisible(icon != null);
  }

  @Override
  public AccessibleContext getAccessibleContext() {
    if (accessibleContext == null) {
      accessibleContext = new AccessibleCodeStyleStatusBarPanel();
    }
    return accessibleContext;
  }

  private final class AccessibleCodeStyleStatusBarPanel extends AccessibleJPanel {
    private final AccessibleAction myAccessibleAction = StatusBarAccessibilityUtil.createAccessibleAction(CodeStyleStatusBarPanel.this);

    @Override
    public AccessibleRole getAccessibleRole() {
      return AccessibleRole.PUSH_BUTTON;
    }

    @Override
    public @Nls String getAccessibleName() {
      return StatusBarAccessibilityUtil.getTextOrTooltipAccessibleName(CodeStyleStatusBarPanel.this, getText());
    }

    @Override
    public @Nls String getAccessibleDescription() {
      return StatusBarAccessibilityUtil.getTooltipAccessibleDescription(CodeStyleStatusBarPanel.this, getText());
    }

    @Override
    public AccessibleAction getAccessibleAction() {
      return myAccessibleAction;
    }
  }
}
