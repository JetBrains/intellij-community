// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.colors.impl

import com.intellij.application.options.EditorFontsConstants
import com.intellij.openapi.editor.colors.FontPreferences
import com.intellij.openapi.editor.colors.ModifiableFontPreferences
import org.jetbrains.annotations.ApiStatus
import java.util.function.Consumer

/**
 * An immutable snapshot of [FontPreferences].
 * [FontPreferencesImpl] keeps its current snapshot in this class and replaces the snapshot on each change.
 * A `with` function returns a new snapshot, or this snapshot when no value changes.
 * The snapshots are safe to use on any thread.
 * The colors scheme of an editor keeps its own font preferences in snapshots, and changes them with [withChanges].
 */
@ApiStatus.Internal
class FontPreferencesSnapshot private constructor(
  private val effectiveFontFamilies: List<String>,
  private val realFontFamilies: List<String>,
  private val sizedFontFamilies: Array<String>, // the font family of each element of fontSizes
  private val fontSizes: FloatArray,
  private val templateFontSize: Float, // the size of a font family that has no size of its own
  private val lineSpacing: Float,
  private val useLigatures: Boolean,
  private val regularSubFamily: String?,
  private val boldSubFamily: String?,
  private val characterVariants: Set<String>,
) : FontPreferences() {

  override fun getEffectiveFontFamilies(): List<String> = effectiveFontFamilies

  override fun getRealFontFamilies(): List<String> = realFontFamilies

  override fun getFontFamily(): String {
    return firstFontFamily(effectiveFontFamilies)
  }

  override fun getRegularSubFamily(): String? = regularSubFamily

  override fun getBoldSubFamily(): String? = boldSubFamily

  override fun getSize(fontFamily: String): Int {
    return (getSize2D(fontFamily) + 0.5).toInt()
  }

  override fun getSize2D(fontFamily: String): Float {
    return sizeOf(fontFamily, sizedFontFamilies, fontSizes, templateFontSize)
  }

  override fun copyTo(preferences: FontPreferences) {
    // A subclass can override a setter, so only the exact class gets the snapshot in one write
    if (preferences.javaClass == FontPreferencesImpl::class.java) {
      (preferences as FontPreferencesImpl).updateSnapshot { withRealFontFamilySizesOnly() }
      return
    }
    if (preferences !is ModifiableFontPreferences) {
      return
    }
    preferences.setEffectiveFontFamilies(effectiveFontFamilies)
    preferences.setRealFontFamilies(realFontFamilies)
    preferences.setTemplateFontSize(templateFontSize)
    preferences.resetFontSizes()
    for (fontFamily in realFontFamilies) {
      val index = sizedFontFamilies.indexOf(fontFamily)
      if (index >= 0) {
        preferences.setFontSize(fontFamily, fontSizes[index])
      }
    }
    preferences.setUseLigatures(useLigatures)
    preferences.setLineSpacing(lineSpacing)
    preferences.setRegularSubFamily(regularSubFamily)
    preferences.setBoldSubFamily(boldSubFamily)
    preferences.setCharacterVariants(characterVariants)
  }

  override fun useLigatures(): Boolean = useLigatures

  override fun hasSize(fontName: String): Boolean {
    return sizedFontFamilies.contains(fontName)
  }

  override fun getLineSpacing(): Float = lineSpacing

  override fun getCharacterVariants(): Set<String> = characterVariants

  /**
   * Returns the snapshot after the changes, or this snapshot when no value changes.
   * The changes run on a builder, so the function creates one snapshot at most.
   * The getters of the builder return the values with the changes so far.
   */
  fun withChanges(changes: Consumer<in ModifiableFontPreferences>): FontPreferencesSnapshot {
    val builder = Builder(this)
    changes.accept(builder)
    return builder.build()
  }

  fun withoutFontSizes(): FontPreferencesSnapshot {
    if (sizedFontFamilies.isEmpty()) {
      return this
    }
    return copy(
      sizedFontFamilies = NO_FONT_FAMILIES,
      fontSizes = NO_FONT_SIZES,
    )
  }

  fun withoutFonts(): FontPreferencesSnapshot {
    if (hasNoFonts()) {
      return this
    }
    return copy(
      effectiveFontFamilies = emptyList(),
      realFontFamilies = emptyList(),
      useLigatures = false,
      regularSubFamily = null,
      boldSubFamily = null,
      characterVariants = emptySet(),
    )
  }

  fun withoutFontsAndFontSizes(): FontPreferencesSnapshot {
    if (hasNoFonts() && sizedFontFamilies.isEmpty()) {
      return this
    }
    return copy(
      effectiveFontFamilies = emptyList(),
      realFontFamilies = emptyList(),
      sizedFontFamilies = NO_FONT_FAMILIES,
      fontSizes = NO_FONT_SIZES,
      useLigatures = false,
      regularSubFamily = null,
      boldSubFamily = null,
      characterVariants = emptySet(),
    )
  }

  fun withLineSpacing(lineSpacing: Float): FontPreferencesSnapshot {
    val fixedLineSpacing = EditorFontsConstants.checkAndFixEditorLineSpacing(lineSpacing)
    if (this.lineSpacing == fixedLineSpacing) {
      return this
    }
    return copy(lineSpacing = fixedLineSpacing)
  }

  /**
   * Adds the font family to the effective and the real font families, if it is absent there.
   * The size becomes the size of the font family and the template font size.
   */
  fun withRegisteredFontFamily(fontFamily: String, size: Float): FontPreferencesSnapshot {
    val effective = effectiveFontFamilies.addIfAbsent(fontFamily)
    val real = realFontFamiliesWith(fontFamily, realFontFamilies, effectiveFontFamilies, effective)
    val index = sizedFontFamilies.indexOf(fontFamily)
    val sizes = fontSizes.withSize(size, index)
    val unchanged = effective === effectiveFontFamilies &&
                    real === realFontFamilies &&
                    sizes === fontSizes &&
                    templateFontSize == size
    if (unchanged) {
      return this
    }
    return copy(
      effectiveFontFamilies = effective,
      realFontFamilies = real,
      sizedFontFamilies = sizedFontFamilies.withSizedFontFamily(fontFamily, index),
      fontSizes = sizes,
      templateFontSize = size,
    )
  }

  fun withFontFamily(fontFamily: String): FontPreferencesSnapshot {
    val effective = effectiveFontFamilies.addIfAbsent(fontFamily)
    val real = realFontFamiliesWith(fontFamily, realFontFamilies, effectiveFontFamilies, effective)
    if (effective === effectiveFontFamilies && real === realFontFamilies) {
      return this
    }
    return copy(
      effectiveFontFamilies = effective,
      realFontFamilies = real,
    )
  }

  fun withFontSize(fontFamily: String, size: Float): FontPreferencesSnapshot {
    val index = sizedFontFamilies.indexOf(fontFamily)
    val sizes = fontSizes.withSize(size, index)
    if (sizes === fontSizes) {
      return this
    }
    return copy(
      sizedFontFamilies = sizedFontFamilies.withSizedFontFamily(fontFamily, index),
      fontSizes = sizes,
    )
  }

  fun withTemplateFontSize(size: Float): FontPreferencesSnapshot {
    if (templateFontSize == size) {
      return this
    }
    return copy(templateFontSize = size)
  }

  fun withEffectiveFontFamilies(fontFamilies: List<String>): FontPreferencesSnapshot {
    val families = immutableFontFamilies(effectiveFontFamilies, fontFamilies)
    if (families === effectiveFontFamilies) {
      return this
    }
    return copy(effectiveFontFamilies = families)
  }

  fun withRealFontFamilies(fontFamilies: List<String>): FontPreferencesSnapshot {
    val families = immutableFontFamilies(realFontFamilies, fontFamilies)
    if (families === realFontFamilies) {
      return this
    }
    return copy(realFontFamilies = families)
  }

  fun withUseLigatures(useLigatures: Boolean): FontPreferencesSnapshot {
    if (this.useLigatures == useLigatures) {
      return this
    }
    return copy(useLigatures = useLigatures)
  }

  fun withRegularSubFamily(subFamily: String?): FontPreferencesSnapshot {
    if (regularSubFamily == subFamily) {
      return this
    }
    return copy(regularSubFamily = subFamily)
  }

  fun withBoldSubFamily(subFamily: String?): FontPreferencesSnapshot {
    if (boldSubFamily == subFamily) {
      return this
    }
    return copy(boldSubFamily = subFamily)
  }

  fun withCharacterVariants(variants: Set<String?>): FontPreferencesSnapshot {
    val immutable = immutableVariants(characterVariants, variants)
    if (immutable === characterVariants) {
      return this
    }
    return copy(characterVariants = immutable)
  }

  fun withCharacterVariant(variant: String, enabled: Boolean): FontPreferencesSnapshot {
    val variants = characterVariants.withVariant(variant, enabled)
    if (variants === characterVariants) {
      return this
    }
    return copy(characterVariants = variants)
  }

  /**
   * Compares the user settings: the real font families and their sizes, the ligatures and the line spacing.
   * It also compares the subfamilies and the character variants.
   * It ignores the effective font families and the template font size.
   * To find any change of the values, compare the snapshots by identity.
   */
  override fun equals(other: Any?): Boolean {
    if (this === other) {
      return true
    }
    if (other !is FontPreferencesSnapshot) {
      return false
    }
    return realFontFamilies == other.realFontFamilies &&
           realFontFamilies.all { hasSameStoredSize(it, other) } &&
           useLigatures == other.useLigatures &&
           lineSpacing == other.lineSpacing &&
           regularSubFamily == other.regularSubFamily &&
           boldSubFamily == other.boldSubFamily &&
           characterVariants == other.characterVariants
  }

  override fun hashCode(): Int {
    return realFontFamilies.hashCode()
  }

  override fun toString(): String {
    return "Effective font families: $effectiveFontFamilies"
  }

  /**
   * Returns true when the snapshot already has the values that [withoutFonts] sets.
   */
  private fun hasNoFonts(): Boolean {
    return effectiveFontFamilies.isEmpty() &&
           realFontFamilies.isEmpty() &&
           !useLigatures &&
           regularSubFamily == null &&
           boldSubFamily == null &&
           characterVariants.isEmpty()
  }

  private fun hasSameStoredSize(fontFamily: String, other: FontPreferencesSnapshot): Boolean {
    val size = storedSize(fontFamily, sizedFontFamilies, fontSizes)
    return size == storedSize(fontFamily, other.sizedFontFamilies, other.fontSizes)
  }

  /**
   * Returns the snapshot without the sizes of the font families that are not real font families.
   * The setter path of [copyTo] drops these sizes in the same way.
   */
  private fun withRealFontFamilySizesOnly(): FontPreferencesSnapshot {
    if (sizedFontFamilies.all { it in realFontFamilies }) {
      return this
    }
    val indices = sizedFontFamilies.indices.filter { sizedFontFamilies[it] in realFontFamilies }
    return copy(
      sizedFontFamilies = Array(indices.size) { sizedFontFamilies[indices[it]] },
      fontSizes = FloatArray(indices.size) { fontSizes[indices[it]] },
    )
  }

  private fun copy(
    effectiveFontFamilies: List<String> = this.effectiveFontFamilies,
    realFontFamilies: List<String> = this.realFontFamilies,
    sizedFontFamilies: Array<String> = this.sizedFontFamilies,
    fontSizes: FloatArray = this.fontSizes,
    templateFontSize: Float = this.templateFontSize,
    lineSpacing: Float = this.lineSpacing,
    useLigatures: Boolean = this.useLigatures,
    regularSubFamily: String? = this.regularSubFamily,
    boldSubFamily: String? = this.boldSubFamily,
    characterVariants: Set<String> = this.characterVariants,
  ): FontPreferencesSnapshot {
    return FontPreferencesSnapshot(
      effectiveFontFamilies = effectiveFontFamilies,
      realFontFamilies = realFontFamilies,
      sizedFontFamilies = sizedFontFamilies,
      fontSizes = fontSizes,
      templateFontSize = templateFontSize,
      lineSpacing = lineSpacing,
      useLigatures = useLigatures,
      regularSubFamily = regularSubFamily,
      boldSubFamily = boldSubFamily,
      characterVariants = characterVariants,
    )
  }

  /**
   * Collects the changes of [ModifiableFontPreferences.runBatchUpdate] and creates one snapshot at the end.
   * The getters return the values with the changes so far.
   */
  private class Builder(private val base: FontPreferencesSnapshot) : ModifiableFontPreferences() {
    private var effectiveFontFamilies: List<String> = base.effectiveFontFamilies
    private var realFontFamilies: List<String> = base.realFontFamilies
    private var sizedFontFamilies: Array<String> = base.sizedFontFamilies
    private var fontSizes: FloatArray = base.fontSizes
    private var templateFontSize: Float = base.templateFontSize
    private var lineSpacing: Float = base.lineSpacing
    private var useLigatures: Boolean = base.useLigatures
    private var regularSubFamily: String? = base.regularSubFamily
    private var boldSubFamily: String? = base.boldSubFamily
    private var characterVariants: Set<String> = base.characterVariants

    override fun getEffectiveFontFamilies(): List<String> = effectiveFontFamilies

    override fun getRealFontFamilies(): List<String> = realFontFamilies

    override fun getFontFamily(): String {
      return firstFontFamily(effectiveFontFamilies)
    }

    override fun getRegularSubFamily(): String? = regularSubFamily

    override fun getBoldSubFamily(): String? = boldSubFamily

    override fun getSize(fontFamily: String): Int {
      return (getSize2D(fontFamily) + 0.5).toInt()
    }

    override fun getSize2D(fontFamily: String): Float {
      return sizeOf(fontFamily, sizedFontFamilies, fontSizes, templateFontSize)
    }

    override fun copyTo(preferences: FontPreferences) {
      build().copyTo(preferences)
    }

    override fun useLigatures(): Boolean = useLigatures

    override fun hasSize(fontName: String): Boolean {
      return sizedFontFamilies.contains(fontName)
    }

    override fun getLineSpacing(): Float = lineSpacing

    override fun getCharacterVariants(): Set<String> = characterVariants

    override fun clear() {
      clearFonts()
      resetFontSizes()
    }

    override fun clearFonts() {
      effectiveFontFamilies = emptyList()
      realFontFamilies = emptyList()
      useLigatures = false
      regularSubFamily = null
      boldSubFamily = null
      characterVariants = emptySet()
    }

    override fun setUseLigatures(useLigatures: Boolean) {
      this.useLigatures = useLigatures
    }

    override fun addFontFamily(family: String) {
      val effective = effectiveFontFamilies.addIfAbsent(family)
      realFontFamilies = realFontFamiliesWith(family, realFontFamilies, effectiveFontFamilies, effective)
      effectiveFontFamilies = effective
    }

    override fun register(family: String, size: Int) {
      register(family, size.toFloat())
    }

    override fun register(family: String, size: Float) {
      addFontFamily(family)
      setFontSize(family, size)
      templateFontSize = size
    }

    override fun setEffectiveFontFamilies(fontFamilies: List<String>) {
      effectiveFontFamilies = immutableFontFamilies(effectiveFontFamilies, fontFamilies)
    }

    override fun setRealFontFamilies(fontFamilies: List<String>) {
      realFontFamilies = immutableFontFamilies(realFontFamilies, fontFamilies)
    }

    override fun setTemplateFontSize(size: Int) {
      setTemplateFontSize(size.toFloat())
    }

    override fun setTemplateFontSize(size: Float) {
      templateFontSize = size
    }

    override fun setLineSpacing(lineSpacing: Float) {
      this.lineSpacing = EditorFontsConstants.checkAndFixEditorLineSpacing(lineSpacing)
    }

    override fun resetFontSizes() {
      sizedFontFamilies = NO_FONT_FAMILIES
      fontSizes = NO_FONT_SIZES
    }

    override fun setFontSize(fontFamily: String, size: Int) {
      setFontSize(fontFamily, size.toFloat())
    }

    override fun setFontSize(fontFamily: String, size: Float) {
      val index = sizedFontFamilies.indexOf(fontFamily)
      sizedFontFamilies = sizedFontFamilies.withSizedFontFamily(fontFamily, index)
      fontSizes = fontSizes.withSize(size, index)
    }

    override fun setRegularSubFamily(subFamily: String?) {
      regularSubFamily = subFamily
    }

    override fun setBoldSubFamily(subFamily: String?) {
      boldSubFamily = subFamily
    }

    override fun setCharacterVariants(variants: Set<String>) {
      characterVariants = immutableVariants(characterVariants, variants)
    }

    override fun setCharacterVariant(variant: String, enabled: Boolean) {
      characterVariants = characterVariants.withVariant(variant, enabled)
    }

    /**
     * Returns the base snapshot when no value differs from it, and a new snapshot otherwise.
     */
    fun build(): FontPreferencesSnapshot {
      if (hasSameValuesAs(base)) {
        return base
      }
      return FontPreferencesSnapshot(
        effectiveFontFamilies = effectiveFontFamilies,
        realFontFamilies = realFontFamilies,
        sizedFontFamilies = sizedFontFamilies,
        fontSizes = fontSizes,
        templateFontSize = templateFontSize,
        lineSpacing = lineSpacing,
        useLigatures = useLigatures,
        regularSubFamily = regularSubFamily,
        boldSubFamily = boldSubFamily,
        characterVariants = characterVariants,
      )
    }

    private fun hasSameValuesAs(other: FontPreferencesSnapshot): Boolean {
      return effectiveFontFamilies == other.effectiveFontFamilies &&
             realFontFamilies == other.realFontFamilies &&
             sizedFontFamilies.contentEquals(other.sizedFontFamilies) &&
             fontSizes.contentEquals(other.fontSizes) &&
             templateFontSize == other.templateFontSize &&
             lineSpacing == other.lineSpacing &&
             useLigatures == other.useLigatures &&
             regularSubFamily == other.regularSubFamily &&
             boldSubFamily == other.boldSubFamily &&
             characterVariants == other.characterVariants
    }
  }

  companion object {
    private val NO_FONT_FAMILIES: Array<String> = emptyArray()
    private val NO_FONT_SIZES: FloatArray = FloatArray(0)

    /**
     * The snapshot of a new [FontPreferencesImpl].
     * It has no font families, and its sizes and line spacing have the default values.
     */
    @JvmField
    val EMPTY: FontPreferencesSnapshot = FontPreferencesSnapshot(
      effectiveFontFamilies = emptyList(),
      realFontFamilies = emptyList(),
      sizedFontFamilies = NO_FONT_FAMILIES,
      fontSizes = NO_FONT_SIZES,
      templateFontSize = DEFAULT_FONT_SIZE.toFloat(),
      lineSpacing = DEFAULT_LINE_SPACING,
      useLigatures = false,
      regularSubFamily = null,
      boldSubFamily = null,
      characterVariants = emptySet(),
    )
  }
}

