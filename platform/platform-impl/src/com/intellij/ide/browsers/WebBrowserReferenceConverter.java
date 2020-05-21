package com.intellij.ide.browsers;

import com.intellij.util.xmlb.Converter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class WebBrowserReferenceConverter extends Converter<WebBrowser> {
  @Nullable
  @Override
  public WebBrowser fromString(@NotNull String value) {
    return WebBrowserManager.getInstance().findBrowserById(value);
  }

  @NotNull
  @Override
  public String toString(@NotNull WebBrowser browser) {
    return browser.getId().toString();
  }
}