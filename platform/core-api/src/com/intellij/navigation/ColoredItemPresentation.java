// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.navigation;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import org.jetbrains.annotations.Nullable;

public interface ColoredItemPresentation extends ItemPresentation {
  /**
   * Returns the text attributes for rendering the item text.
   *
   * @return the text attributes, or null if default text attributes should be used
   */
  @Nullable
  TextAttributesKey getTextAttributesKey();
}
