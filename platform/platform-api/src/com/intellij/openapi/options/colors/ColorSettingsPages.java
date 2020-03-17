/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.options.colors;

import com.intellij.openapi.components.ServiceManager;
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
    return ServiceManager.getService(ColorSettingsPages.class);
  }

  /**
   * Registers a custom page for the "Colors and Fonts" settings dialog.
   *
   * @param page the instance of the page to register.
   *
   * Used only in special cases when pages are registered dynamically (Rider).
   * Otherwise pages should be registered as extensions with {@link com.intellij.openapi.options.colors.ColorSettingsPage#EP_NAME}
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
