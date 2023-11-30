// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.customFolding;

import com.intellij.lang.LangBundle;
import com.intellij.lang.folding.CustomFoldingProvider;
import com.intellij.openapi.util.text.StringUtil;

/**
 * Supports <a href="http://msdn.microsoft.com/en-us/library/9a1ybwek%28v=vs.100%29.aspx">VisualStudio custom foldings.</a>
 */
public final class VisualStudioCustomFoldingProvider extends CustomFoldingProvider {
  @Override
  public boolean isCustomRegionStart(String elementText) {
    return elementText.contains("region") && elementText.matches("[/*#-]*\\s*region.*");
  }

  @Override
  public boolean isCustomRegionEnd(String elementText) {
    return elementText.contains("endregion") && elementText.matches("[/*#-]*\\s*endregion.*");
  }

  @Override
  public String getPlaceholderText(String elementText) {
    String textAfterMarker = elementText.replaceFirst("[/*#-]*\\s*region(.*)", "$1");
    String result = elementText.startsWith("/*") ? StringUtil.trimEnd(textAfterMarker, "*/").trim() : textAfterMarker.trim();
    return result.isEmpty() ? "..." : result;
  }

  @Override
  public String getDescription() {
    //noinspection DialogTitleCapitalization -- region..endregion are standard keywords
    return LangBundle.message("custom.folding.comments.vs.description");
  }

  @Override
  public String getStartString() {
    return "region ?";
  }

  @Override
  public String getEndString() {
    return "endregion";
  }
}
