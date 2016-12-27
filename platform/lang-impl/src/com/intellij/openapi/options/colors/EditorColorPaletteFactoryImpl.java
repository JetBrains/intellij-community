/*
 * Copyright 2000-2016 JetBrains s.r.o.
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


import com.intellij.lang.Language;
import com.intellij.openapi.editor.colors.EditorColorPalette;
import com.intellij.openapi.editor.colors.EditorColorPaletteFactory;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class EditorColorPaletteFactoryImpl extends EditorColorPaletteFactory {
  
  @Override
  public EditorColorPalette getPalette(@NotNull EditorColorsScheme scheme, @Nullable Language language) {
    return new ColorPagesPalette(scheme, language);
  }

  private static class ColorPagesPalette extends EditorColorPalette {

    @Nullable private Language myLanguage;

    public ColorPagesPalette(@NotNull EditorColorsScheme colorsScheme, @Nullable Language language) {
      super(colorsScheme);
      myLanguage = language;
    }

    @Override
    protected Collection<TextAttributesKey> getTextAttributeKeys() {
      Set<TextAttributesKey> textAttributesKeys = new HashSet<>();
      for (ColorSettingsPage page : ColorSettingsPages.getInstance().getRegisteredPages()) {
        Language pageLanguage = guessPageLanguage(page);
        if (
          myLanguage == null && pageLanguage == null ||
          myLanguage != null && (myLanguage.is(pageLanguage) || myLanguage.is(Language.ANY))
          ) {
          for (AttributesDescriptor descriptor : page.getAttributeDescriptors()) {
            textAttributesKeys.add(descriptor.getKey());
          }
        }
      }
      return textAttributesKeys;
    }

    @Nullable
    private static Language guessPageLanguage(@NotNull ColorSettingsPage page) {
      for (Language language : Language.getRegisteredLanguages()) {
        if (page instanceof RainbowColorSettingsPage && language.is(((RainbowColorSettingsPage)page).getLanguage()) ||
            page.getDisplayName().equals(language.getDisplayName())) {
          return language;
        }
      }
      return null;
    }
  }
}
