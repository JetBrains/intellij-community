// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers;

import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.Url;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public abstract class WebBrowserService {
  public static WebBrowserService getInstance() {
    return ApplicationManager.getApplication().getService(WebBrowserService.class);
  }

  public abstract @NotNull Collection<Url> getUrlsToOpen(@NotNull OpenInBrowserRequest request, boolean preferLocalUrl) throws WebBrowserUrlProvider.BrowserException;

  @SuppressWarnings("unused")
  public static boolean isHtmlOrXmlLanguage(@NotNull Language language) {
    return WebBrowserXmlService.getInstance().isHtmlOrXmlLanguage(language);
  }
}