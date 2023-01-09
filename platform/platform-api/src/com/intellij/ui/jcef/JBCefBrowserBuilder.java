// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef;

import com.intellij.openapi.util.registry.RegistryManager;
import org.cef.browser.CefBrowser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A builder for creating {@link JBCefBrowser}.
 *
 * @author tav
 * @see JBCefBrowser#createBuilder
 */
public class JBCefBrowserBuilder {
  @Nullable JBCefClient myClient;
  @Nullable String myUrl;
  @Nullable CefBrowser myCefBrowser;
  @Nullable JBCefOSRHandlerFactory myOSRHandlerFactory;
  boolean myIsOffScreenRendering = RegistryManager.getInstance().is("ide.browser.jcef.osr.enabled");
  boolean myCreateImmediately;
  boolean myEnableOpenDevToolsMenuItem;
  boolean myMouseWheelEventEnable = true;

  /**
   * Sets whether the browser is rendered off-screen.
   * <p>
   * When set to {@code true} a buffered rendering is used onto a lightweight Swing component.
   * To override, use {@link #setOSRHandlerFactory(JBCefOSRHandlerFactory)}.
   * <p>
   * When not set (or {@code false}) the windowed mode is used to render the browser.
   *
   * @see #setOSRHandlerFactory(JBCefOSRHandlerFactory)
   */
  public @NotNull JBCefBrowserBuilder setOffScreenRendering(boolean isOffScreenRendering) {
    myIsOffScreenRendering = isOffScreenRendering;
    return this;
  }

  /**
   * Sets the client.
   * <p>
   * When not set, the default client is created (which will be disposed automatically).
   * <p>
   * The disposal of the provided client is the responsibility of the caller.
   */
  public @NotNull JBCefBrowserBuilder setClient(@Nullable JBCefClient client) {
    myClient = client;
    return this;
  }

  /**
   * Sets the initial URL to load.
   * <p>
   * When not set, no initial URL is loaded.
   *
   * @see JBCefBrowserBase#loadURL(String)
   */
  public @NotNull JBCefBrowserBuilder setUrl(@Nullable String url) {
    myUrl = url;
    return this;
  }

  /**
   * Sets the browser to wrap.
   * <p>
   * When not set, the default browser is created.
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
   * <p>
   * When not set (or {@code false{}}) the native browser is created when the browser's component is added to a UI hierarchy.
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
   * <p>
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
   * <p>
   * When not set, the item is not present.
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

  /**
   * If {@code true}, the browser will intercept mouse wheel events. Otherwise, the browser won't react on scrolling,
   * and the parent component will handle scroll events.
   * <p>
   * Default is {@code true}.
   */
  public @NotNull JBCefBrowserBuilder setMouseWheelEventEnable(boolean mouseWheelEventEnable) {
    myMouseWheelEventEnable = mouseWheelEventEnable;
    return this;
  }
}
