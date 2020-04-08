// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.customize;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.WelcomeWizardUtil;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.ui.VerticalFlowLayout;

import javax.swing.*;
import java.awt.*;

public class CustomizeMacKeyboardLayoutStep extends AbstractCustomizeWizardStep {
  public CustomizeMacKeyboardLayoutStep() {
    setLayout(new GridLayout(1, 2, GAP, GAP));

    String style = "<style type=\"text/css\">" +
                   "body {margin-left:" + GAP + "px; border:none;padding:0px;}" +
                   "table {margin:0px; cell-padding:0px; border:none;}" +
                   "</style>";

    JRadioButton macRadioButton = new JRadioButton(
      IdeBundle.message("radio.button.i.ve.never.used.0", ApplicationNamesInfo.getInstance().getFullProductName()));
    macRadioButton.setOpaque(false);
    JPanel macPanel = createBigButtonPanel(new VerticalFlowLayout(), macRadioButton,
                                           () -> WelcomeWizardUtil.setWizardKeymap(KeymapManager.MAC_OS_X_10_5_PLUS_KEYMAP));
    macPanel.add(macRadioButton);
    macPanel.add(new JLabel(IdeBundle.message("label.text.mac.os.x.keymap.examples", style, KeymapManager.MAC_OS_X_10_5_PLUS_KEYMAP)));
    add(macPanel);

    JRadioButton defaultRadioButton = new JRadioButton(
      IdeBundle.message("radio.button.i.used.0.before", ApplicationNamesInfo.getInstance().getFullProductName()));
    defaultRadioButton.setOpaque(false);
    JPanel defaultPanel = createBigButtonPanel(new VerticalFlowLayout(), defaultRadioButton,
                                               () -> WelcomeWizardUtil.setWizardKeymap(KeymapManager.MAC_OS_X_KEYMAP));
    defaultPanel.add(defaultRadioButton);
    defaultPanel.add(new JLabel(IdeBundle.message("label.mac.os.default.keymap.examples", style, KeymapManager.MAC_OS_X_KEYMAP)));
    add(defaultPanel);

    ButtonGroup group = new ButtonGroup();
    group.add(macRadioButton);
    group.add(defaultRadioButton);
    macRadioButton.setSelected(true);
  }

  @Override
  public String getTitle() {
    return IdeBundle.message("step.title.keymaps");
  }

  @Override
  public String getHTMLHeader() {
    return IdeBundle.message("label.select.keymap.scheme");
  }

  @Override
  public String getHTMLFooter() {
    return IdeBundle.message("label.keymap.scheme.can.be.changed.later.in.0.keymap", CommonBundle.settingsTitle());
  }
}