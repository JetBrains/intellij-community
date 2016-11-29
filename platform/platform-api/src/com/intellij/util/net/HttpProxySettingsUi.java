/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.google.common.net.HostAndPort;
import com.google.common.net.InetAddresses;
import com.google.common.net.InternetDomainName;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ConfigurableUi;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.ui.PortField;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.ui.RelativeFont;
import com.intellij.ui.components.JBRadioButton;
import com.intellij.util.io.HttpRequests;
import com.intellij.util.proxy.CommonProxy;
import com.intellij.util.proxy.JavaProxyProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

class HttpProxySettingsUi implements ConfigurableUi<HttpConfigurable> {
  private JPanel myMainPanel;

  private JTextField myProxyLoginTextField;
  private JPasswordField myProxyPasswordTextField;
  private JCheckBox myProxyAuthCheckBox;
  private PortField myProxyPortTextField;
  private JTextField myProxyHostTextField;
  private JCheckBox myRememberProxyPasswordCheckBox;

  private JLabel myProxyLoginLabel;
  private JLabel myProxyPasswordLabel;
  private JLabel myHostNameLabel;
  private JLabel myPortNumberLabel;
  private JBRadioButton myAutoDetectProxyRb;
  private JBRadioButton myUseHTTPProxyRb;
  private JLabel mySystemProxyDefined;
  private JBRadioButton myNoProxyRb;
  private JBRadioButton myHTTP;
  private JBRadioButton mySocks;
  private JButton myClearPasswordsButton;
  private JLabel myErrorLabel;
  private JButton myCheckButton;
  private JLabel myOtherWarning;
  private JLabel myProxyExceptionsLabel;
  private RawCommandLineEditor myProxyExceptions;
  private JLabel myNoProxyForLabel;
  private JCheckBox myPacUrlCheckBox;
  private JTextField myPacUrlTextField;
  private volatile boolean myConnectionCheckInProgress;

  @Override
  public boolean isModified(@NotNull HttpConfigurable settings) {
    if (!isValid()) {
      return false;
    }

    return !Comparing.strEqual(myProxyExceptions.getText().trim(), settings.PROXY_EXCEPTIONS) ||
           settings.USE_PROXY_PAC != myAutoDetectProxyRb.isSelected() ||
           settings.USE_PAC_URL != myPacUrlCheckBox.isSelected() ||
           !Comparing.strEqual(settings.PAC_URL, myPacUrlTextField.getText()) ||
           settings.USE_HTTP_PROXY != myUseHTTPProxyRb.isSelected() ||
           settings.PROXY_AUTHENTICATION != myProxyAuthCheckBox.isSelected() ||
           settings.KEEP_PROXY_PASSWORD != myRememberProxyPasswordCheckBox.isSelected() ||
           settings.PROXY_TYPE_IS_SOCKS != mySocks.isSelected() ||
           !Comparing.strEqual(settings.getProxyLogin(), myProxyLoginTextField.getText()) ||
           !Comparing.strEqual(settings.getPlainProxyPassword(), new String(myProxyPasswordTextField.getPassword())) ||
           settings.PROXY_PORT != myProxyPortTextField.getNumber() ||
           !Comparing.strEqual(settings.PROXY_HOST, myProxyHostTextField.getText());
  }

