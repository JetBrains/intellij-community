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

/**
 * @author Yura Cangea
 */
package com.intellij.application.options;

import com.intellij.CommonBundle;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

public class SaveSchemeDialog extends DialogWrapper {
  private final JTextField mySchemeName = new JTextField();
  private final ArrayList myInvalidNames;

  public SaveSchemeDialog(Component parent, String title, ArrayList invalidNames){
    super(parent, false);
    myInvalidNames = invalidNames;
    setTitle(title);
    init();
  }

  public String getSchemeName() {
    return mySchemeName.getText();
  }

  protected JComponent createNorthPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    GridBagConstraints gc = new GridBagConstraints();
    gc.gridx = 0;
    gc.gridy = 0;
    gc.weightx = 0;
    gc.insets = new Insets(5, 0, 5, 5);
    panel.add(new JLabel(ApplicationBundle.message("label.name")), gc);

    gc = new GridBagConstraints();
    gc.gridx = 1;
    gc.gridy = 0;
    gc.weightx = 1;
    gc.fill = GridBagConstraints.HORIZONTAL;
    gc.gridwidth = 2;
    gc.insets = new Insets(0, 0, 5, 0);
    panel.add(mySchemeName, gc);

    panel.setPreferredSize(new Dimension(220, 40));
    return panel;
  }

  protected void doOKAction() {
    if (getSchemeName().trim().length()==0) {
      Messages.showMessageDialog(getContentPane(), ApplicationBundle.message("error.scheme.must.have.a.name"),
                                 CommonBundle.getErrorTitle(), Messages.getErrorIcon());
      return;
    }
    else
    //noinspection HardCodedStringLiteral
    if ("default".equals(getSchemeName())) {
      Messages.showMessageDialog(getContentPane(),ApplicationBundle.message("error.illegal.scheme.name"),
                                 CommonBundle.getErrorTitle(), Messages.getErrorIcon());
      return;
    } else if (myInvalidNames.contains(getSchemeName())) {
      Messages.showMessageDialog(
        getContentPane(),
        ApplicationBundle.message("error.a.scheme.with.this.name.already.exists.or.was.deleted.without.applying.the.changes"),
        CommonBundle.getErrorTitle(),
        Messages.getErrorIcon()
      );
      return;
    }
    super.doOKAction();
  }

  protected JComponent createCenterPanel() {
    return null;
  }

  public JComponent getPreferredFocusedComponent() {
    return mySchemeName;
  }
}
