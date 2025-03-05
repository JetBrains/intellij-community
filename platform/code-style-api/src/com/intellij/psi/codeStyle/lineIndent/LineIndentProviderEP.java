// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle.lineIndent;

import com.intellij.lang.Language;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

/**
 * Line indent provider extension point
 */
public final class LineIndentProviderEP {
  private static final ExtensionPointName<LineIndentProvider> EP_NAME = ExtensionPointName.create("com.intellij.lineIndentProvider");

  public static @Nullable LineIndentProvider findLineIndentProvider(@Nullable Language language) {
    return ContainerUtil.find(EP_NAME.getExtensionList(), provider -> provider.isSuitableFor(language));
  }
}
