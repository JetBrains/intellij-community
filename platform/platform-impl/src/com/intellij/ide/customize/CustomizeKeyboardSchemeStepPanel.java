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
package com.intellij.ide.customize;

import com.intellij.CommonBundle;
import com.intellij.idea.StartupUtil;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.ui.VerticalFlowLayout;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class CustomizeKeyboardSchemeStepPanel extends AbstractCustomizeWizardStep {

  private final JToggleButton myMacButton;
  private final JToggleButton myDefaultButton;

  public CustomizeKeyboardSchemeStepPanel() {//&#8997; alt
    setLayout(new GridLayout(1, 2, GAP, GAP));
    myMacButton = new JToggleButton();
    myMacButton.setLayout(new VerticalFlowLayout());
    final JRadioButton macRadioButton =
      new JRadioButton("I've never used " + ApplicationNamesInfo.getInstance().getProductName() + " or am a Mac user");
    myMacButton.add(macRadioButton);
    myMacButton.add(new JLabel("<html><body><h3>" + KeymapManager.MAC_OS_X_10_5_PLUS_KEYMAP + " keymap</h3>" +
                               "Adapted for Mac<br><br><table><tr><td colspan=\"2\">EXAMPLES</td></tr>" +
                               "<tr><td style=\"text-align:right;\">&#8984;N</td><td style=\"text-align:left;\">Generate</td></tr>" +
                               "<tr><td style=\"text-align:right;\">&#8984;O</td><td style=\"text-align:left;\">Go to class</td></tr>" +
                               "<tr><td style=\"text-align:right;\">&#8984;&#9003;</td><td style=\"text-align:left;\">Delete line</td></tr>" +
                               "</table></body></html>"
    ));

    add(myMacButton);
    myDefaultButton = new JToggleButton();
    myDefaultButton.setLayout(new VerticalFlowLayout());
    final JRadioButton defaultRadioButton =
      new JRadioButton("I used " + ApplicationNamesInfo.getInstance().getProductName() + " before or am a Windows user");
    myDefaultButton.add(defaultRadioButton);
    myDefaultButton.add(new JLabel("<html><body><h3>" + KeymapManager.MAC_OS_X_KEYMAP + " keymap</h3>" +
                                   "Default for all platforms<br><br><table><tr><td colspan=\"2\">EXAMPLES</td></tr>" +
                                   "<tr><td style=\"text-align:right;\">^N</td><td style=\"text-align:left;\">Generate</td></tr>" +
                                   "<tr><td style=\"text-align:right;\">&#8984;N</td><td style=\"text-align:left;\">Go to class</td></tr>" +
                                   "<tr><td style=\"text-align:right;\">&#8984;Y</td><td style=\"text-align:left;\">Delete line</td></tr>" +
                                   "</table></body></html>"
    ));

    add(myMacButton);
    add(myDefaultButton);
    ButtonGroup group = new ButtonGroup();
    ButtonGroup subGroup = new ButtonGroup();
    group.add(myMacButton);
    group.add(myDefaultButton);
    myMacButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        macRadioButton.setSelected(myMacButton.isSelected());
        StartupUtil.setMyWizardMacKeymap(KeymapManager.MAC_OS_X_10_5_PLUS_KEYMAP);
      }
    });
    myDefaultButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        defaultRadioButton.setSelected(myDefaultButton.isSelected());
        StartupUtil.setMyWizardMacKeymap(KeymapManager.MAC_OS_X_KEYMAP);
      }
    });
    subGroup.add(macRadioButton);
    subGroup.add(defaultRadioButton);
    myDefaultButton.setSelected(true);
    defaultRadioButton.setSelected(true);
  }

  @Override
  Component getDefaultFocusedComponent() {
    return myMacButton.isSelected() ? myMacButton : myDefaultButton;
  }

  @Override
  public String getTitle() {
    return "Keymaps";
  }

  @Override
  public String getHTMLHeader() {
    return "<html><body><h2>Select keymap scheme</h2>&nbsp;</body></html>";
  }

  @Override
  public String getHTMLFooter() {
    return "Keymap scheme can be later changed in " + CommonBundle.settingsTitle() + " | Keymap";
  }
}
