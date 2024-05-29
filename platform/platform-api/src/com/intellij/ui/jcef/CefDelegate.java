// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.jcef;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.cef.browser.CefBrowser;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * If 'active', replaces the default JCEF implementation to be used by IntellJ platform.
 */
@ApiStatus.Internal
public interface CefDelegate {
  ExtensionPointName<CefDelegate> EP = ExtensionPointName.create("com.intellij.cefDelegate");

  /**
   * Whether this delegate should actually be used to provide JCEF support.
   */
  boolean isActive();

  boolean isCefSupported();
  @NotNull JBCefClient createClient();
  @NotNull CefBrowser createBrowser(@NotNull String url);
}
