// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import org.cef.browser.CefBrowser;
import org.cef.browser.CefBrowserOsrWithHandler;
import org.cef.handler.CefRenderHandler;
import org.jetbrains.annotations.NotNull;

import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

/**
 * A wrapper over {@link CefBrowser} that forwards paint requests and notifications to a custom {@link CefRenderHandler}.
 * Key and mouse events are to be sent back to the {@link CefBrowser} via the callbacks: {@link CefBrowser#sendKeyEvent(KeyEvent)},
 * {@link CefBrowser#sendMouseEvent(MouseEvent)}, {@link CefBrowser#sendMouseWheelEvent(MouseWheelEvent)}.
 * <p></p>
 * Use {@link #loadURL(String)} or {@link #loadHTML(String)} for loading.
 * <p>
 * For window-based rendering use {@link JBCefBrowser}.
 *
 * @see JBCefBrowser
 * @see JBCefBrowser#getCefBrowser
 */
public final class JBCefOsrHandlerBrowser extends JBCefBrowserBase {
  /**
   * Creates the browser and immediately creates its native peer.
   * <p></p>
   * In order to use {@link JBCefJSQuery} create the browser via {@link #create(String, CefRenderHandler, boolean)} or
   * {@link #create(String, CefRenderHandler, JBCefClient, boolean, boolean)}.
   */
  @NotNull
  public static JBCefOsrHandlerBrowser create(@NotNull String url, @NotNull CefRenderHandler renderHandler) {
    return create(url, renderHandler, true);
  }

  /**
   * Creates the browser and creates its native peer depending on {@code createImmediately}.
   * <p></p>
   * For the browser to start loading call {@link #getCefBrowser()} and {@link CefBrowser#createImmediately()}.
   * <p></p>
   * In order to use {@link JBCefJSQuery} pass {@code createImmediately} as {@code false}, then call {@link CefBrowser#createImmediately()}
   * after all the JS queries are created.
   *
   * @see CefBrowser#createImmediately()
   */
  @NotNull
  public static JBCefOsrHandlerBrowser create(@NotNull String url, @NotNull CefRenderHandler renderHandler, boolean createImmediately) {
    return create(url, renderHandler, JBCefApp.getInstance().createClient(), true, createImmediately);
  }

  /**
   * Creates the browser with the provided {@link JBCefClient} and immediately creates its native peer.
   * <p></p>
   * In order to use {@link JBCefJSQuery} set {@link JBCefClient.Properties#JS_QUERY_POOL_SIZE} before passing the client.
   */
  @NotNull
  public static JBCefOsrHandlerBrowser create(@NotNull String url, @NotNull CefRenderHandler renderHandler, @NotNull JBCefClient client) {
    return create(url, renderHandler, client, false, true);
  }

  /**
   * Creates the browser and creates its native peer depending on {@code createImmediately}.
   * <p></p>
   * For the browser to start loading call {@link #getCefBrowser()} and {@link CefBrowser#createImmediately()}.
   * <p></p>
   * In order to use {@link JBCefJSQuery} pass {@code createImmediately} as {@code false}, then call {@link CefBrowser#createImmediately()}
   * after all the JS queries are created. Alternatively, pass {@code createImmediately} as {@code true} and set
   * {@link JBCefClient.Properties#JS_QUERY_POOL_SIZE} before passing the client.
   *
   * @see CefBrowser#createImmediately()
   */
  @NotNull
  public static JBCefOsrHandlerBrowser create(@NotNull String url, @NotNull CefRenderHandler renderHandler, @NotNull JBCefClient client, boolean createImmediately) {
    return create(url, renderHandler, client, false, createImmediately);
  }

  private static JBCefOsrHandlerBrowser create(@NotNull String url,
                                               @NotNull CefRenderHandler renderHandler,
                                               @NotNull JBCefClient client,
                                               boolean isDefaultClient,
                                               boolean createImmediately)
  {
    var cefBrowser = new CefBrowserOsrWithHandler(client.getCefClient(), url, null, renderHandler);
    if (createImmediately) cefBrowser.createImmediately();
    return new JBCefOsrHandlerBrowser(client, cefBrowser, true, isDefaultClient);
  }

  JBCefOsrHandlerBrowser(@NotNull JBCefClient cefClient, @NotNull CefBrowser cefBrowser, boolean newBrowserCreated, boolean isDefaultClient) {
    super(cefClient, cefBrowser, newBrowserCreated, isDefaultClient);

    JBCefApp.checkOffScreenRenderingModeEnabled();
    setProperty(Properties.IS_LIGHTWEIGHT, Boolean.TRUE);
  }
}
