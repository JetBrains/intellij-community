// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.help.impl;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.application.IdeUrlTrackingParametersProvider;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.help.WebHelpProvider;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.platform.ide.customization.ExternalProductResourceUrls;
import com.intellij.util.Url;
import kotlin.jvm.functions.Function1;
import org.apache.http.client.utils.URIBuilder;
import org.jetbrains.annotations.Nullable;

import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class HelpManagerImpl extends HelpManager {
  private static final ExtensionPointName<WebHelpProvider>
    WEB_HELP_PROVIDER_EP_NAME = ExtensionPointName.create("com.intellij.webHelpProvider");

  @Override
  public void invokeHelp(@Nullable String id) {
    logWillOpenHelpId(id);
    String helpUrl = getHelpUrl(id);
    if (helpUrl != null) {
      BrowserUtil.browse(helpUrl);
    }
  }

  public static @Nullable String getHelpUrl(@Nullable String id) {
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

    Function1<String, Url> urlSupplier = ExternalProductResourceUrls.getInstance().getHelpPageUrl();
    if (urlSupplier == null) return null;

    final KeymapManager manager = KeymapManager.getInstance();
    final Keymap activeKeymap = manager == null ? null : KeymapManager.getInstance().getActiveKeymap();

    final String urlWithUtm = IdeUrlTrackingParametersProvider.getInstance().augmentUrl(urlSupplier.invoke(id).toExternalForm());
    if (activeKeymap == null) return urlWithUtm;
    try {
      return new URIBuilder(urlWithUtm).setParameter("keymap", activeKeymap.getPresentableName()).build().toString();
    }
    catch (URISyntaxException e) {
      return urlWithUtm;
    }
  }
}