private fun firstFontFamily(effectiveFontFamilies: List<String>): String {
  if (effectiveFontFamilies.isEmpty()) {
    return FontPreferences.DEFAULT_FONT_NAME
  }
  return effectiveFontFamilies.first()
}

/**
 * Returns the stored size of the font family, then the template font size, then the default font size.
 * A size that is not positive counts as absent.
 */
private fun sizeOf(
  fontFamily: String,
  sizedFontFamilies: Array<String>,
  fontSizes: FloatArray,
  templateFontSize: Float,
): Float {
  var result = storedSize(fontFamily, sizedFontFamilies, fontSizes)
  if (result <= 0) {
    result = templateFontSize
  }
  if (result > 0) {
    return result
  }
  return FontPreferences.DEFAULT_FONT_SIZE.toFloat()
}

private fun storedSize(fontFamily: String, sizedFontFamilies: Array<String>, fontSizes: FloatArray): Float {
  val index = sizedFontFamilies.indexOf(fontFamily)
  if (index < 0) {
    return 0f
  }
  return fontSizes[index]
}

/**
 * Returns the sized font families after the font family gets a size.
 * The [index] is the index of the font family in this array, or -1.
 */
private fun Array<String>.withSizedFontFamily(fontFamily: String, index: Int): Array<String> {
  if (index < 0) {
    return this + fontFamily
  }
  return this
}

