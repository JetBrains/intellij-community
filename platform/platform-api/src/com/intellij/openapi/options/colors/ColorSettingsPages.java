// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options.colors;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.Nullable;

/**
 * Registry for custom pages shown in the "Colors and Fonts" settings dialog.
 */
public abstract class ColorSettingsPages {
  /**
   * Gets the global instance of the registry.
   *
   * @return the registry instance.
   */
  public static ColorSettingsPages getInstance() {
    return ApplicationManager.getApplication().getService(ColorSettingsPages.class);
  }

  /**
   * Registers a custom page for the "Colors and Fonts" settings dialog.
   *
   * @param page the instance of the page to register.
   *
   * Used only in special cases when pages are registered dynamically (Rider).
   * Otherwise pages should be registered as extensions with {@link ColorSettingsPage#EP_NAME}
   */
  public abstract void registerPage(ColorSettingsPage page);

  /**
   * Returns the list of all registered pages in the "Colors and Fonts" dialog.
   *
   * @return the list of registered pages.
   */
  public abstract ColorSettingsPage[] getRegisteredPages();

  @Nullable
  public abstract Pair<ColorAndFontDescriptorsProvider, AttributesDescriptor> getAttributeDescriptor(TextAttributesKey key);

  @Nullable
  public abstract Pair<ColorAndFontDescriptorsProvider, ColorDescriptor> getColorDescriptor(ColorKey key);

}
