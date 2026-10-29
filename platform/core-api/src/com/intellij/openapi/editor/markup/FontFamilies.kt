// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.markup

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Unmodifiable

/**
 * The font families of editor text, in the order that the editor tries them.
 * Set them with [TextAttributes.setFontFamilies].
 *
 * A name is a typographic family name, as `FontFamilyService.getAvailableFamilies()` lists it, for example `JetBrains Mono`.
 * A face name, such as `JetBrains Mono Bold`, is not a family name.
 *
 * For each character, the editor uses the first installed family that can display it.
 * After these families, the editor tries the editor fonts, and then the system fallback fonts.
 * The text uses the editor font size, the line height of the editor font and the baseline of the line.
 * The bold and italic styles come from [TextAttributes.getFontType].
 *
 * Two values are equal when their names are equal.
 * The instances are immutable and safe to use on any thread.
 */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface FontFamilies {
  /**
   * Returns the family names, in the order that the editor tries them.
   * The list is not empty.
   */
  fun names(): @Unmodifiable List<String>

  companion object {
    /**
     * Returns the font families with the given names, in this order.
     *
     * @throws IllegalArgumentException if there are no names, if a name is null, blank, or repeated,
     *   or if a name has leading or trailing spaces or a comma
     */
    @JvmStatic
    fun of(vararg names: String): FontFamilies {
      return FontFamiliesImpl.create(names.asList())
    }

    /**
     * Returns the font families with the given names, in this order.
     *
     * @throws IllegalArgumentException if there are no names, if a name is null, blank, or repeated,
     *   or if a name has leading or trailing spaces or a comma
     */
    @JvmStatic
    fun of(names: List<String>): FontFamilies {
      return FontFamiliesImpl.create(names)
    }
  }
}
