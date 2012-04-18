/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.ide.highlighter.custom.impl;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ide.IdeBundle;
import com.intellij.CommonBundle;

import javax.swing.*;
import java.awt.*;

/**
 * @author Yura Cangea
 */
public class ModifyKeywordDialog extends DialogWrapper {
  private final JTextField myKeywordName = new JTextField();

  public ModifyKeywordDialog(Component parent, String initialValue) {
    super(parent, false);
    if (initialValue == null || "".equals(initialValue)) {
      setTitle(IdeBundle.message("title.add.new.keyword"));
    }
    else {
      setTitle(IdeBundle.message("title.edit.keyword"));
    }
    init();
    myKeywordName.setText(initialValue);
  }

  protected JComponent createNorthPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gc = new GridBagConstraints();
    gc.gridx = 0;
    gc.gridy = 0;
    gc.weightx = 0;
    gc.insets = new Insets(5, 0, 5, 10);
    gc.anchor = GridBagConstraints.BASELINE;
    panel.add(new JLabel(IdeBundle.message("editbox.keyword")), gc);

    gc = new GridBagConstraints();
    gc.gridx = 1;
    gc.gridy = 0;
    gc.weightx = 1;
    gc.fill = GridBagConstraints.HORIZONTAL;
    gc.gridwidth = 2;
    gc.insets = new Insets(0, 0, 5, 0);
    panel.add(myKeywordName, gc);

    panel.setPreferredSize(new Dimension(220, 40));
    return panel;
  }

  protected JComponent createCenterPanel() {
    return null;
  }

  protected void doOKAction() {
    final String keywordName = myKeywordName.getText().trim();
    if (keywordName.length() == 0) {
      Messages.showMessageDialog(getContentPane(), IdeBundle.message("error.keyword.cannot.be.empty"),
                                 CommonBundle.getErrorTitle(), Messages.getErrorIcon());
      return;
    }
    if (keywordName.indexOf(' ') >= 0) {
      Messages.showMessageDialog(getContentPane(), IdeBundle.message("error.keyword.may.not.contain.spaces"),
                                 CommonBundle.getErrorTitle(), Messages.getErrorIcon());
      return;
    }
    super.doOKAction();
  }

  public JComponent getPreferredFocusedComponent() {
    return myKeywordName;
  }

  public String getKeywordName() {
    return myKeywordName.getText();
  }
}
