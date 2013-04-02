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

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.util.proxy.CommonProxy;
import com.intellij.util.proxy.JavaProxyProperty;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Aug 28, 2003
 * Time: 3:52:47 PM
 * To change this template use Options | File Templates.
 */
public class HTTPProxySettingsPanel implements SearchableConfigurable, Configurable.NoScroll {
  public static final String NAME = "Proxy";
  private JPanel myMainPanel;

  private JTextField myProxyLoginTextField;
  private JPasswordField myProxyPasswordTextField;
  private JCheckBox myProxyAuthCheckBox;
  private JTextField myProxyPortTextField;
  private JTextField myProxyHostTextField;
  private JCheckBox myRememberProxyPasswordCheckBox;

  private JLabel myProxyLoginLabel;
  private JLabel myProxyPasswordLabel;
  private JLabel myHostNameLabel;
  private JLabel myPortNumberLabel;
  private JBRadioButton myAutoDetectProxyRb;
  private JBRadioButton myUseHTTPProxyRb;
  private JBLabel mySystemProxyDefined;
  private JBRadioButton myNoProxyRb;
  private JBRadioButton myHTTP;
  private JBRadioButton mySocks;
  private JButton myClearPasswordsButton;
  private JLabel myErrorLabel;
  private JButton myCheckButton;
  private JBLabel myOtherWarning;
  private JLabel myProxyExceptionsLabel;
  private JTextArea myProxyExceptions;
  private JLabel myNoProxyForLabel;
  private final HttpConfigurable myHttpConfigurable;
  private volatile boolean myConnectionCheckInProgress;