/**
 * Returns the font sizes after the font family gets the size, or this array when the size does not change.
 * The [index] is the index of the font family in the sized font families, or -1.
 */
private fun FloatArray.withSize(size: Float, index: Int): FloatArray {
  if (index < 0) {
    return this + size
  }
  if (this[index] == size) {
    return this
  }
  val sizes = copyOf()
  sizes[index] = size
  return sizes
}

/**
 * Returns the real font families after the font family is added, or [realFontFamilies] when they already have it.
 * A new list is the same list as [newEffectiveFontFamilies] when the real and the effective font families were equal before.
 */
private fun realFontFamiliesWith(
  fontFamily: String,
  realFontFamilies: List<String>,
  effectiveFontFamilies: List<String>,
  newEffectiveFontFamilies: List<String>,
): List<String> {
  if (realFontFamilies.contains(fontFamily)) {
    return realFontFamilies
  }
  if (realFontFamilies == effectiveFontFamilies) {
    return newEffectiveFontFamilies
  }
  return realFontFamilies.addIfAbsent(fontFamily)
}

// listOf(a, b) gives Arrays.asList, which allows set()
@Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
private fun List<String>.addIfAbsent(fontFamily: String): List<String> {
  if (contains(fontFamily)) {
    return this
  }
  return when (size) {
    0 -> java.util.List.of(fontFamily)
    1 -> java.util.List.of(this[0], fontFamily)
    else -> java.util.List.copyOf(this + fontFamily)
  }
}

