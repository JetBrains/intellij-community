// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableBase;
import com.intellij.openapi.options.ConfigurableUi;
import com.intellij.util.net.internal.HttpConfigurableMigrationUtilsKt;
import com.intellij.util.net.internal.ProxyMigrationService;
import org.jetbrains.annotations.NotNull;

public class HttpProxyConfigurable extends ConfigurableBase<ConfigurableUi<ProxySettings>, ProxySettings> implements Configurable.NoMargin {
  private final ProxySettings proxySettings;
  private final ProxyCredentialStore credentialStore;
  private final DisabledProxyAuthPromptsManager disabledProxyAuthPromptsManager;

  public HttpProxyConfigurable() {
    this(ProxySettings.getInstance());
  }

  public HttpProxyConfigurable(@NotNull ProxySettings proxySettings) {
    this(proxySettings, ProxyCredentialStore.getInstance(), DisabledProxyAuthPromptsManager.getInstance());
  }

  HttpProxyConfigurable(
    @NotNull ProxySettings proxySettings,
    @NotNull ProxyCredentialStore credentialStore,
    @NotNull DisabledProxyAuthPromptsManager disabledProxyAuthPromptsManager
  ) {
    super("http.proxy", IdeBundle.message("http.proxy.configurable"), "http.proxy");

    this.proxySettings = proxySettings;
    this.credentialStore = credentialStore;
    this.disabledProxyAuthPromptsManager = disabledProxyAuthPromptsManager;
  }

  @Override
  protected @NotNull ProxySettings getSettings() {
    return proxySettings;
  }

  @Override
  protected ConfigurableUi<ProxySettings> createUi() {
    return ProxyMigrationService.getInstance().createProxySettingsUi(proxySettings, credentialStore, disabledProxyAuthPromptsManager);
  }
}
