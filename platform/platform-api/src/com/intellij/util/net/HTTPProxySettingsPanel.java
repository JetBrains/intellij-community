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
package com.intellij.util.net;

import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentListener;
import javax.swing.event.DocumentEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Aug 28, 2003
 * Time: 3:52:47 PM
 * To change this template use Options | File Templates.
 */
public class HTTPProxySettingsPanel implements SearchableConfigurable {
  private JPanel myMainPanel;

  private JTextField myProxyLoginTextField;
  private JPasswordField myProxyPasswordTextField;
  private JCheckBox myProxyAuthCheckBox;
  private JTextField myProxyPortTextField;
  private JTextField myProxyHostTextField;
  private JCheckBox myUseProxyCheckBox;
  private JCheckBox myRememberProxyPasswordCheckBox;

  private JLabel myProxyLoginLabel;
  private JLabel myProxyPasswordLabel;
  private JLabel myHostNameLabel;
  private JLabel myPortNumberLabel;
  private final HttpConfigurable myHttpConfigurable;

  public boolean isModified() {
    boolean isModified = false;
    HttpConfigurable httpConfigurable = myHttpConfigurable;
    isModified |= httpConfigurable.USE_HTTP_PROXY != myUseProxyCheckBox.isSelected();
    isModified |= httpConfigurable.PROXY_AUTHENTICATION != myProxyAuthCheckBox.isSelected();
    isModified |= httpConfigurable.KEEP_PROXY_PASSWORD != myRememberProxyPasswordCheckBox.isSelected();

    isModified |= !Comparing.strEqual(httpConfigurable.PROXY_LOGIN, myProxyLoginTextField.getText());
    isModified |= !Comparing.strEqual(httpConfigurable.getPlainProxyPassword(),new String (myProxyPasswordTextField.getPassword()));

    try {
      isModified |= httpConfigurable.PROXY_PORT != Integer.valueOf(myProxyPortTextField.getText()).intValue();
    } catch (NumberFormatException e) {
      isModified = true;
    }
    isModified |= !Comparing.strEqual(httpConfigurable.PROXY_HOST, myProxyHostTextField.getText());
    return isModified;
  }

  public HTTPProxySettingsPanel(final HttpConfigurable httpConfigurable) {
    myProxyAuthCheckBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        enableProxyAuthentication(myProxyAuthCheckBox.isSelected());
      }
    });

    myUseProxyCheckBox.addActionListener(new ActionListener () {
      public void actionPerformed(ActionEvent e) {
        enableProxy(myUseProxyCheckBox.isSelected());
      }
    });
    myHttpConfigurable = httpConfigurable;
  }

  public void reset() {
    HttpConfigurable httpConfigurable = myHttpConfigurable;
    myUseProxyCheckBox.setSelected(httpConfigurable.USE_HTTP_PROXY);
    myProxyAuthCheckBox.setSelected(httpConfigurable.PROXY_AUTHENTICATION);

    enableProxy(httpConfigurable.USE_HTTP_PROXY);

    myProxyLoginTextField.setText(httpConfigurable.PROXY_LOGIN);
    myProxyPasswordTextField.setText(httpConfigurable.getPlainProxyPassword());

    myProxyPortTextField.setText(Integer.toString(httpConfigurable.PROXY_PORT));
    myProxyHostTextField.setText(httpConfigurable.PROXY_HOST);

    myRememberProxyPasswordCheckBox.setSelected(httpConfigurable.KEEP_PROXY_PASSWORD);
  }

  public void apply () {
    HttpConfigurable httpConfigurable = myHttpConfigurable;
    httpConfigurable.USE_HTTP_PROXY = myUseProxyCheckBox.isSelected();
    httpConfigurable.PROXY_AUTHENTICATION = myProxyAuthCheckBox.isSelected();
    httpConfigurable.KEEP_PROXY_PASSWORD = myRememberProxyPasswordCheckBox.isSelected();

    httpConfigurable.PROXY_LOGIN = myProxyLoginTextField.getText();
    httpConfigurable.setPlainProxyPassword(new String (myProxyPasswordTextField.getPassword()));

    try {
      httpConfigurable.PROXY_PORT = Integer.valueOf(myProxyPortTextField.getText()).intValue();
    } catch (NumberFormatException e) {
      httpConfigurable.PROXY_PORT = 80;
    }
    httpConfigurable.PROXY_HOST = myProxyHostTextField.getText();
  }

  private void enableProxy (boolean enabled) {
    myHostNameLabel.setEnabled(enabled);
    myPortNumberLabel.setEnabled(enabled);
    myProxyHostTextField.setEnabled(enabled);
    myProxyPortTextField.setEnabled(enabled);

    myProxyAuthCheckBox.setEnabled(enabled);
    enableProxyAuthentication(enabled && myProxyAuthCheckBox.isSelected());
  }

  private void enableProxyAuthentication (boolean enabled) {
    myProxyPasswordLabel.setEnabled(enabled);
    myProxyLoginLabel.setEnabled(enabled);

    myProxyLoginTextField.setEnabled(enabled);
    myProxyPasswordTextField.setEnabled(enabled);

    myRememberProxyPasswordCheckBox.setEnabled(enabled);
  }

  public JComponent getComponent() {
    return myMainPanel;
  }

  public JComponent createComponent() {
    return myMainPanel;
  }

  @NotNull
  public String getId() {
    return getHelpTopic();
  }

  public Runnable enableSearch(final String option) {
    return null;
  }

  @Nls
  public String getDisplayName() {
    return "HTTP Proxy";
  }

  public Icon getIcon() {
    return null;
  }

  public String getHelpTopic() {
    return "http.proxy";
  }

  public void addActionListener(final ActionListener actionListener) {
    myProxyLoginTextField.addActionListener(actionListener);
    DocumentListener docListener = new DocumentListener() {
      public void insertUpdate(DocumentEvent e) {
        actionListener.actionPerformed(null);
      }

      public void removeUpdate(DocumentEvent e) {
        actionListener.actionPerformed(null);
      }

      public void changedUpdate(DocumentEvent e) {
        actionListener.actionPerformed(null);
      }
    };
    myProxyPasswordTextField.getDocument().addDocumentListener(docListener);
    myProxyAuthCheckBox.addActionListener(actionListener);
    myProxyPortTextField.getDocument().addDocumentListener(docListener);
    myProxyHostTextField.getDocument().addDocumentListener(docListener);
    myUseProxyCheckBox.addActionListener(actionListener);
    myRememberProxyPasswordCheckBox.addActionListener(actionListener);

  }

  @Override
  public void disposeUIResources() {
  }
}
