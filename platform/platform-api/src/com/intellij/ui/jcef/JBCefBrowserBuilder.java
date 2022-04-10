// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import org.cef.browser.CefBrowser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A builder for creating {@link JBCefBrowser}.
 *
 * @see JBCefBrowser#createBuilder
 * @author tav
 */
public class JBCefBrowserBuilder {
  @Nullable JBCefClient myClient;
  @Nullable String myUrl;
  @Nullable CefBrowser myCefBrowser;
  @Nullable JBCefOSRHandlerFactory myOSRHandlerFactory;
  boolean myIsOffScreenRendering;
  boolean myCreateImmediately;
  boolean myEnableOpenDevToolsMenuItem;

  /**
   * Sets whether the browser is rendered off-screen.
   * <p></p>
   * When set to true a buffered rendering is used onto a lightweight Swing component.
   * To override - use {@link #setOSRHandlerFactory(JBCefOSRHandlerFactory)}.
   * <p></p>
   * When not set (or false) the windowed mode is used to render the browser.
   *
   * @see #setOSRHandlerFactory(JBCefOSRHandlerFactory)
   */
  public @NotNull JBCefBrowserBuilder setOffScreenRendering(boolean isOffScreenRendering) {
    myIsOffScreenRendering = isOffScreenRendering;
    return this;
  }

  /**
   * Sets the client.
   * <p></p>
   * When not set the default client is created (which will be disposed automatically).
   * <p>
   * The disposal of the provided client is the responsibility of the caller.
   */
  public @NotNull JBCefBrowserBuilder setClient(@Nullable JBCefClient client) {
    myClient = client;
    return this;
  }

  /**
   * Sets the initial URL to load.
   * <p></p>
   * When not set no initial URL is loaded.
   *
   * @see JBCefBrowserBase#loadURL(String)
   */
  public @NotNull JBCefBrowserBuilder setUrl(@Nullable String url) {
    myUrl = url;
    return this;
  }

  /**
   * Sets the browser to wrap.
   * <p></p>
   * When not set the default browser is created.
   * <p>
   * Use this option to set a browser like DevTools.
   *
   * @see CefBrowser#getDevTools
   */
  public @NotNull JBCefBrowserBuilder setCefBrowser(@Nullable CefBrowser browser) {
    myCefBrowser = browser;
    return this;
  }

  /**
   * Sets whether the native browser should be created immediately.
   * <p></p>
   * When not set (or false) the native browser is created when the browser's component is added to a UI hierarchy.
   *
   * @see CefBrowser#createImmediately
   * @see JBCefBrowserBase#getComponent
   */
  public @NotNull JBCefBrowserBuilder setCreateImmediately(boolean createImmediately) {
    myCreateImmediately = createImmediately;
    return this;
  }

  /**
   * Sets the OSR handler factory.
   * <p></p>
   * When not set the {@link JBCefOSRHandlerFactory#DEFAULT} factory is used.
   * <p>
   * Used only with off-screen rendering, otherwise ignored.
   *
   * @see #setOffScreenRendering(boolean)
   */
  public @NotNull JBCefBrowserBuilder setOSRHandlerFactory(@Nullable JBCefOSRHandlerFactory factory) {
    myOSRHandlerFactory = factory;
    return this;
  }

  /**
   * Sets whether the "Open DevTools" item should be present in the context menu.
   * <p></p>
   * When not set the item is not present.
   */
  public @NotNull JBCefBrowserBuilder setEnableOpenDevToolsMenuItem(boolean enableOpenDevToolsMenuItem) {
    myEnableOpenDevToolsMenuItem = enableOpenDevToolsMenuItem;
    return this;
  }

  /**
   * @deprecated use {@link JBCefBrowserBuilder#build()} instead
   */
  @Deprecated
  public @NotNull JBCefBrowser createBrowser() {
    return build();
  }

  /**
   * Creates and returns the browser with the set parameters.
   */
  public @NotNull JBCefBrowser build() {
    return JBCefBrowser.create(this);
  }
}
