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
  boolean myCreateImmediately;

  /**
   * Sets the rendering type.
   */
  public @NotNull JBCefBrowserBuilder setRenderingType(@NotNull RenderingType type) {
    myRenderingType = type;
    return this;
  }

  /**
   * Sets the client.
   * <p></p>
   * The disposal of the provided client is the responsibility of the caller.
   * When not set a default client is created (disposed automatically).
   */
  public @NotNull JBCefBrowserBuilder setClient(@NotNull JBCefClient client) {
    myClient = client;
    return this;
  }

  /**
   * Sets the initial URL to load.
   * <p></p>
   * It's ok to leave it empty.
   */
  public @NotNull JBCefBrowserBuilder setUrl(@NotNull String url) {
    myUrl = url;
    return this;
  }

  /**
   * Sets the browser to wrap.
   * <p></p>
   * When not set a default browser is created corresponding to the provided {@link JBCefBrowser.RenderingType}.
   * Rely on the default browser unless a special browser (like DevTools) should be created.
   */
  public @NotNull JBCefBrowserBuilder setCefBrowser(@NotNull CefBrowser browser) {
    myCefBrowser = browser;
    return this;
  }

  /**
   * Sets whether the native browser should be created immediately, but not when the browser's component is added to a UI hierarchy.
   *
   * @see CefBrowser#createImmediately
   * @see JBCefBrowserBase#getComponent
   */
  public @NotNull JBCefBrowserBuilder setCreateImmediately(boolean createImmediately) {
    myCreateImmediately = createImmediately;
    return this;
  }

  public @NotNull JBCefBrowser createBrowser() {
    return JBCefBrowser.create(this);
  }
}
