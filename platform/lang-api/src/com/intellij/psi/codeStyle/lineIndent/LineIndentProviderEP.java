// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle.lineIndent;

import com.intellij.lang.Language;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.Nullable;

/**
 * Line indent provider extension point
 */
public final class LineIndentProviderEP {
  private final static ExtensionPointName<LineIndentProvider> EP_NAME = ExtensionPointName.create("com.intellij.lineIndentProvider");

  @Nullable
  public static LineIndentProvider findLineIndentProvider(@Nullable Language language) {
    return EP_NAME.getExtensionList().stream().filter(provider -> provider.isSuitableFor(language)).findFirst().orElse(null);
  }
}
