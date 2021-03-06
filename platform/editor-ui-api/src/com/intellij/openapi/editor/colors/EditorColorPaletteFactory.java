// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.colors;

import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Builds an empty editor color palette. The constructed palette can be filled with {@code EditorColorPalette.with...()} methods.
 * @see EditorColorPalette#withBackgroundColors()
 * @see EditorColorPalette#withForegroundColors()
 */
public abstract class EditorColorPaletteFactory {
  public static EditorColorPaletteFactory getInstance() {
    return ApplicationManager.getApplication().getService(EditorColorPaletteFactory.class);
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

