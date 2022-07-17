// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.help.impl;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.application.IdeUrlTrackingParametersProvider;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.help.WebHelpProvider;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.Nullable;

public class HelpManagerImpl extends HelpManager {
  private static final ExtensionPointName<WebHelpProvider>
    WEB_HELP_PROVIDER_EP_NAME = ExtensionPointName.create("com.intellij.webHelpProvider");

  @Override
  public void invokeHelp(@Nullable String id) {
    String helpUrl = getHelpUrl(id);
    if (helpUrl != null) {
      BrowserUtil.browse(helpUrl);
    }
  }

  public @Nullable String getHelpUrl(@Nullable String id) {
    id = StringUtil.notNullize(id, "top");

    for (WebHelpProvider provider : WEB_HELP_PROVIDER_EP_NAME.getExtensions()) {
      if (id.startsWith(provider.getHelpTopicPrefix())) {
        String url = provider.getHelpPageUrl(id);
        if (url != null) {
          return url;
        }
      }
    }

    if (MacHelpUtil.isApplicable() && MacHelpUtil.invokeHelp(id)) {
      return null;
    }

    ApplicationInfoEx info = ApplicationInfoEx.getInstanceEx();
    String productVersion = info.getShortVersion();

    String url = info.getWebHelpUrl();
    if (!url.endsWith("/")) url += "/";
    url += productVersion + "/?" + URLUtil.encodeURIComponent(id);

    return IdeUrlTrackingParametersProvider.getInstance().augmentUrl(url);
  }
}
