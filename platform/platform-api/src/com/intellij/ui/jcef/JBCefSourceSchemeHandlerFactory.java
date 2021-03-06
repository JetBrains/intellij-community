// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefSchemeRegistrar;
import org.cef.handler.CefResourceHandler;
import org.cef.network.CefRequest;
import org.jetbrains.annotations.NotNull;

final class JBCefSourceSchemeHandlerFactory implements JBCefApp.JBCefCustomSchemeHandlerFactory {
  public static final String SOURCE_SCHEME = "source";

  @Override
  public void registerCustomScheme(@NotNull CefSchemeRegistrar registrar) {
    registrar.addCustomScheme(SOURCE_SCHEME, true, true, false, false, false, false, false);
  }

  @Override
  public @NotNull String getSchemeName() {
    return SOURCE_SCHEME;
  }

  @Override
  public @NotNull String getDomainName() {
    return "";
  }

  @Override
  public CefResourceHandler create(CefBrowser browser, CefFrame frame, String schemeName, CefRequest request) {
    return new JBCefSourceSchemeHandler();
  }
}
