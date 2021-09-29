// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.codeStyle.excludedFiles;

import com.intellij.icons.AllIcons;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

class ExcludedFilesPanelBase extends JPanel {

  protected JComponent createWarningMessage(@Nls @NotNull String text) {
    JPanel warningPanel = new JPanel();
    warningPanel.setLayout(new BoxLayout(warningPanel, BoxLayout.LINE_AXIS));
    JLabel warnIcon = new JLabel(AllIcons.General.Warning);
    warnIcon.setAlignmentY(Component.TOP_ALIGNMENT);
    warnIcon.setBorder(JBUI.Borders.emptyRight(5));
    warningPanel.add(warnIcon);
    JLabel textLabel = new JLabel(text);
    textLabel.setAlignmentY(Component.TOP_ALIGNMENT);
    textLabel.setFont(JBUI.Fonts.smallFont());
    warningPanel.add(textLabel);
    return warningPanel;
  }
}
