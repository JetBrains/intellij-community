// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.colors.impl

import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.markup.FontFamilies
import com.intellij.openapi.editor.markup.TextAttributes
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.util.function.BiPredicate

/**
 * An immutable snapshot of the state of one [AbstractColorsScheme].
 * It has the name and the parent scheme of the scheme, and it tells if a user can delete the scheme.
 * It has the own font preferences of the editor and of the console, and it tells if the scheme uses them.
 * It also has the colors and the text attributes that the scheme defines itself, without the values of the parent scheme.
 * The text attributes of temporary keys are values that a caller derives from the other text attributes.
 * So a change of the text attributes removes them in the same snapshot.
 * The scheme keeps its current snapshot in one volatile field and replaces the snapshot on each change.
 * A `with` function returns a new snapshot, or this snapshot when no value changes.
 * The snapshots are safe to use on any thread.
 * A [TextAttributes] object is mutable, so a caller must not change the text attributes of a snapshot.
 */
@ApiStatus.Internal
class ColorsSchemeSnapshot private constructor(
  private val schemeName: String?, // null until the scheme gets a name
  private val parentScheme: EditorColorsScheme?,
  private val canBeDeleted: Boolean,
  private val fontPreferences: FontPreferencesSnapshot, // the own editor font, unused while the scheme uses the application font
  private val useAppFontPreferencesInEditor: Boolean,
  private val consoleFontPreferences: FontPreferencesSnapshot, // the own console font, unused while the console uses the editor font
  private val useEditorFontPreferencesInConsole: Boolean,
  private val colors: PersistentMap<ColorKey, Color>,
  private val attributes: PersistentMap<String, TextAttributes>, // the text attributes by the external name of their key
  private val tempAttributes: PersistentMap<String, TextAttributes>, // the text attributes of temporary keys, see TextAttributesKey.isTemp
) {
  /**
   * The font families of [attributes] and [tempAttributes], or `null` before the first [textAttributesFontFamilies] call.
   */
  @Volatile
  private var textAttributesFontFamilies: Set<FontFamilies>? = null

  fun schemeName(): String? = schemeName

  fun parentScheme(): EditorColorsScheme? = parentScheme

  fun canBeDeleted(): Boolean = canBeDeleted

  fun fontPreferences(): FontPreferencesSnapshot = fontPreferences

  fun useAppFontPreferencesInEditor(): Boolean = useAppFontPreferencesInEditor

  fun consoleFontPreferences(): FontPreferencesSnapshot = consoleFontPreferences

  fun useEditorFontPreferencesInConsole(): Boolean = useEditorFontPreferencesInConsole

  /**
   * Returns the colors by their keys.
   * The map is unmodifiable.
   */
  fun colors(): Map<ColorKey, Color> = colors

  /**
   * Returns the text attributes by the external names of their keys.
   * The map is unmodifiable.
   */
  fun attributes(): Map<String, TextAttributes> = attributes

  /**
   * Returns the font families of the text attributes that the scheme defines itself, of temporary keys included.
   * The parent scheme does not count. The snapshot computes the set once.
   */
  fun textAttributesFontFamilies(): Set<FontFamilies> {
    val current = textAttributesFontFamilies
    if (current != null) {
      return current
    }
    synchronized(this) {
      val again = textAttributesFontFamilies
      if (again != null) {
        return again
      }
      val computed = collectFontFamilies(attributes.values, tempAttributes.values)
      textAttributesFontFamilies = computed
      return computed
    }
  }

  fun getColor(key: ColorKey): Color? {
    return colors[key]
  }

  fun getAttributes(keyName: String): TextAttributes? {
    return attributes[keyName]
  }

  /**
   * Returns the text attributes of the temporary key with [keyName], or `null` if the snapshot has none.
   */
  fun getTempAttributes(keyName: String): TextAttributes? {
    return tempAttributes[keyName]
  }

  fun withSchemeName(schemeName: String): ColorsSchemeSnapshot {
    return copy(schemeName = schemeName)
  }

  fun withParentScheme(parentScheme: EditorColorsScheme?): ColorsSchemeSnapshot {
    return copy(parentScheme = parentScheme)
  }

  fun withCanBeDeleted(canBeDeleted: Boolean): ColorsSchemeSnapshot {
    return copy(canBeDeleted = canBeDeleted)
  }

  fun withFontPreferences(fontPreferences: FontPreferencesSnapshot): ColorsSchemeSnapshot {
    return copy(fontPreferences = fontPreferences)
  }

  fun withUseAppFontPreferencesInEditor(useAppFontPreferencesInEditor: Boolean): ColorsSchemeSnapshot {
    return copy(useAppFontPreferencesInEditor = useAppFontPreferencesInEditor)
  }

  fun withConsoleFontPreferences(consoleFontPreferences: FontPreferencesSnapshot): ColorsSchemeSnapshot {
    return copy(consoleFontPreferences = consoleFontPreferences)
  }

  fun withUseEditorFontPreferencesInConsole(useEditorFontPreferencesInConsole: Boolean): ColorsSchemeSnapshot {
    return copy(useEditorFontPreferencesInConsole = useEditorFontPreferencesInConsole)
  }

  fun withColor(key: ColorKey, color: Color): ColorsSchemeSnapshot {
    return copy(colors = putValue(colors, key, color))
  }

  fun withAttributes(keyName: String, textAttributes: TextAttributes): ColorsSchemeSnapshot {
    return copy(attributes = putValue(attributes, keyName, textAttributes))
  }

  /**
   * Returns this snapshot with [textAttributes] for the temporary key with [keyName].
   * The other text attributes do not change.
   */
  fun withTempAttributes(keyName: String, textAttributes: TextAttributes): ColorsSchemeSnapshot {
    return copy(tempAttributes = putValue(tempAttributes, keyName, textAttributes))
  }

  /**
   * Returns this snapshot with all [newColors].
   * A new color replaces the color of the same key.
   */
  fun withAllColors(newColors: Map<ColorKey, Color>): ColorsSchemeSnapshot {
    return copy(colors = putAllValues(colors, newColors))
  }

  /**
   * Returns this snapshot with all [newAttributes].
   * New text attributes replace the text attributes of the same key name.
   */
  fun withAllAttributes(newAttributes: Map<String, TextAttributes>): ColorsSchemeSnapshot {
    return copy(attributes = putAllValues(attributes, newAttributes))
  }

  /**
   * Returns this snapshot with the colors and the text attributes of [other] instead of its own.
   * The other values of this snapshot do not change.
   */
  fun withColorsAndAttributesOf(other: ColorsSchemeSnapshot): ColorsSchemeSnapshot {
    return copy(colors = other.colors, attributes = other.attributes)
  }

  /**
   * Returns this snapshot with the font preferences of [other] and the flags that tell if the scheme uses them.
   * The other values of this snapshot do not change.
   */
  fun withFontsOf(other: ColorsSchemeSnapshot): ColorsSchemeSnapshot {
    return copy(
      fontPreferences = other.fontPreferences,
      useAppFontPreferencesInEditor = other.useAppFontPreferencesInEditor,
      consoleFontPreferences = other.consoleFontPreferences,
      useEditorFontPreferencesInConsole = other.useEditorFontPreferencesInConsole,
    )
  }

  /**
   * Returns this snapshot with the colors and the text attributes of [other] whose keys this snapshot does not have.
   */
  fun withMissing(other: ColorsSchemeSnapshot): ColorsSchemeSnapshot {
    return copy(
      colors = putMissingValues(colors, other.colors),
      attributes = putMissingValues(attributes, other.attributes),
    )
  }

  /**
   * Returns this snapshot without the colors that match [predicate].
   */
  fun withoutColors(predicate: BiPredicate<in ColorKey, in Color>): ColorsSchemeSnapshot {
    return copy(colors = removeValues(colors, predicate))
  }

  /**
   * Returns this snapshot without the text attributes that match [predicate].
   */
  fun withoutAttributes(predicate: BiPredicate<in String, in TextAttributes>): ColorsSchemeSnapshot {
    return copy(attributes = removeValues(attributes, predicate))
  }

  private fun copy(
    schemeName: String? = this.schemeName,
    parentScheme: EditorColorsScheme? = this.parentScheme,
    canBeDeleted: Boolean = this.canBeDeleted,
    fontPreferences: FontPreferencesSnapshot = this.fontPreferences,
    useAppFontPreferencesInEditor: Boolean = this.useAppFontPreferencesInEditor,
    consoleFontPreferences: FontPreferencesSnapshot = this.consoleFontPreferences,
    useEditorFontPreferencesInConsole: Boolean = this.useEditorFontPreferencesInConsole,
    colors: PersistentMap<ColorKey, Color> = this.colors,
    attributes: PersistentMap<String, TextAttributes> = this.attributes,
    tempAttributes: PersistentMap<String, TextAttributes> = if (attributes === this.attributes) this.tempAttributes else persistentHashMapOf(),
  ): ColorsSchemeSnapshot {
    if (schemeName == this.schemeName &&
        parentScheme === this.parentScheme &&
        canBeDeleted == this.canBeDeleted &&
        fontPreferences === this.fontPreferences &&
        useAppFontPreferencesInEditor == this.useAppFontPreferencesInEditor &&
        consoleFontPreferences === this.consoleFontPreferences &&
        useEditorFontPreferencesInConsole == this.useEditorFontPreferencesInConsole &&
        colors === this.colors &&
        attributes === this.attributes &&
        tempAttributes === this.tempAttributes) {
      return this
    }
    return ColorsSchemeSnapshot(
      schemeName,
      parentScheme,
      canBeDeleted,
      fontPreferences,
      useAppFontPreferencesInEditor,
      consoleFontPreferences,
      useEditorFontPreferencesInConsole,
      colors,
      attributes,
      tempAttributes,
    )
  }

  companion object {
    /**
     * Returns the snapshot of a new scheme.
     * The scheme uses the application font in the editor and the editor font in the console.
     * It has no name, no parent scheme, no colors and no text attributes, and a user can delete it.
     */
    @JvmStatic
    fun initial(): ColorsSchemeSnapshot {
      return ColorsSchemeSnapshot(
        schemeName = null,
        parentScheme = null,
        canBeDeleted = true,
        fontPreferences = FontPreferencesSnapshot.EMPTY,
        useAppFontPreferencesInEditor = true,
        consoleFontPreferences = FontPreferencesSnapshot.EMPTY,
        useEditorFontPreferencesInConsole = true,
        colors = persistentHashMapOf(),
        attributes = persistentHashMapOf(),
        tempAttributes = persistentHashMapOf(),
      )
    }
  }
}

