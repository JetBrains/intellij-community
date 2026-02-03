// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.customFolding;

import com.intellij.lang.LangBundle;
import com.intellij.lang.folding.CustomFoldingProvider;
import org.jetbrains.annotations.ApiStatus;

/**
 * Custom folding provider for <a href="http://ui.netbeans.org/docs/ui/code_folding/cf_uispec.html#menus">NetBeans folding conventions.</a>
 */
@ApiStatus.Internal
public final class NetBeansCustomFoldingProvider extends CustomFoldingProvider {
  @Override
  public boolean isCustomRegionStart(String elementText) {
    return elementText.contains("<editor-fold");
  }

  @Override
  public boolean isCustomRegionEnd(String elementText) {
    return elementText.contains("</editor-fold");
  }

  @Override
  public String getPlaceholderText(String elementText) {
    String customText = elementText.replaceFirst(".*desc\\s*=\\s*\"([^\"]*)\".*", "$1").trim();
    return customText.isEmpty() ? "..." : customText;
  }

  @Override
  public String getDescription() {
    //noinspection DialogTitleCapitalization -- <editor-fold...> is a part of syntax
    return LangBundle.message("custom.folding.comments.net.beans.description");
  }

  @Override
  public String getStartString() {
    return "<editor-fold desc=\"?\">";
  }

  @Override
  public String getEndString() {
    return "</editor-fold>";
  }

  @Override
  public boolean isCollapsedByDefault(String text) {
    return super.isCollapsedByDefault(text) || text.matches(".*defaultstate\\s*=\\s*\"collapsed\".*");
  }
}
