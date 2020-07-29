// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ui;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

public class TitledSeparator extends JPanel {
  public static final int TOP_INSET = 7;
  public static final int BOTTOM_INSET = 5;
  public static final int SEPARATOR_LEFT_INSET = 6;
  public static final int SEPARATOR_RIGHT_INSET = 0;

  private static final Color ENABLED_SEPARATOR_FOREGROUND = JBColor.namedColor("Group.separatorColor", new JBColor(Gray.xCD, Gray.x51));
  private static final Color DISABLED_SEPARATOR_FOREGROUND = JBColor.namedColor("Group.disabledSeparatorColor", ENABLED_SEPARATOR_FOREGROUND);

  @NotNull
  public static Border createEmptyBorder() {
    return JBUI.Borders.empty(TOP_INSET, 0, BOTTOM_INSET, 0);
  }

  protected final JBLabel myLabel = new JBLabel();
  protected final JSeparator mySeparator = new JSeparator(SwingConstants.HORIZONTAL);
  private String originalText;

  public TitledSeparator() {
    this("");
  }

  public TitledSeparator(@NlsContexts.Separator String text) {
    this(text, null);
  }

  public TitledSeparator(@NlsContexts.Separator String text, @Nullable JComponent labelFor) {
    mySeparator.setForeground(ENABLED_SEPARATOR_FOREGROUND);

    setLayout(new GridBagLayout());
    add(myLabel, new GridBagConstraints(0, 0, 1, 1, 0, 1, GridBagConstraints.WEST, GridBagConstraints.NONE, JBUI.emptyInsets(), 0, 0));
    add(mySeparator,
        new GridBagConstraints(1, 0, GridBagConstraints.REMAINDER, 1, 1, 1, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL,
                               JBUI.insets(2, SEPARATOR_LEFT_INSET, 0, SEPARATOR_RIGHT_INSET), 0, 0));
    setBorder(createEmptyBorder());
    setText(text);
    setLabelFor(labelFor);
    setOpaque(false);
  }

  public String getText() {
    return originalText;
  }

  public void setText(@NlsContexts.Separator String text) {
    originalText = text;
    myLabel.setText(text != null && text.startsWith("<html>") ? text : UIUtil.replaceMnemonicAmpersand(originalText));
  }
  public void setTitleFont(Font font) {
    myLabel.setFont(font);
  }

  public Font getTitleFont() {
    return myLabel.getFont();
  }

  public JLabel getLabel() {
    return myLabel;
  }

  public JSeparator getSeparator() {
    return mySeparator;
  }


  public Component getLabelFor() {
    return myLabel.getLabelFor();
  }

  public void setLabelFor(Component labelFor) {
    myLabel.setLabelFor(labelFor);
  }

  @Override
  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    myLabel.setEnabled(enabled);
    mySeparator.setEnabled(enabled);

    mySeparator.setForeground(enabled ? ENABLED_SEPARATOR_FOREGROUND : DISABLED_SEPARATOR_FOREGROUND);
  }
}
