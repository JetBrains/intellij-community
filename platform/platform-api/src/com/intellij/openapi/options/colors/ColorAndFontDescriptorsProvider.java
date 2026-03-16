// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.colors;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Defines interface for extending set of text/color descriptors operated by color schemes. 
 */
public interface ColorAndFontDescriptorsProvider {

  ExtensionPointName<ColorAndFontDescriptorsProvider> EP_NAME = ExtensionPointName.create("com.intellij.colorAndFontDescriptorProvider");
  
  /**
   * Returns the list of descriptors specifying the {@link TextAttributesKey} instances
   * for which colors are specified in the page. For such attribute keys, the user can choose
   * all highlighting attributes (font type, background color, foreground color, error stripe color and
   * effects).
   *
   * @return the array of attribute descriptors.
   */
  AttributesDescriptor @NotNull [] getAttributeDescriptors();

  /**
   * Returns the list of descriptors specifying the {@link com.intellij.openapi.editor.colors.ColorKey}
   * instances for which colors are specified in the page. For such color keys, the user can
   * choose only the background or foreground color.
   *
   * @return the array of color descriptors.
   */
  ColorDescriptor @NotNull [] getColorDescriptors();

  /**
   * Returns the title of the page, shown as text in the dialog tab.
   *
   * @return the title of the custom page.
   */
  @NotNull
  @NlsContexts.ConfigurableName
  String getDisplayName();

  @NotNull
  @NonNls
  default String getId() {
    return this.getClass().getName();
  }
}
