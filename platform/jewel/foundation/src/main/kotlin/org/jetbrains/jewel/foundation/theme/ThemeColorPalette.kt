package org.jetbrains.jewel.foundation.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import org.jetbrains.jewel.foundation.GenerateDataFunctions

private val nonIslandsColorKeyRegex: Regex
    get() = "([a-z]+)(\\d+)".toRegex(RegexOption.IGNORE_CASE)

private val islandsColorKeyRegex: Regex
    get() = "([a-z]+)-(\\d+)".toRegex(RegexOption.IGNORE_CASE)

/**
 * A palette of colors provided by the theme.
 *
 * You can access the current palette via [`JewelTheme.colorPalette`][org.jetbrains.jewel.ui.theme.colorPalette].
 *
 * Note that not all Look and Feel themes are guaranteed to have a full palette, and some may not have one at all. The
 * number of colors in each list depends on the implementation in the LaF. It is therefore important to use the
 * `*OrNull` accessors to avoid [IndexOutOfBoundsException]s.
 *
 * @property gray A list of gray colors, from lightest to darkest.
 * @property blue A list of blue colors.
 * @property green A list of green colors.
 * @property red A list of red colors.
 * @property yellow A list of yellow colors.
 * @property orange A list of orange colors.
 * @property purple A list of purple colors.
 * @property teal A list of teal colors.
 * @property rawMap A map of all colors in the palette, with their original keys.
 */
