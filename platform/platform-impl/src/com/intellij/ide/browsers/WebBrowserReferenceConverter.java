// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers;

import com.intellij.util.xmlb.Converter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class WebBrowserReferenceConverter extends Converter<WebBrowser> {
  @Override
  public @Nullable WebBrowser fromString(@NotNull String value) {
    return WebBrowserManager.getInstance().findBrowserById(value);
  }

  @Override
  public @NotNull String toString(@NotNull WebBrowser browser) {
    return browser.getId().toString();
  }
}