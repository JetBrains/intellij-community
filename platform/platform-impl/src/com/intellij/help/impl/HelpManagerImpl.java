// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.help.impl;

import com.intellij.ide.BrowserUtil;
import com.intellij.ide.browsers.BrowserLauncher;
import com.intellij.ide.browsers.WebBrowserManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.IdeUrlTrackingParametersProvider;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.help.HelpTarget;
import com.intellij.openapi.help.HelpTargetProvider;
import com.intellij.openapi.help.KeymapHelpIdPresenter;
import com.intellij.openapi.help.WebHelpProvider;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.platform.ide.customization.ExternalProductResourceUrls;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

import static java.util.Objects.requireNonNullElse;

public class HelpManagerImpl extends HelpManager {
  private static final ExtensionPointName<WebHelpProvider> EP_NAME = ExtensionPointName.create("com.intellij.webHelpProvider");
  private static final ExtensionPointName<HelpTargetProvider> TARGET_EP = ExtensionPointName.create("com.intellij.helpTargetProvider");

  @Override
  public void invokeHelp(@Nullable String id) {
    logWillOpenHelpId(id);
    var target = getHelpTarget(id);
    if (target != null) {
      var browser = WebBrowserManager.getInstance().findBrowserById(target.getBrowserId());
      if (browser == null) {
        BrowserUtil.browse(target.getUrl());
      }
      else {
        BrowserLauncher.getInstance().browse(target.getUrl(), browser);
      }
      JupyterHelpUsageCollector.logOpened(id);
    }
  }

  public static @Nullable String getHelpUrl(@Nullable String id) {
    var target = getHelpTarget(id);
    return target == null ? null : target.getUrl();
  }

  private static @Nullable HelpTarget getHelpTarget(@Nullable String id) {
    id = requireNonNullElse(id, "top");

    for (var provider : EP_NAME.getExtensionList()) {
      var prefix = provider.getHelpTopicPrefix();
      if (!prefix.isEmpty() && id.startsWith(prefix)) {
        var url = provider.getHelpPageUrl(id);
        if (url != null) {
          return new HelpTarget(url);
        }
      }
    }

    for (var provider : TARGET_EP.getExtensionList()) {
      var target = provider.getHelpTarget(id);
      if (target != null) return target;
    }

    if (MacHelpUtil.isApplicable() && MacHelpUtil.invokeHelp(id)) {
      return null;
    }

    var url = getDefaultHelpUrl(id);
    return url == null ? null : new HelpTarget(url);
  }

  /** Returns the product web URL without invoking help providers or opening a browser. */
  @ApiStatus.Internal
  public static @Nullable String getDefaultHelpUrl(@Nullable String id) {
    var urlSupplier = ExternalProductResourceUrls.getInstance().getHelpPageUrl();
    if (urlSupplier == null) return null;
    var url = urlSupplier.invoke(requireNonNullElse(id, "top"));

    Keymap activeKeymap = KeymapManagerEx.getInstanceEx().getActiveKeymap();
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