@Suppress("MemberVisibilityCanBePrivate", "KDocUnresolvedReference")
@Immutable
@GenerateDataFunctions
public class ThemeColorPalette(
    public val gray: List<Color>,
    public val blue: List<Color>,
    public val green: List<Color>,
    public val red: List<Color>,
    public val yellow: List<Color>,
    public val orange: List<Color>,
    public val purple: List<Color>,
    public val teal: List<Color>,
    public val rawMap: Map<String, Color>,
    private val isIslands: Boolean,
) {
    @Suppress("DEPRECATION")
    @Deprecated("Use the constructor with isIslands parameter", level = DeprecationLevel.HIDDEN)
    public constructor(
        gray: List<Color>,
        blue: List<Color>,
        green: List<Color>,
        red: List<Color>,
        yellow: List<Color>,
        orange: List<Color>,
        purple: List<Color>,
        teal: List<Color>,
        rawMap: Map<String, Color>,
    ) : this(
        gray = gray,
        blue = blue,
        green = green,
        red = red,
        yellow = yellow,
        orange = orange,
        purple = purple,
        teal = teal,
        rawMap = rawMap,
        isIslands = false,
    )

    /**
     * Retrieves a gray color from the palette by its index. Note that this function is not safe to use and can throw an
     * [IndexOutOfBoundsException] at runtime if the Look and Feel does not provide a full palette.
     *
     * You should use [grayOrNull] instead.
     *
     * Palette indices start at 1; how many entries exist for a color depends on the Look and Feel. Some LaFs may only
     * have a partial palette, or none at all.
     *
     * @param index The 1-based index of the color to retrieve.
     * @return The [Color] at the specified index.
     * @throws IndexOutOfBoundsException if the index is out of bounds.
     */
    @Deprecated(
        "This can throw exceptions if the LaF does not have a full palette, use grayOrNull() instead",
        ReplaceWith("grayOrNull(index)"),
    )
    @Suppress("UnsafeCallOnNullableType")
    public fun gray(index: Int): Color = grayOrNull(index)!!

    /**
     * Retrieves a gray color from the palette by its index, or `null` if the index is out of bounds.
     *
     * Palette indices start at 1 (or 10 for Islands themes); how many entries exist for a color depends on the Look and
     * Feel. Some LaFs may only have a partial palette, or none at all.
     *
     * @param index The 1-based (or 10-based for Islands themes) index of the color to retrieve. Only values of 1 and
     *   above are valid.
     * @return The [Color] at the specified index, or `null` if the index is out of bounds.
     */
    public fun grayOrNull(index: Int): Color? = getByIndexOrNull(gray, index)

    /**
     * Retrieves a blue color from the palette by its index. Note that this function is not safe to use and can throw an
     * [IndexOutOfBoundsException] at runtime if the Look and Feel does not provide a full palette.
     *
     * You should use [blueOrNull] instead.
     *
     * Palette indices start at 1; how many entries exist for a color depends on the Look and Feel. Some LaFs may only
     * have a partial palette, or none at all.
     *
     * @param index The 1-based index of the color to retrieve.
     * @return The [Color] at the specified index.
     * @throws IndexOutOfBoundsException if the index is out of bounds.
     */
    @Deprecated(
        "This can throw exceptions if the LaF does not have a full palette, use blueOrNull() instead",
        ReplaceWith("blueOrNull(index)"),
    )
    @Suppress("UnsafeCallOnNullableType")
    public fun blue(index: Int): Color = blueOrNull(index)!!

    /**
     * Retrieves a blue color from the palette by its index, or `null` if the index is out of bounds.
     *
     * Palette indices start at 1 (or 10 for Islands themes); how many entries exist for a color depends on the Look and
     * Feel. Some LaFs may only have a partial palette, or none at all.
     *
     * @param index The 1-based (or 10-based for Islands themes) index of the color to retrieve. Only values of 1 and
     *   above are valid.
     * @return The [Color] at the specified index, or `null` if the index is out of bounds.
     */
    public fun blueOrNull(index: Int): Color? = getByIndexOrNull(blue, index)

    /**
     * Retrieves a green color from the palette by its index. Note that this function is not safe to use and can throw
     * an [IndexOutOfBoundsException] at runtime if the Look and Feel does not provide a full palette.
     *
     * You should use [greenOrNull] instead.
     *
     * Palette indices start at 1; how many entries exist for a color depends on the Look and Feel. Some LaFs may only
     * have a partial palette, or none at all.
     *
     * @param index The 1-based index of the color to retrieve.
     * @return The [Color] at the specified index.
     * @throws IndexOutOfBoundsException if the index is out of bounds.
     */
    @Deprecated(
        "This can throw exceptions if the LaF does not have a full palette, use greenOrNull() instead",
        ReplaceWith("greenOrNull(index)"),
    )
    @Suppress("UnsafeCallOnNullableType")
    public fun green(index: Int): Color = greenOrNull(index)!!

    /**
     * Retrieves a green color from the palette by its index, or `null` if the index is out of bounds.
     *
     * Palette indices start at 1 (or 10 for Islands themes); how many entries exist for a color depends on the Look and
     * Feel. Some LaFs may only have a partial palette, or none at all.
     *
     * @param index The 1-based (or 10-based for Islands themes) index of the color to retrieve. Only values of 1 and
     *   above are valid.
     * @return The [Color] at the specified index, or `null` if the index is out of bounds.
     */
    public fun greenOrNull(index: Int): Color? = getByIndexOrNull(green, index)

    /**
     * Retrieves a red color from the palette by its index. Note that this function is not safe to use and can throw an
     * [IndexOutOfBoundsException] at runtime if the Look and Feel does not provide a full palette.
     *
     * You should use [redOrNull] instead.
     *
     * Palette indices start at 1; how many entries exist for a color depends on the Look and Feel. Some LaFs may only
     * have a partial palette, or none at all.
     *
     * @param index The 1-based index of the color to retrieve.
     * @return The [Color] at the specified index.
     * @throws IndexOutOfBoundsException if the index is out of bounds.
     */
    @Deprecated(
        "This can throw exceptions if the LaF does not have a full palette, use redOrNull() instead",
        ReplaceWith("redOrNull(index)"),
    )
    @Suppress("UnsafeCallOnNullableType")
    public fun red(index: Int): Color = redOrNull(index)!!

    /**
     * Retrieves a red color from the palette by its index, or `null` if the index is out of bounds.
     *
     * Palette indices start at 1 (or 10 for Islands themes); how many entries exist for a color depends on the Look and
     * Feel. Some LaFs may only have a partial palette, or none at all.
     *
     * @param index The 1-based (or 10-based for Islands themes) index of the color to retrieve. Only values of 1 and
     *   above are valid.
     * @return The [Color] at the specified index, or `null` if the index is out of bounds.
     */
    public fun redOrNull(index: Int): Color? = getByIndexOrNull(red, index)

    /**
     * Retrieves a yellow color from the palette by its index. Note that this function is not safe to use and can throw
     * an [IndexOutOfBoundsException] at runtime if the Look and Feel does not provide a full palette.
     *
     * You should use [yellowOrNull] instead.
     *
     * Palette indices start at 1; how many entries exist for a color depends on the Look and Feel. Some LaFs may only
     * have a partial palette, or none at all.
     *
     * @param index The 1-based index of the color to retrieve.
     * @return The [Color] at the specified index.
     * @throws IndexOutOfBoundsException if the index is out of bounds.
     */
    @Deprecated(
        "This can throw exceptions if the LaF does not have a full palette, use yellowOrNull() instead",
        ReplaceWith("yellowOrNull(index)"),
    )
    @Suppress("UnsafeCallOnNullableType")
    public fun yellow(index: Int): Color = yellowOrNull(index)!!

    /**
     * Retrieves a yellow color from the palette by its index, or `null` if the index is out of bounds.
     *
     * Palette indices start at 1 (or 10 for Islands themes); how many entries exist for a color depends on the Look and
     * Feel. Some LaFs may only have a partial palette, or none at all.
     *
     * @param index The 1-based (or 10-based for Islands themes) index of the color to retrieve. Only values of 1 and
     *   above are valid.
     * @return The [Color] at the specified index, or `null` if the index is out of bounds.
     */
    public fun yellowOrNull(index: Int): Color? = getByIndexOrNull(yellow, index)

    /**
     * Retrieves an orange color from the palette by its index. Note that this function is not safe to use and can throw
     * an [IndexOutOfBoundsException] at runtime if the Look and Feel does not provide a full palette.
     *
     * You should use [orangeOrNull] instead.
     *
     * Palette indices start at 1; how many entries exist for a color depends on the Look and Feel. Some LaFs may only
     * have a partial palette, or none at all.
     *
     * @param index The 1-based index of the color to retrieve.
     * @return The [Color] at the specified index.
     * @throws IndexOutOfBoundsException if the index is out of bounds.
     */
    @Deprecated(
        "This can throw exceptions if the LaF does not have a full palette, use orangeOrNull() instead",
        ReplaceWith("orangeOrNull(index)"),
    )
    @Suppress("UnsafeCallOnNullableType")
    public fun orange(index: Int): Color = orangeOrNull(index)!!

    /**
     * Retrieves an orange color from the palette by its index, or `null` if the index is out of bounds.
     *
     * Palette indices start at 1 (or 10 for Islands themes); how many entries exist for a color depends on the Look and
     * Feel. Some LaFs may only have a partial palette, or none at all.
     *
     * @param index The 1-based (or 10-based for Islands themes) index of the color to retrieve. Only values of 1 and
     *   above are valid.
     * @return The [Color] at the specified index, or `null` if the index is out of bounds.
     */
    public fun orangeOrNull(index: Int): Color? = getByIndexOrNull(orange, index)

    /**
     * Retrieves a purple color from the palette by its index. Note that this function is not safe to use and can throw
     * an [IndexOutOfBoundsException] at runtime if the Look and Feel does not provide a full palette.
     *
     * You should use [purpleOrNull] instead.
     *
     * Palette indices start at 1; how many entries exist for a color depends on the Look and Feel. Some LaFs may only
     * have a partial palette, or none at all.
     *
     * @param index The 1-based index of the color to retrieve.
     * @return The [Color] at the specified index.
     * @throws IndexOutOfBoundsException if the index is out of bounds.
     */
    @Deprecated(
        "This can throw exceptions if the LaF does not have a full palette, use purpleOrNull() instead",
        ReplaceWith("purpleOrNull(index)"),
    )
    @Suppress("UnsafeCallOnNullableType")
    public fun purple(index: Int): Color = purpleOrNull(index)!!

    /**
     * Retrieves a purple color from the palette by its index, or `null` if the index is out of bounds.
     *
     * Palette indices start at 1 (or 10 for Islands themes); how many entries exist for a color depends on the Look and
     * Feel. Some LaFs may only have a partial palette, or none at all.
     *
     * @param index The 1-based (or 10-based for Islands themes) index of the color to retrieve. Only values of 1 and
     *   above are valid.
     * @return The [Color] at the specified index, or `null` if the index is out of bounds.
     */
    public fun purpleOrNull(index: Int): Color? = getByIndexOrNull(purple, index)

    /**
     * Retrieves a teal color from the palette by its index. Note that this function is not safe to use and can throw an
     * [IndexOutOfBoundsException] at runtime if the Look and Feel does not provide a full palette.
     *
     * You should use [tealOrNull] instead.
     *
     * Palette indices start at 1; how many entries exist for a color depends on the Look and Feel. Some LaFs may only
     * have a partial palette, or none at all.
     *
     * @param index The 1-based index of the color to retrieve.
     * @return The [Color] at the specified index.
     * @throws IndexOutOfBoundsException if the index is out of bounds.
     */
    @Deprecated(
        "This can throw exceptions if the LaF does not have a full palette, use tealOrNull() instead",
        ReplaceWith("tealOrNull(index)"),
    )
    @Suppress("UnsafeCallOnNullableType")
    public fun teal(index: Int): Color = tealOrNull(index)!!

    /**
     * Retrieves a teal color from the palette by its index, or `null` if the index is out of bounds.
     *
     * Palette indices start at 1 (or 10 for Islands themes); how many entries exist for a color depends on the Look and
     * Feel. Some LaFs may only have a partial palette, or none at all.
     *
     * @param index The 1-based (or 10-based for Islands themes) index of the color to retrieve. Only values of 1 and
     *   above are valid.
     * @return The [Color] at the specified index, or `null` if the index is out of bounds.
     */
    public fun tealOrNull(index: Int): Color? = getByIndexOrNull(teal, index)

    private fun getByIndexOrNull(list: List<Color>, index: Int): Color? =
        if (isIslands) {
            if (index % 10 != 0) null else list.getOrNull(index / 10 - 1)
        } else {
            list.getOrNull(index - 1)
        }

    /**
     * Looks up a color in the palette by its key. The key can be in the format "colorNameN" (e.g., "gray1", "blue12")
     * or a raw key from the Look and Feel theme.
     *
     * @param colorKey The key of the color to look up.
     * @return The [Color] associated with the key, or `null` if the key is not found.
     */
    public fun lookup(colorKey: String): Color? {
        val colorKeyRegex = if (isIslands) islandsColorKeyRegex else nonIslandsColorKeyRegex
        val result = colorKeyRegex.matchEntire(colorKey.trim())
        val colorGroup = result?.groupValues?.getOrNull(1)?.lowercase()
        val colorIndex = result?.groupValues?.getOrNull(2)?.toIntOrNull()

        if (colorGroup == null || colorIndex == null) {
            return rawMap[colorKey]
        }

        return when (colorGroup) {
            "grey",
            "gray" -> grayOrNull(colorIndex)
            "blue" -> blueOrNull(colorIndex)
            "green" -> greenOrNull(colorIndex)
            "red" -> redOrNull(colorIndex)
            "yellow" -> yellowOrNull(colorIndex)
            "orange" -> orangeOrNull(colorIndex)
            "purple" -> purpleOrNull(colorIndex)
            "teal" -> tealOrNull(colorIndex)
            else -> null
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ThemeColorPalette

        if (gray != other.gray) return false
        if (blue != other.blue) return false
        if (green != other.green) return false
        if (red != other.red) return false
        if (yellow != other.yellow) return false
        if (orange != other.orange) return false
        if (purple != other.purple) return false
        if (teal != other.teal) return false
        if (rawMap != other.rawMap) return false
        if (isIslands != other.isIslands) return false

        return true
    }

    override fun hashCode(): Int {
        var result = gray.hashCode()
        result = 31 * result + blue.hashCode()
        result = 31 * result + green.hashCode()
        result = 31 * result + red.hashCode()
        result = 31 * result + yellow.hashCode()
        result = 31 * result + orange.hashCode()
        result = 31 * result + purple.hashCode()
        result = 31 * result + teal.hashCode()
        result = 31 * result + rawMap.hashCode()
        result = 31 * result + isIslands.hashCode()
        return result
    }

    override fun toString(): String {
        return "ThemeColorPalette(" +
            "gray=$gray, " +
            "blue=$blue, " +
            "green=$green, " +
            "red=$red, " +
            "yellow=$yellow, " +
            "orange=$orange, " +
            "purple=$purple, " +
            "teal=$teal, " +
            "rawMap=$rawMap, " +
            "isIslands=$isIslands" +
            ")"
    }

    public companion object {
        public val Empty: ThemeColorPalette =
            ThemeColorPalette(
                gray = emptyList(),
                blue = emptyList(),
                green = emptyList(),
                red = emptyList(),
                yellow = emptyList(),
                orange = emptyList(),
                purple = emptyList(),
                teal = emptyList(),
                rawMap = emptyMap(),
                isIslands = false,
            )
    }
}
