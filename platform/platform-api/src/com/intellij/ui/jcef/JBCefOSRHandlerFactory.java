// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef;

import com.intellij.openapi.application.ApplicationManager;
import org.cef.handler.CefRenderHandler;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * A factory for creating alternative component/handler/bounds for an off-screen rendering browser.
 *
 * @see JBCefBrowserBuilder#setOSRHandlerFactory(JBCefOSRHandlerFactory)
 * @see JBCefBrowserBuilder#setOffScreenRendering(boolean)
 * @author tav
 */
public interface JBCefOSRHandlerFactory {
  static JBCefOSRHandlerFactory getInstance() {
    return ApplicationManager.getApplication().getService(JBCefOSRHandlerFactory.class);
  }

  /**
   * Creates a lightweight component on which the browser will be rendered.
   *
   * @see JBCefBrowserBase#getComponent
   *
   * @param isMouseWheelEventEnabled If {@code true}, the browser will intercept mouse wheel events. Otherwise, the browser won't react
   *                                 on scrolling, and the parent component will handle scroll events.
   */
  @NotNull JComponent createComponent(boolean isMouseWheelEventEnabled);

  /**
   * Creates the OSR handler.
   */
  @NotNull CefRenderHandler createCefRenderHandler(@NotNull JComponent component);
}