/**
 * Returns [current] when it is equal to [fontFamilies], and an immutable copy of [fontFamilies] otherwise.
 */
private fun immutableFontFamilies(current: List<String>, fontFamilies: List<String>): List<String> {
  if (current == fontFamilies) {
    return current
  }
  return java.util.List.copyOf(fontFamilies)
}

/**
 * Returns [current] when it is equal to [variants], and the variants as an immutable set without a null element otherwise.
 * A persisted state can hold a null element, for example after a manual edit of the settings file.
 */
private fun immutableVariants(current: Set<String>, variants: Set<String?>): Set<String> {
  if (current == variants) {
    return current
  }
  if (variants.any { it == null }) {
    return java.util.Set.copyOf(variants.filterNotNull())
  }
  // No element is null, and Set.copyOf returns an immutable JDK set as is
  @Suppress("UNCHECKED_CAST")
  return java.util.Set.copyOf(variants as Set<String>)
}

/**
 * Returns the variants after the variant is enabled or disabled, or this set when nothing changes.
 */
private fun Set<String>.withVariant(variant: String, enabled: Boolean): Set<String> {
  if (contains(variant) == enabled) {
    return this
  }
  val variants = HashSet(this)
  if (enabled) {
    variants.add(variant)
  } else {
    variants.remove(variant)
  }
  return java.util.Set.copyOf(variants)
}
