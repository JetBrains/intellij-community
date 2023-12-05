// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes;

import com.intellij.lang.LanguageExtensionPoint;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;

final class SyntaxHighlighterFactoryEP extends LanguageExtensionPoint<SyntaxHighlighterFactory> {
  /**
   * @deprecated use "language" attribute instead
   */
  @Deprecated
  @Attribute("key")
  public String key;

  @Override
  public @NotNull String getKey() {
    String result = super.getKey();
    return result == null ? key : result;
  }
}