  public HttpProxySettingsUi(@NotNull final HttpConfigurable settings) {
    ButtonGroup group = new ButtonGroup();
    group.add(myUseHTTPProxyRb);
    group.add(myAutoDetectProxyRb);
    group.add(myNoProxyRb);
    myNoProxyRb.setSelected(true);

    ButtonGroup proxyTypeGroup = new ButtonGroup();
    proxyTypeGroup.add(myHTTP);
    proxyTypeGroup.add(mySocks);
    myHTTP.setSelected(true);

    Boolean property = Boolean.getBoolean(JavaProxyProperty.USE_SYSTEM_PROXY);
    mySystemProxyDefined.setVisible(Boolean.TRUE.equals(property));
    if (Boolean.TRUE.equals(property)) {
      mySystemProxyDefined.setIcon(Messages.getWarningIcon());
      RelativeFont.BOLD.install(mySystemProxyDefined);
    }

    myProxyAuthCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(@NotNull ActionEvent e) {
        enableProxyAuthentication(myProxyAuthCheckBox.isSelected());
      }
    });
    myPacUrlCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(@NotNull ActionEvent e) {
        myPacUrlTextField.setEnabled(myPacUrlCheckBox.isSelected());
      }
    });

    ActionListener listener = new ActionListener() {
      @Override
      public void actionPerformed(@NotNull ActionEvent e) {
        enableProxy(myUseHTTPProxyRb.isSelected());
      }
    };
    myUseHTTPProxyRb.addActionListener(listener);
    myAutoDetectProxyRb.addActionListener(listener);
    myNoProxyRb.addActionListener(listener);

    myClearPasswordsButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(@NotNull ActionEvent e) {
        settings.clearGenericPasswords();
        //noinspection DialogTitleCapitalization
        Messages.showMessageDialog(myMainPanel, "Proxy passwords were cleared.", "Auto-detected Proxy", Messages.getInformationIcon());
      }
    });

    configureCheckButton();
  }

  private void configureCheckButton() {
    if (HttpConfigurable.getInstance() == null) {
      myCheckButton.setVisible(false);
      return;
    }

    myCheckButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(@NotNull ActionEvent e) {
        final String title = "Check Proxy Settings";
        final String answer = Messages.showInputDialog(myMainPanel, "Warning: your settings will be saved.\n\nEnter any URL to check connection to:",
                                                       title, Messages.getQuestionIcon(), "http://", null);
        if (StringUtil.isEmptyOrSpaces(answer)) {
          return;
        }

        final HttpConfigurable settings = HttpConfigurable.getInstance();
        apply(settings);
        final AtomicReference<IOException> exceptionReference = new AtomicReference<>();
        myCheckButton.setEnabled(false);
        myCheckButton.setText("Check connection (in progress...)");
        myConnectionCheckInProgress = true;
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
          try {
            //already checked for null above
            //noinspection ConstantConditions
            HttpRequests.request(answer)
              .readTimeout(3 * 1000)
              .tryConnect();
          }
          catch (IOException e1) {
            exceptionReference.set(e1);
          }

          //noinspection SSBasedInspection
          SwingUtilities.invokeLater(() -> {
            myConnectionCheckInProgress = false;
            reset(settings);  // since password might have been set
            Component parent;
            if (myMainPanel.isShowing()) {
              parent = myMainPanel;
              myCheckButton.setText("Check connection");
              myCheckButton.setEnabled(canEnableConnectionCheck());
            }
            else {
              IdeFrame frame = IdeFocusManager.findInstance().getLastFocusedFrame();
              if (frame == null) {
                return;
              }
              parent = frame.getComponent();
            }
            //noinspection ThrowableResultOfMethodCallIgnored
            final IOException exception = exceptionReference.get();
            if (exception == null) {
              Messages.showMessageDialog(parent, "Connection successful", title, Messages.getInformationIcon());
            }
            else {
              final String message = exception.getMessage();
              if (settings.USE_HTTP_PROXY) {
                settings.LAST_ERROR = message;
              }
              Messages.showErrorDialog(parent, errorText(message));
            }
          });
        });
      }
    });
  }

  private boolean canEnableConnectionCheck() {
    return !myNoProxyRb.isSelected() && !myConnectionCheckInProgress;
  }

  @Override
  public void reset(@NotNull HttpConfigurable settings) {
    myNoProxyRb.setSelected(true);  // default
    myAutoDetectProxyRb.setSelected(settings.USE_PROXY_PAC);
    myPacUrlCheckBox.setSelected(settings.USE_PAC_URL);
    myPacUrlTextField.setText(settings.PAC_URL);
    myUseHTTPProxyRb.setSelected(settings.USE_HTTP_PROXY);
    myProxyAuthCheckBox.setSelected(settings.PROXY_AUTHENTICATION);

    enableProxy(settings.USE_HTTP_PROXY);

    myProxyLoginTextField.setText(settings.getProxyLogin());
    myProxyPasswordTextField.setText(settings.getPlainProxyPassword());

    myProxyPortTextField.setNumber(settings.PROXY_PORT);
    myProxyHostTextField.setText(settings.PROXY_HOST);
    myProxyExceptions.setText(StringUtil.notNullize(settings.PROXY_EXCEPTIONS));

    myRememberProxyPasswordCheckBox.setSelected(settings.KEEP_PROXY_PASSWORD);
    mySocks.setSelected(settings.PROXY_TYPE_IS_SOCKS);
    myHTTP.setSelected(!settings.PROXY_TYPE_IS_SOCKS);

    boolean showError = !StringUtil.isEmptyOrSpaces(settings.LAST_ERROR);
    myErrorLabel.setVisible(showError);
    myErrorLabel.setText(showError ? errorText(settings.LAST_ERROR) : null);

    final String oldStyleText = CommonProxy.getMessageFromProps(CommonProxy.getOldStyleProperties());
    myOtherWarning.setVisible(oldStyleText != null);
    if (oldStyleText != null) {
      myOtherWarning.setText(oldStyleText);
      myOtherWarning.setIcon(Messages.getWarningIcon());
    }
  }

  @NotNull
  private static String errorText(@NotNull String s) {
    return "Problem with connection: " + s;
  }

  private boolean isValid() {
    if (myUseHTTPProxyRb.isSelected()) {
      String host = getText(myProxyHostTextField);
      if (host == null) {
        return false;
      }

      try {
        HostAndPort parsedHost = HostAndPort.fromString(host);
        if (parsedHost.hasPort()) {
          return false;
        }
        host = parsedHost.getHostText();

        try {
          InetAddresses.forString(host);
          return true;
        }
        catch (IllegalArgumentException e) {
          // it is not an IPv4 or IPv6 literal
        }

        InternetDomainName.from(host);
      }
      catch (IllegalArgumentException e) {
        return false;
      }

      if (myProxyAuthCheckBox.isSelected()) {
        return !StringUtil.isEmptyOrSpaces(myProxyLoginTextField.getText()) && myProxyPasswordTextField.getPassword().length > 0;
      }
    }
    return true;
  }

  @Override
  public void apply(@NotNull HttpConfigurable settings) {
    if (!isValid()) {
      return;
    }

    if (isModified(settings)) {
      settings.AUTHENTICATION_CANCELLED = false;
    }

    settings.USE_PROXY_PAC = myAutoDetectProxyRb.isSelected();
    settings.USE_PAC_URL = myPacUrlCheckBox.isSelected();
    settings.PAC_URL = getText(myPacUrlTextField);
    settings.USE_HTTP_PROXY = myUseHTTPProxyRb.isSelected();
    settings.PROXY_TYPE_IS_SOCKS = mySocks.isSelected();
    settings.PROXY_AUTHENTICATION = myProxyAuthCheckBox.isSelected();
    settings.KEEP_PROXY_PASSWORD = myRememberProxyPasswordCheckBox.isSelected();

    settings.setProxyLogin(getText(myProxyLoginTextField));
    settings.setPlainProxyPassword(new String(myProxyPasswordTextField.getPassword()));
    settings.PROXY_EXCEPTIONS = StringUtil.nullize(myProxyExceptions.getText(), true);

    settings.PROXY_PORT = myProxyPortTextField.getNumber();
    settings.PROXY_HOST = getText(myProxyHostTextField);
  }

  @Nullable
  private static String getText(@NotNull JTextField field) {
    return StringUtil.nullize(field.getText(), true);
  }

  private void enableProxy(boolean enabled) {
    myHostNameLabel.setEnabled(enabled);
    myPortNumberLabel.setEnabled(enabled);
    myProxyHostTextField.setEnabled(enabled);
    myProxyPortTextField.setEnabled(enabled);
    mySocks.setEnabled(enabled);
    myHTTP.setEnabled(enabled);
    myProxyExceptions.setEnabled(enabled);
    myProxyExceptionsLabel.setEnabled(enabled);
    myNoProxyForLabel.setEnabled(enabled);

    myProxyAuthCheckBox.setEnabled(enabled);
    enableProxyAuthentication(enabled && myProxyAuthCheckBox.isSelected());
    myCheckButton.setEnabled(canEnableConnectionCheck());

    final boolean autoDetectProxy = myAutoDetectProxyRb.isSelected();
    myPacUrlCheckBox.setEnabled(autoDetectProxy);
    myClearPasswordsButton.setEnabled(autoDetectProxy);
    myPacUrlTextField.setEnabled(autoDetectProxy && myPacUrlCheckBox.isSelected());
  }

  private void enableProxyAuthentication(boolean enabled) {
    myProxyPasswordLabel.setEnabled(enabled);
    myProxyLoginLabel.setEnabled(enabled);

    myProxyLoginTextField.setEnabled(enabled);
    myProxyPasswordTextField.setEnabled(enabled);

    myRememberProxyPasswordCheckBox.setEnabled(enabled);
  }

  @Override
  @NotNull
  public JComponent getComponent() {
    return myMainPanel;
  }
}
