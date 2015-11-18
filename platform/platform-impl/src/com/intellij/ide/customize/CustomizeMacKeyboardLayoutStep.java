/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

    JRadioButton macRadioButton = new JRadioButton("I've never used " + ApplicationNamesInfo.getInstance().getProductName());
    macRadioButton.setOpaque(false);
    JPanel macPanel = createBigButtonPanel(new VerticalFlowLayout(), macRadioButton, new Runnable() {
      @Override
      public void run() {
        WelcomeWizardUtil.setWizardKeymap(KeymapManager.MAC_OS_X_10_5_PLUS_KEYMAP);
      }
    });
    macPanel.add(macRadioButton);
    macPanel.add(new JLabel("<html><head>" + style + "</head><body><h3>" + KeymapManager.MAC_OS_X_10_5_PLUS_KEYMAP + " keymap</h3>" +
                            "Adapted for OS X<br><br><table><tr><td align=\"left\" colspan=\"2\">EXAMPLES</td></tr>" +
                            "<tr><td style=\"text-align:right;\">&#8984;N</td><td style=\"text-align:left;\">Generate</td></tr>" +
                            "<tr><td style=\"text-align:right;\">&#8984;O</td><td style=\"text-align:left;\">Go to class</td></tr>" +
                            "<tr><td style=\"text-align:right;\">&#8984;&#9003;</td><td style=\"text-align:left;\">Delete line</td></tr>" +
                            "</table></body></html>"));
    add(macPanel);

    JRadioButton defaultRadioButton = new JRadioButton("I used " + ApplicationNamesInfo.getInstance().getProductName() + " before");
    defaultRadioButton.setOpaque(false);
    JPanel defaultPanel = createBigButtonPanel(new VerticalFlowLayout(), defaultRadioButton, new Runnable() {
      @Override
      public void run() {
        WelcomeWizardUtil.setWizardKeymap(KeymapManager.MAC_OS_X_KEYMAP);
      }
    });
    defaultPanel.add(defaultRadioButton);
    defaultPanel.add(new JLabel("<html><head>" + style + "</head><body><h3>" + KeymapManager.MAC_OS_X_KEYMAP + " keymap</h3>" +
                                "Default for all platforms<br><br><table><tr><td align=\"left\" colspan=\"2\">EXAMPLES</td></tr>" +
                                "<tr><td style=\"text-align:right;\">^N</td><td style=\"text-align:left;\">Generate</td></tr>" +
                                "<tr><td style=\"text-align:right;\">&#8984;N</td><td style=\"text-align:left;\">Go to class</td></tr>" +
                                "<tr><td style=\"text-align:right;\">&#8984;Y</td><td style=\"text-align:left;\">Delete line</td></tr>" +
                                "</table></body></html>"));
    add(defaultPanel);

    ButtonGroup group = new ButtonGroup();
    group.add(macRadioButton);
    group.add(defaultRadioButton);
    macRadioButton.setSelected(true);
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
    return "Keymap scheme can be changed later in " + CommonBundle.settingsTitle() + " | Keymap";
  }
}