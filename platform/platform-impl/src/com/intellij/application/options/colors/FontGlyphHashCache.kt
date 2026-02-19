// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.colors

import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.EditorFontType
import com.jetbrains.JBR
import org.jetbrains.annotations.ApiStatus
import java.awt.Font
import java.awt.font.FontRenderContext

@ApiStatus.Internal
internal class FontGlyphHashCache {
  data class FontKey(val fontFamily: String, val features: String) {
    companion object {
      fun fromFont(f: Font): FontKey {
        return FontKey(f.name, JBR.getFontExtensions().getEnabledFeatures(f).joinToString(","))
      }
    }
  }

  private val fontGlyphCache: HashMap<FontKey, HashMap<Char, Int>> = hashMapOf()
  private val ligatureCache: HashMap<FontKey, HashMap<String, IntArray>> = hashMapOf()

  fun computeCaches(scheme: EditorColorsScheme, text: CharSequence): FontKey {
    val font = scheme.getFont(EditorFontType.PLAIN).deriveFont(13f)
    return computeCaches(font, text)
  }

  fun computeLigatureCaches(font: Font, ligatures: List<String>) {
    val key = FontKey.fromFont(font)
    if (ligatureCache[key] == null) ligatureCache[key] = HashMap()

    val context = lazy { FontRenderContext(null, false, false) }

    for (ligature in ligatures) {
      if (ligatureCache[key]!!.containsKey(ligature)) continue
      val glyphVector = font.layoutGlyphVector(context.value, ligature.toCharArray(), 0, ligature.length, Font.LAYOUT_LEFT_TO_RIGHT)
      val array = ligature.indices.map { glyphVector.getGlyphCode(it) }.toIntArray()
      ligatureCache[key]!![ligature] = array
    }
  }

  fun computeCaches(font: Font, text: CharSequence): FontKey {
    val key = FontKey.fromFont(font)
    val cache = fontGlyphCache.getOrPut(key, { HashMap() })

    val context = lazy { FontRenderContext(null, false, false) }

    for (c in text) {
      if (cache.containsKey(c)) continue
      val code = font.layoutGlyphVector(context.value, arrayOf(c).toCharArray(), 0, 1, Font.LAYOUT_LEFT_TO_RIGHT)
        .getGlyphCode(0)
      cache[c] = code
    }

    return key
  }

  fun getGlyphCache(key: FontKey, char: Char): Int {
    return fontGlyphCache[key]?.get(char) ?: 0
  }

  fun getLigatureCache(key: FontKey, ligature: String): IntArray? {
    return ligatureCache[key]?.get(ligature)
  }
}