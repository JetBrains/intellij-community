// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.help.impl;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.IdeUrlTrackingParametersProvider;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.help.KeymapHelpIdPresenter;
import com.intellij.openapi.help.WebHelpProvider;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.platform.ide.customization.ExternalProductResourceUrls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

import static java.util.Objects.requireNonNullElse;
import static com.intellij.internal.statistic.utils.PluginInfoDetectorKt.getPluginInfo;

public class HelpManagerImpl extends HelpManager {
  private static final ExtensionPointName<WebHelpProvider> EP_NAME = ExtensionPointName.create("com.intellij.webHelpProvider");

  @Override
  public void invokeHelp(@Nullable String id) {
    logWillOpenHelpId(id);
    var helpUrl = getHelpUrl(id);
    if (helpUrl != null) {
      HelpUsageCollector.logOpened(id);
      BrowserUtil.browse(helpUrl);
    }
  }

  record ResolvedHelpProviderUrl(@NotNull WebHelpProvider provider, @NotNull String url) {
    boolean isJetBrainsProvider() {
      return getPluginInfo(provider.getClass()).isDevelopedByJetBrains();
    }
  }

  static @Nullable ResolvedHelpProviderUrl findProviderFor(@NotNull String id) {
    for (var provider : EP_NAME.getExtensionList()) {
      var prefix = provider.getHelpTopicPrefix();
      if (!prefix.isEmpty() && id.startsWith(prefix)) {
        String url = provider.getHelpPageUrl(id);
        if (url != null) return new ResolvedHelpProviderUrl(provider, url);
      }
    }
    return null;
  }

  public static @Nullable String getHelpUrl(@Nullable String id) {
    id = requireNonNullElse(id, "top");
    var providerUrl = findProviderFor(id);
    if (providerUrl != null) return providerUrl.url();
    if (MacHelpUtil.isApplicable() && MacHelpUtil.invokeHelp(id)) {
      return null;
    }

    var urlSupplier = ExternalProductResourceUrls.getInstance().getHelpPageUrl();
    if (urlSupplier == null) return null;
    var url = urlSupplier.invoke(id);

    var activeKeymap = KeymapManagerEx.getInstanceEx().getActiveKeymap();
    if (activeKeymap.canModify()) {
      // if the user has a custom keymap, we need to show the predefined keymap it was inherited from
      activeKeymap = activeKeymap.getParent();
    }
    if (activeKeymap != null) {
      var keymapID = ApplicationManager.getApplication().getService(KeymapHelpIdPresenter.class).getKeymapIdForHelp(activeKeymap);
      url = url.addParameters(Map.of("keymap", keymapID));
    }

    return IdeUrlTrackingParametersProvider.getInstance().augmentUrl(url.toExternalForm());
  }
}