/**
 * Returns [map] with [value] for [key].
 * Returns [map] itself when it already has this value object for the key.
 */
private fun <K, V : Any> putValue(map: PersistentMap<K, V>, key: K, value: V): PersistentMap<K, V> {
  if (map[key] === value) {
    return map
  }
  return map.putting(key, value)
}

/**
 * Returns [map] with all [entries].
 * Returns [map] itself when it already has the value object of each entry.
 */
private fun <K, V : Any> putAllValues(map: PersistentMap<K, V>, entries: Map<K, V>): PersistentMap<K, V> {
  if (entries.all { (key, value) -> map[key] === value }) {
    return map
  }
  return map.puttingAll(entries)
}

/**
 * Returns [map] with the [entries] whose keys [map] does not have.
 * Returns [map] itself when it has all the keys.
 */
private fun <K, V : Any> putMissingValues(map: PersistentMap<K, V>, entries: Map<K, V>): PersistentMap<K, V> {
  if (map.keys.containsAll(entries.keys)) {
    return map
  }
  val result = map.builder()
  for ((key, value) in entries) {
    result.putIfAbsent(key, value)
  }
  return result.build()
}

/**
 * Returns [map] without the entries that match [predicate].
 * Returns [map] itself when no entry matches.
 * The predicate runs once for each entry.
 */
private fun <K, V : Any> removeValues(map: PersistentMap<K, V>, predicate: BiPredicate<in K, in V>): PersistentMap<K, V> {
  var result: PersistentMap.Builder<K, V>? = null
  for ((key, value) in map) {
    if (predicate.test(key, value)) {
      if (result == null) {
        result = map.builder()
      }
      result.remove(key)
    }
  }
  if (result == null) {
    return map
  }
  return result.build()
}

private fun collectFontFamilies(vararg groups: Collection<TextAttributes>): Set<FontFamilies> {
  val result = HashSet<FontFamilies>()
  for (group in groups) {
    for (attributes in group) {
      val fontFamilies = attributes.fontFamilies
      if (fontFamilies != null) {
        result.add(fontFamilies)
      }
    }
  }
  return java.util.Set.copyOf(result)
}
