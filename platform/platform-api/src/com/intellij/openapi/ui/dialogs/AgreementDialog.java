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
package com.intellij.openapi.ui.dialogs;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.ScrollPaneFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.MessageFormat;
import java.util.ResourceBundle;

public class AgreementDialog extends DialogWrapper {
  private final Font myFont = new Font("SansSerif", Font.PLAIN, 12);
  private final ResourceBundle myBundle = ResourceBundle.getBundle("messages.LicenseCommonBundle");

  private final String myText;
  private JCheckBox myAcceptCheckBox;
  private boolean myOK = false;

  public AgreementDialog(String text, String programName) {
    super(null, false, true);
    getPeer().setAppIcons();

    myText = text;
    String title;
    if (programName != null) {
      title = MessageFormat.format(myBundle.getString("license.agreement.title.for"), programName);
    }
    else {
      title = myBundle.getString("license.agreement.title");
    }

    setTitle(title);

    init();

    getOKAction().setEnabled(false);
  }

  protected void doOKAction() {
    myOK = true;
    super.doOKAction();
  }

  protected JComponent createNorthPanel() {
    JPanel panel = new JPanel(new BorderLayout());
    String text = myBundle.getString("license.agreement.prompt");
    JLabel licensePrompt = new JLabel(text);
    licensePrompt.setFocusable(false);
    licensePrompt.setFont(myFont);
    licensePrompt.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 5));
    panel.add(licensePrompt, BorderLayout.WEST);
    panel.add(new JPanel(), BorderLayout.CENTER);
    return panel;
  }

  public boolean isAgreed() {
    return myOK && myAcceptCheckBox.isSelected();
  }

  protected JComponent createCenterPanel() {
    JPanel optionsPanel = new JPanel(new BorderLayout());
    JTextArea licenseTextArea = new JTextArea(myText, 20, 50);
    licenseTextArea.getCaret().setDot(0);
    licenseTextArea.setFont(myFont);
    licenseTextArea.setLineWrap(true);
    licenseTextArea.setWrapStyleWord(true);
    licenseTextArea.setEditable(false);
    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(licenseTextArea);

    optionsPanel.add(scrollPane, BorderLayout.CENTER);
    JPanel agreePanel = new JPanel(new GridLayout(1, 1));
    agreePanel.setBorder(BorderFactory.createEmptyBorder(10, 5, 5, 5));
    myAcceptCheckBox = new JCheckBox(myBundle.getString("license.agreement.accept.checkbox"));
    myAcceptCheckBox.setMnemonic(myAcceptCheckBox.getText().charAt(0));
    myAcceptCheckBox.setFont(myFont);

    agreePanel.add(myAcceptCheckBox);
    optionsPanel.add(agreePanel, BorderLayout.SOUTH);

    myAcceptCheckBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent event) {
        getOKAction().setEnabled(myAcceptCheckBox.isSelected());
      }
    });
    return optionsPanel;
  }
}
