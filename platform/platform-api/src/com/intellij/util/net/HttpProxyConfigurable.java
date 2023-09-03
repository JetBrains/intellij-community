// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.net;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.options.ConfigurableBase;
import org.jetbrains.annotations.NotNull;

public class HttpProxyConfigurable extends ConfigurableBase<HttpProxySettingsUi, HttpConfigurable> {
  private final HttpConfigurable settings;

  public HttpProxyConfigurable() {
    this(HttpConfigurable.getInstance());
  }

  public HttpProxyConfigurable(@NotNull HttpConfigurable settings) {
    super("http.proxy", IdeBundle.message("http.proxy.configurable"), "http.proxy");

    this.settings = settings;
  }

  @Override
  protected @NotNull HttpConfigurable getSettings() {
    return settings;
  }

  @Override
  protected HttpProxySettingsUi createUi() {
    return new HttpProxySettingsUi(settings);
  }
}