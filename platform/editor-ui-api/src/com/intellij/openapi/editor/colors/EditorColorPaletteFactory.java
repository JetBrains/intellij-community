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
package com.intellij.openapi.editor.colors;

import com.intellij.lang.Language;
import com.intellij.openapi.components.ServiceManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Builds an empty editor color palette. The constructed palette can be filled with {@code EditorColorPalette.with...()} methods.
 * @see EditorColorPalette#withBackgroundColors()
 * @see EditorColorPalette#withForegroundColors()
 */
public abstract class EditorColorPaletteFactory {
  public static EditorColorPaletteFactory getInstance() {
    return ServiceManager.getService(EditorColorPaletteFactory.class);
  }

  /**
   * Builds an empty palette object for the given scheme and language.
   * 
   * @param scheme   The editor color scheme to retrieve colors from.
   * @param language The language. It can be one of the following:
   *                 <ul>
   *                 <li><b>null</b> - for colors not associated with any language.</li>  
   *                 <li><b>Language.ANY</b> - all available colors.</li>
   *                 <li><i>language</i> - colors for the given language</li>  
   *                 </ul>
   * @return The empty color palette for the given scheme and language.
   */
  public abstract EditorColorPalette getPalette(@NotNull EditorColorsScheme scheme, @Nullable Language language);
}

