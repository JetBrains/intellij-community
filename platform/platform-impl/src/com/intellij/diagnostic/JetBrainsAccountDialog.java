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
package com.intellij.diagnostic;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.ClickListener;
import com.intellij.util.net.HttpConfigurable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

public class JetBrainsAccountDialog extends DialogWrapper {
  private JTextField myItnLoginTextField;
  private JPasswordField myItnPasswordTextField;
  private JCheckBox myRememberITNPasswordCheckBox;

  public void storeInfo() {
    ErrorReportConfigurable.getInstance().ITN_LOGIN = myItnLoginTextField.getText();
    ErrorReportConfigurable.getInstance().setPlainItnPassword(new String(myItnPasswordTextField.getPassword()));
    ErrorReportConfigurable.getInstance().KEEP_ITN_PASSWORD = myRememberITNPasswordCheckBox.isSelected();
  }

  public void loadInfo() {
    myItnLoginTextField.setText(ErrorReportConfigurable.getInstance().ITN_LOGIN);
    myItnPasswordTextField.setText(ErrorReportConfigurable.getInstance().getPlainItnPassword());
    myRememberITNPasswordCheckBox.setSelected(ErrorReportConfigurable.getInstance().KEEP_ITN_PASSWORD);
  }

  public JetBrainsAccountDialog(Component parent) throws HeadlessException {
    super(parent, false);
    init();
  }

  public JetBrainsAccountDialog(Project project) throws HeadlessException {
    super(project, false);
    init();
  }

  protected JPanel myMainPanel;
  protected JLabel mySendingSettingsLabel;
  private JLabel myCreateAccountLabel;

  @Override
  protected String getDimensionServiceKey() {
    return "#com.intellij.diagnostic.AbstractSendErrorDialog";
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myItnLoginTextField;
  }

  @Override
  protected void init() {
    setTitle(ReportMessages.ERROR_REPORT);
    getContentPane().add(myMainPanel);

    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent e, int clickCount) {
        HttpConfigurable.editConfigurable(myMainPanel);
        return true;
      }
    }.installOn(mySendingSettingsLabel);

    mySendingSettingsLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));

    loadInfo();

    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent e, int clickCount) {
        BrowserUtil.browse("http://account.jetbrains.com");
        return true;
      }
    }.installOn(myCreateAccountLabel);
    myCreateAccountLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));

    super.init();
  }

  @Override
  protected void doOKAction() {
    storeInfo();
    super.doOKAction();
  }

  @Override
  protected JComponent createCenterPanel() {
    return myMainPanel;
  }
}
