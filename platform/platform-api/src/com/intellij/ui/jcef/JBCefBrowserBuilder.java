// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import org.cef.browser.CefBrowser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.ui.jcef.JBCefBrowserBase.RenderingType;

/**
 * @author tav
 */
public class JBCefBrowserBuilder {
  @Nullable RenderingType myRenderingType;
  @Nullable JBCefClient myClient;
  @Nullable String myUrl;
  @Nullable CefBrowser myCefBrowser;
  @Nullable JBCefOSRHandlerFactory myOSRHandlerFactory;
  boolean myCreateImmediately;

  /**
   * Sets the rendering type.
   * <p></p>
   * When not set the default {@link RenderingType#EMBEDDED_WINDOW} type is used.
   */
  public @NotNull JBCefBrowserBuilder setRenderingType(@NotNull RenderingType type) {
    myRenderingType = type;
    return this;
  }

  /**
   * Sets the client.
   * <p></p>
   * When not set the default client is created (disposed automatically).
   * <p>
   * The disposal of the provided client is the responsibility of the caller.
   */
  public @NotNull JBCefBrowserBuilder setClient(@NotNull JBCefClient client) {
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
  public @NotNull JBCefBrowserBuilder setUrl(@NotNull String url) {
    myUrl = url;
    return this;
  }

  /**
   * Sets the browser to wrap.
   * <p></p>
   * When not set the default browser is created corresponding to the provided {@link JBCefBrowser.RenderingType}.
   * <p>
   * Use this option to set a browser like DevTools.
   *
   * @see CefBrowser#getDevTools
   */
  public @NotNull JBCefBrowserBuilder setCefBrowser(@NotNull CefBrowser browser) {
    myCefBrowser = browser;
    return this;
  }

  /**
   * Sets whether the native browser should be created immediately.
   * <p></p>
   * When not set the native browser is created when the browser's component is added to a UI hierarchy.
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
   * Used only with {@link RenderingType#BUFFERED_IMAGE}, otherwise ignored.
   *
   * @see #setRenderingType(RenderingType)
   */
  public @NotNull JBCefBrowserBuilder setOSRHandlerFactory(@NotNull JBCefOSRHandlerFactory factory) {
    myOSRHandlerFactory = factory;
    return this;
  }

  /**
   * Creates the browser with the set parameters.
   */
  public @NotNull JBCefBrowser createBrowser() {
    return JBCefBrowser.create(this);
  }
}
