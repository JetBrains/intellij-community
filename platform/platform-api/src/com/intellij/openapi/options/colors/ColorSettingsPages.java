// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
   * <p>
   * Used only in special cases when pages are registered dynamically (Rider).
   * Otherwise, pages should be registered as extensions with {@link ColorSettingsPage#EP_NAME}
   */
  public abstract void registerPage(ColorSettingsPage page);

  /**
   * Returns the list of all registered pages in the "Colors and Fonts" dialog.
   *
   * @return the array of registered pages.
   */
  public abstract ColorSettingsPage[] getRegisteredPages();

  public abstract @Nullable Pair<ColorAndFontDescriptorsProvider, AttributesDescriptor> getAttributeDescriptor(TextAttributesKey key);

  public abstract @Nullable Pair<ColorAndFontDescriptorsProvider, ColorDescriptor> getColorDescriptor(ColorKey key);

}
