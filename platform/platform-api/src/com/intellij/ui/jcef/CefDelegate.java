// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefMessageRouter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * If 'active', replaces the default JCEF implementation to be used by IntelliJ platform.
 */
@ApiStatus.Internal
public interface CefDelegate {
  ExtensionPointName<CefDelegate> EP = ExtensionPointName.create("com.intellij.cefDelegate");

  /**
   * Whether this delegate should actually be used to provide JCEF support.
   */
  boolean isActive();

  boolean isCefSupported();
  @NotNull CefClient createClient();
  boolean isInitialized(@NotNull CefBrowser browser);
  @NotNull CefMessageRouter createMessageRouter(@Nullable CefMessageRouter.CefMessageRouterConfig config);
  void disableNavigation(@NotNull CefBrowser browser);
}
