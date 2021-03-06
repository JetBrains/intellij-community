// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import com.intellij.credentialStore.Credentials;
import com.intellij.ide.DataManager;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

/**
 * @author tav
 */
class JBCefProxyAuthenticator {
  static @Nullable Credentials getCredentials(@NotNull JBCefBrowserBase browser, @NotNull String proxyServer, int proxyPort) {
    final Ref<Credentials> credentials = new Ref<>();
    JBCefProxySettings proxySettings = JBCefProxySettings.getInstance();
    String proxyHost = StringUtil.trimTrailing(ObjectUtils.notNull(proxySettings.PROXY_HOST, ""), '/');
    proxyServer = StringUtil.trimTrailing(proxyServer, '/');

    if (proxySettings.PROXY_AUTHENTICATION && proxyServer.equals(proxyHost) && proxySettings.PROXY_PORT == proxyPort) {
      // first try credentials from the settings
      credentials.set(new Credentials(proxySettings.getProxyLogin(), proxySettings.getPlainProxyPassword()));
      if (credentials.get().getUserName() != null && credentials.get().getPassword() != null) {
        return credentials.get();
      }
    }
    // then ask the user for credentials
    if (!GraphicsEnvironment.isHeadless() && !JBCefApp.isOffScreenRenderingMode()) {
      String proxy = proxyServer + ":" + proxyPort;
      Runnable runnable = () -> {
        // 3) finally ask the user for credentials
        DataContext dataContext = DataManager.getInstance().getDataContext(browser.getCefBrowser().getUIComponent());
        MyLoginDialog dialog = new MyLoginDialog(CommonDataKeys.PROJECT.getData(dataContext), proxy, proxySettings.getProxyLogin());
        dialog.show();
        credentials.set(dialog.getCredentials());
      };
      if (EventQueue.isDispatchThread()) {
        runnable.run();
      }
      else {
        try {
          EventQueue.invokeAndWait(runnable);
        }
        catch (Throwable e) {
          Logger.getInstance(JBCefProxyAuthenticator.class).error(e);
        }
      }
    }
    return credentials.get();
  }

  private static class MyLoginDialog extends DialogWrapper {
    private final @NotNull JBTextField myLoginField = new JBTextField();
    private final @NotNull JBPasswordField myPasswordField = new JBPasswordField();

    private final @NotNull String myProxy;
    private final @Nullable String myLogin;

    private volatile Credentials myCredentials;

    MyLoginDialog(@Nullable Project project, @NotNull String proxy, @Nullable String login) {
      super(project, false, IdeModalityType.PROJECT);
      myProxy = proxy;
      myLogin = login;
      setResizable(false);
      setTitle(IdeBundle.message("dialog.title.jcef.proxyAuthentication"));
      setOKButtonText(IdeBundle.message("dialog.button.ok.jcef.signIn"));
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
      JPanel panel = new JPanel(new GridBagLayout());
      GridBagConstraints gc = new GridBagConstraints();

      gc.fill = GridBagConstraints.HORIZONTAL;

      GridHelper gh = (gridx, gridy, gridwidth, comp) -> {
        gc.gridx = gridx;
        gc.gridy = gridy;
        gc.gridwidth = gridwidth;
        panel.add(comp, gc);
      };

      gh.add(0, 0, 3,
             new JBLabel(IdeBundle.message("dialog.content.jcef.proxyServer", myProxy)));
      gh.add(0, 1, 3,
             new Box.Filler(new Dimension(1, JBUIScale.scale(20)),
                            new Dimension(1, JBUIScale.scale(20)),
                            new Dimension(Short.MAX_VALUE, Short.MAX_VALUE)));
      gh.add(0, 2, 1,
             new JBLabel(IdeBundle.message("dialog.content.label.jcef.login"), SwingConstants.RIGHT));
      gh.add(1, 2, 2,
             myLoginField);
      gh.add(0, 3, 1,
             new JBLabel(IdeBundle.message("dialog.content.label.jcef.password"), SwingConstants.RIGHT));
      gh.add(1, 3, 2,
             myPasswordField);

      if (myLogin != null) myLoginField.setText(myLogin);
      return panel;
    }

    public @Nullable Credentials getCredentials() {
      return myCredentials;
    }

    @Override
    protected @NotNull Action getOKAction() {
      return new DialogWrapper.OkAction() {
        @Override
        protected void doAction(ActionEvent e) {
          myCredentials = new Credentials(myLoginField.getText(), String.valueOf(myPasswordField.getPassword()));
          super.doAction(e);
        }
      };
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
      return myLogin == null ? myLoginField : myPasswordField;
    }

    @Override
    public void show() {
      init();
      super.show();
    }
  }

  @FunctionalInterface
  private interface GridHelper {
    void add(int gridx, int gridy, int gridwidth, Component comp);
  }
}
