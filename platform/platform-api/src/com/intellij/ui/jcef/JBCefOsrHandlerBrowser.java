// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefBrowserOsrWithHandler;
import org.cef.handler.CefRenderHandler;
import org.jetbrains.annotations.NotNull;

import java.awt.event.KeyEvent;

/**
 * A wrapper over {@link CefBrowser} that forwards everything to {@link CefRenderHandler} so you can render everything
 * and send events back using {@link CefBrowser#sendKeyEvent(KeyEvent)}
 * <p>
 * Use {@link #loadURL(String)} or {@link #loadHTML(String)} for loading.
 * <p>
 * If you need to render to be done by CEDF, see {@link JBCefBrowser}
 *
 * @see JBCefBrowser
 */
public final class JBCefOsrHandlerBrowser extends JBCefBrowserBase {
  @NotNull
  public static JBCefOsrHandlerBrowser create(@NotNull String url, @NotNull CefRenderHandler renderHandler) {
    var client = JBCefApp.getInstance().createClient();
    var cefBrowser = new CefBrowserOsrWithHandler(client.getCefClient(), url, null, renderHandler);
    return new JBCefOsrHandlerBrowser(client, cefBrowser, true, true);
  }

  private JBCefOsrHandlerBrowser(@NotNull JBCefClient cefClient,
                                 @NotNull CefBrowser cefBrowser, boolean newBrowserCreated, boolean isDefaultClient) {
    super(cefClient, cefBrowser, newBrowserCreated, isDefaultClient);
  }
}
