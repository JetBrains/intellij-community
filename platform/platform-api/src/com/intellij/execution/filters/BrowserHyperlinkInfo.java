// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.filters;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Consider to use {@link com.intellij.ide.browsers.OpenUrlHyperlinkInfo}.
 */
public final class BrowserHyperlinkInfo implements HyperlinkInfo {
  private final String myUrl;

  public BrowserHyperlinkInfo(String url) {
    myUrl = url;
  }

  @Override
  public void navigate(@NotNull Project project) {
    BrowserUtil.browse(myUrl);
  }
}
