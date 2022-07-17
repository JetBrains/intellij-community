// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

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
  /**
   * Default implementation provides buffered rendering onto a lightweight Swing component.
   */
  @NotNull JBCefOSRHandlerFactory DEFAULT = new JBCefOSRHandlerFactory() {};

  /**
   * Creates a lightweight component on which the browser will be rendered.
   *
   * @see JBCefBrowserBase#getComponent
   */
  default @NotNull JComponent createComponent() {
    return new JBCefOsrComponent();
  }

  /**
   * Creates the OSR handler.
   */
  default @NotNull CefRenderHandler createCefRenderHandler(@NotNull JComponent component) {
    assert component instanceof JBCefOsrComponent;
    return new JBCefOsrHandler((JBCefOsrComponent)component, createScreenBoundsProvider());
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
