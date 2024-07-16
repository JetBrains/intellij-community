// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.Function;
import org.cef.handler.CefRenderHandler;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

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
  default @NotNull JComponent createComponent(boolean isMouseWheelEventEnabled) {
    return new JBCefOsrComponent(isMouseWheelEventEnabled);
  }

  /**
   * Creates the OSR handler.
   */
  default @NotNull CefRenderHandler createCefRenderHandler(@NotNull JComponent component) {
    assert component instanceof JBCefOsrComponent;
    JBCefOsrComponent osrComponent = (JBCefOsrComponent)component;
    Function<? super JComponent, ? extends Rectangle> screenBoundsProvider = createScreenBoundsProvider();
    return new JBCefOsrHandler(osrComponent, screenBoundsProvider);
  }

  /**
   * Creates a screen bounds provider which takes into account the passed component.
   * <p></p>
   * Override the method in the headless mode to provide meaningful screen bounds.
   *
   * @see GraphicsEnvironment#isHeadless
   */
  default @NotNull Function<? super JComponent, ? extends Rectangle> createScreenBoundsProvider() {
    return component -> {
      if (component != null && !GraphicsEnvironment.isHeadless()) {
        try {
          return component.isShowing() ?
                 component.getGraphicsConfiguration().getDevice().getDefaultConfiguration().getBounds() :
                 GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().getBounds();
        }
        catch (Exception e) {
          Logger.getInstance(JBCefOsrHandler.class).error(e);
        }
      }
      return new Rectangle(0, 0, 0, 0);
    };
  }
}
