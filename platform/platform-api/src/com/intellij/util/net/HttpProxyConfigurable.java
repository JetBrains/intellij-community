// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableBase;
import com.intellij.openapi.options.ConfigurableUi;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.util.net.internal.ProxyMigrationService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class HttpProxyConfigurable extends ConfigurableBase<ConfigurableUi<ProxySettings>, ProxySettings> implements Configurable.NoMargin {
  private final ProxySettings proxySettings;
  private final ProxyCredentialStore credentialStore;
  private final DisabledProxyAuthPromptsManager disabledProxyAuthPromptsManager;

  public HttpProxyConfigurable() {
    super("http.proxy", IdeBundle.message("http.proxy.configurable"), "http.proxy");

    this.proxySettings = ProxySettings.getInstance();
    this.credentialStore = ProxyCredentialStore.getInstance();
    this.disabledProxyAuthPromptsManager = DisabledProxyAuthPromptsManager.getInstance();
  }

  @Override
  protected @NotNull ProxySettings getSettings() {
    return proxySettings;
  }

  @Override
  protected ConfigurableUi<ProxySettings> createUi() {
    return ProxyMigrationService.getInstance().createProxySettingsUi(proxySettings, credentialStore, disabledProxyAuthPromptsManager);
  }

  public static boolean editConfigurable(@Nullable JComponent parent) {
    return ShowSettingsUtil.getInstance().editConfigurable(parent, new HttpProxyConfigurable());
  }
}