  public boolean isModified() {
    boolean isModified = false;
    HttpConfigurable httpConfigurable = myHttpConfigurable;
    if (! Comparing.equal(myProxyExceptions.getText().trim(), httpConfigurable.PROXY_EXCEPTIONS)) return true;
    isModified |= httpConfigurable.USE_PROXY_PAC != myAutoDetectProxyRb.isSelected();
    isModified |= httpConfigurable.USE_HTTP_PROXY != myUseHTTPProxyRb.isSelected();
    isModified |= httpConfigurable.PROXY_AUTHENTICATION != myProxyAuthCheckBox.isSelected();
    isModified |= httpConfigurable.KEEP_PROXY_PASSWORD != myRememberProxyPasswordCheckBox.isSelected();
    isModified |= httpConfigurable.PROXY_TYPE_IS_SOCKS != mySocks.isSelected();

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
    final ButtonGroup group = new ButtonGroup();
    group.add(myUseHTTPProxyRb);
    group.add(myAutoDetectProxyRb);
    group.add(myNoProxyRb);
    myNoProxyRb.setSelected(true);

    final ButtonGroup proxyTypeGroup = new ButtonGroup();
    proxyTypeGroup.add(myHTTP);
    proxyTypeGroup.add(mySocks);
    myHTTP.setSelected(true);

    myProxyExceptions.setBorder(UIUtil.getTextFieldBorder());

    final Boolean property = Boolean.getBoolean(JavaProxyProperty.USE_SYSTEM_PROXY);
    mySystemProxyDefined.setVisible(Boolean.TRUE.equals(property));
    if (Boolean.TRUE.equals(property)) {
      mySystemProxyDefined.setIcon(Messages.getWarningIcon());
      mySystemProxyDefined.setFont(mySystemProxyDefined.getFont().deriveFont(Font.BOLD));
      mySystemProxyDefined.setUI(new MultiLineLabelUI());
    }

    myProxyAuthCheckBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        enableProxyAuthentication(myProxyAuthCheckBox.isSelected());
      }
    });

    final ActionListener listener = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        enableProxy(myUseHTTPProxyRb.isSelected());
      }
    };
    myUseHTTPProxyRb.addActionListener(listener);
    myAutoDetectProxyRb.addActionListener(listener);
    myNoProxyRb.addActionListener(listener);
    myHttpConfigurable = httpConfigurable;

    myClearPasswordsButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myHttpConfigurable.clearGenericPasswords();
        Messages.showMessageDialog(myMainPanel, "Proxy passwords were cleared.", "Auto-detected proxy", Messages.getInformationIcon());
      }
    });

    if (HttpConfigurable.getInstance() != null) {
      myCheckButton.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          final String title = "Check Proxy Settings";
          final String answer = Messages
            .showInputDialog(myMainPanel, "Warning: your settings will be saved.\n\nEnter any URL to check connection to:",
                             title, Messages.getQuestionIcon(), "http://", null);
          if (! StringUtil.isEmptyOrSpaces(answer)) {
            apply();
            final HttpConfigurable instance = HttpConfigurable.getInstance();
            final AtomicReference<IOException> exc = new AtomicReference<IOException>();
            myCheckButton.setEnabled(false);
            myCheckButton.setText("Check connection (in progress...)");
            myConnectionCheckInProgress = true;
            final Application application = ApplicationManager.getApplication();
            application.executeOnPooledThread(new Runnable() {
              @Override
              public void run() {
                HttpURLConnection connection = null;
                try {
                  //already checked for null above
                  //noinspection ConstantConditions
                  connection = instance.openHttpConnection(answer);
                  connection.setReadTimeout(3 * 1000);
                  connection.setConnectTimeout(3 * 1000);
                  connection.connect();
                  final int code = connection.getResponseCode();
                  if (HttpURLConnection.HTTP_OK != code) {
                    exc.set(new IOException("Error code: " + code));
                  }
                }
                catch (IOException e1) {
                  exc.set(e1);
                }
                finally {
                  if (connection != null) {
                    connection.disconnect();
                  }
                }
                //noinspection SSBasedInspection
                SwingUtilities.invokeLater(new Runnable() {
                  @Override
                  public void run() {
                    myConnectionCheckInProgress = false;
                    reset();  // since password might have been set
                    Component parent = null;
                    if (myMainPanel.isShowing()) {
                      parent = myMainPanel;
                      myCheckButton.setText("Check connection");
                      myCheckButton.setEnabled(canEnableConnectionCheck());
                    } else {
                      final IdeFrame frame = IdeFocusManager.findInstance().getLastFocusedFrame();
                      if (frame == null) {
                        return;
                      }
                      parent = frame.getComponent();
                    }
                    //noinspection ThrowableResultOfMethodCallIgnored
                    final IOException exception = exc.get();
                    if (exception == null) {
                      Messages.showMessageDialog(parent, "Connection successful", title, Messages.getInformationIcon());
                    }
                    else {
                      final String message = exception.getMessage();
                      if (instance.USE_HTTP_PROXY) {
                        instance.LAST_ERROR = message;
                      }
                      Messages.showErrorDialog(parent, errorText(message));
                    }
                  }
                });
              }
            });
          }
        }
      });
    } else {
      myCheckButton.setVisible(false);
    }
  }

  private boolean canEnableConnectionCheck() {
    return ! myNoProxyRb.isSelected() && ! myConnectionCheckInProgress;
  }

  public void reset() {
    myNoProxyRb.setSelected(true);  // default
    HttpConfigurable httpConfigurable = myHttpConfigurable;
    myAutoDetectProxyRb.setSelected(httpConfigurable.USE_PROXY_PAC);
    myUseHTTPProxyRb.setSelected(httpConfigurable.USE_HTTP_PROXY);
    myProxyAuthCheckBox.setSelected(httpConfigurable.PROXY_AUTHENTICATION);

    enableProxy(httpConfigurable.USE_HTTP_PROXY);

    myProxyLoginTextField.setText(httpConfigurable.PROXY_LOGIN);
    myProxyPasswordTextField.setText(httpConfigurable.getPlainProxyPassword());

    myProxyPortTextField.setText(Integer.toString(httpConfigurable.PROXY_PORT));
    myProxyHostTextField.setText(httpConfigurable.PROXY_HOST);
    myProxyExceptions.setText(httpConfigurable.PROXY_EXCEPTIONS);

    myRememberProxyPasswordCheckBox.setSelected(httpConfigurable.KEEP_PROXY_PASSWORD);
    mySocks.setSelected(httpConfigurable.PROXY_TYPE_IS_SOCKS);
    myHTTP.setSelected(!httpConfigurable.PROXY_TYPE_IS_SOCKS);

    final boolean showError = !StringUtil.isEmptyOrSpaces(httpConfigurable.LAST_ERROR);
    myErrorLabel.setVisible(showError);
    myErrorLabel.setText(showError ? errorText(httpConfigurable.LAST_ERROR) : "");

    final String oldStyleText = CommonProxy.getMessageFromProps(CommonProxy.getOldStyleProperties());
    myOtherWarning.setVisible(oldStyleText != null);
    if (oldStyleText != null) {
      myOtherWarning.setText(oldStyleText);
      myOtherWarning.setUI(new MultiLineLabelUI());
      myOtherWarning.setIcon(Messages.getWarningIcon());
    }
  }

  private String errorText(final String s) {
    return "Problem with connection: " + s;
  }

  public void apply () {
    HttpConfigurable httpConfigurable = myHttpConfigurable;
    if (isModified()){
      httpConfigurable.AUTHENTICATION_CANCELLED = false;
    }
    httpConfigurable.USE_PROXY_PAC = myAutoDetectProxyRb.isSelected();
    httpConfigurable.USE_HTTP_PROXY = myUseHTTPProxyRb.isSelected();
    httpConfigurable.PROXY_TYPE_IS_SOCKS = mySocks.isSelected();
    httpConfigurable.PROXY_AUTHENTICATION = myProxyAuthCheckBox.isSelected();
    httpConfigurable.KEEP_PROXY_PASSWORD = myRememberProxyPasswordCheckBox.isSelected();

    httpConfigurable.PROXY_LOGIN = trimFieldText(myProxyLoginTextField);
    httpConfigurable.setPlainProxyPassword(new String(myProxyPasswordTextField.getPassword()));
    httpConfigurable.PROXY_EXCEPTIONS = myProxyExceptions.getText();

    try {
      httpConfigurable.PROXY_PORT = Integer.valueOf(trimFieldText(myProxyPortTextField)).intValue();
    } catch (NumberFormatException e) {
      httpConfigurable.PROXY_PORT = 80;
    }
    httpConfigurable.PROXY_HOST = trimFieldText(myProxyHostTextField);
  }

  private static String trimFieldText(JTextField field) {
    String trimmed = field.getText().trim();
    field.setText(trimmed);
    return trimmed;
  }

  private void enableProxy (boolean enabled) {
    myHostNameLabel.setEnabled(enabled);
    myPortNumberLabel.setEnabled(enabled);
    myProxyHostTextField.setEnabled(enabled);
    myProxyPortTextField.setEnabled(enabled);
    mySocks.setEnabled(enabled);
    myHTTP.setEnabled(enabled);
    myProxyExceptions.setEnabled(enabled);
    myProxyExceptions.setBackground(myProxyPortTextField.getBackground());
    myProxyExceptionsLabel.setEnabled(enabled);
    myNoProxyForLabel.setEnabled(enabled);

    myProxyAuthCheckBox.setEnabled(enabled);
    enableProxyAuthentication(enabled && myProxyAuthCheckBox.isSelected());
    myCheckButton.setEnabled(canEnableConnectionCheck());
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
    return NAME;
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
    myUseHTTPProxyRb.addActionListener(actionListener);
    myRememberProxyPasswordCheckBox.addActionListener(actionListener);

  }

  @Override
  public void disposeUIResources() {
  }
}
