// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.net;

import com.google.common.net.HostAndPort;
import com.google.common.net.InetAddresses;
import com.google.common.net.InternetDomainName;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.options.ConfigurableUi;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
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
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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

  @Override
  public boolean isModified(@NotNull HttpConfigurable settings) {
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

  HttpProxySettingsUi(@NotNull final HttpConfigurable settings) {
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

    myProxyAuthCheckBox.addActionListener(e -> enableProxyAuthentication(myProxyAuthCheckBox.isSelected()));
    myPacUrlCheckBox.addActionListener(e -> myPacUrlTextField.setEnabled(myPacUrlCheckBox.isSelected()));

    ActionListener listener = e -> enableProxy(myUseHTTPProxyRb.isSelected());
    myUseHTTPProxyRb.addActionListener(listener);
    myAutoDetectProxyRb.addActionListener(listener);
    myNoProxyRb.addActionListener(listener);

    myClearPasswordsButton.addActionListener(e -> {
      settings.clearGenericPasswords();
      //noinspection DialogTitleCapitalization
      Messages.showMessageDialog(myMainPanel, IdeBundle.message("message.text.proxy.passwords.were.cleared"),
                                 IdeBundle.message("dialog.title.auto.detected.proxy"), Messages.getInformationIcon());
    });

    configureCheckButton();
  }

  private void configureCheckButton() {
    if (HttpConfigurable.getInstance() == null) {
      myCheckButton.setVisible(false);
      return;
    }

    myCheckButton.addActionListener(event -> {
      String error = isValid();
      if (error != null) {
        Messages.showErrorDialog(myMainPanel, error);
        return;
      }

      final String title = IdeBundle.message("dialog.title.check.proxy.settings");
      final String answer =
        Messages.showInputDialog(myMainPanel,
                                 IdeBundle.message("message.text.enter.url.to.check.connection"),
                                 title, Messages.getQuestionIcon(), "http://", null);
      if (StringUtil.isEmptyOrSpaces(answer)) {
        return;
      }

      final HttpConfigurable settings = HttpConfigurable.getInstance();
      try {
        apply(settings);
      }
      catch (ConfigurationException e) {
        return;
      }

      final AtomicReference<IOException> exceptionReference = new AtomicReference<>();
      ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
        try {
          HttpRequests.request(answer).readTimeout(3 * 1000).tryConnect();
        }
        catch (IOException e) {
          exceptionReference.set(e);
        }
      }, IdeBundle.message("progress.title.check.connection"), true, null);

      reset(settings);  // since password might have been set

      final IOException exception = exceptionReference.get();
      if (exception == null) {
        Messages.showMessageDialog(myMainPanel, IdeBundle.message("message.connection.successful"), title, Messages.getInformationIcon());
      }
      else {
        final String message = exception.getMessage();
        if (settings.USE_HTTP_PROXY) {
          settings.LAST_ERROR = message;
        }
        Messages.showErrorDialog(myMainPanel, errorText(message));
      }
    });
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

  private void createUIComponents() {
    myProxyExceptions = new RawCommandLineEditor(text -> {
      List<String> result = new ArrayList<>();
      for (String token : text.split(",")) {
        String trimmedToken = token.trim();
        if (!trimmedToken.isEmpty()) {
          result.add(trimmedToken);
        }
      }
      return result;
    }, strings -> StringUtil.join(strings, ", "));
  }

  @NotNull
  private static String errorText(@NotNull String s) {
    return "Problem with connection: " + s;
  }

  @Nullable
  private String isValid() {
    if (myUseHTTPProxyRb.isSelected()) {
      String host = getText(myProxyHostTextField);
      if (host == null) {
        return "Host name is empty";
      }

      try {
        HostAndPort parsedHost = HostAndPort.fromString(host);
        if (parsedHost.hasPort()) {
          return "Invalid host value";
        }
        host = parsedHost.getHost();

        try {
          InetAddresses.forString(host);
          return null;
        }
        catch (IllegalArgumentException e) {
          // it is not an IPv4 or IPv6 literal
        }

        InternetDomainName.from(host);
      }
      catch (IllegalArgumentException e) {
        return "Invalid host value";
      }

      if (myProxyAuthCheckBox.isSelected()) {
        if (StringUtil.isEmptyOrSpaces(myProxyLoginTextField.getText())) {
          return "Login is empty";
        }
        if (myProxyPasswordTextField.getPassword().length == 0) {
          return "Password is empty";
        }
      }
    }
    return null;
  }

  @Override
  public void apply(@NotNull HttpConfigurable settings) throws ConfigurationException {
    String error = isValid();
    if (error != null) {
      throw new ConfigurationException(error);
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

    final boolean autoDetectProxy = myAutoDetectProxyRb.isSelected();
    myPacUrlCheckBox.setEnabled(autoDetectProxy);
    myClearPasswordsButton.setEnabled(autoDetectProxy);
    myPacUrlTextField.setEnabled(autoDetectProxy && myPacUrlCheckBox.isSelected());
  }

  private void enableProxyAuthentication(boolean enabled) {
    myProxyLoginLabel.setEnabled(enabled);
    myProxyLoginTextField.setEnabled(enabled);
    myProxyPasswordLabel.setEnabled(enabled);
    myProxyPasswordTextField.setEnabled(enabled);
    myRememberProxyPasswordCheckBox.setEnabled(enabled);
  }

  @Override
  @NotNull
  public JComponent getComponent() {
    return myMainPanel;
  }
}
