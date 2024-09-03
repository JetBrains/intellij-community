package org.jetbrains.jewel.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.foundation.theme.JewelTheme

/**
 * A quick way to obtain text styles derived from [the default `TextStyle`][JewelTheme.defaultTextStyle]. These match
 * the functionality provided by `JBFont` in the IntelliJ Platform.
 */
public object Typography {
    /** The text style to use for labels. Identical to [JewelTheme.defaultTextStyle]. */
    @Composable public fun labelTextStyle(): TextStyle = JewelTheme.defaultTextStyle

    /** The text size to use for labels. Identical to the size set in [JewelTheme.defaultTextStyle]. */
    @Composable public fun labelTextSize(): TextUnit = JewelTheme.defaultTextStyle.fontSize

    /** The text style to use for h0 titles. Derived from [JewelTheme.defaultTextStyle]. */
    @Composable
    public fun h0TextStyle(): TextStyle =
        JewelTheme.defaultTextStyle.copy(fontSize = labelTextSize() + 12.sp, fontWeight = FontWeight.Bold)

    /** The text style to use for h1 titles. Derived from [JewelTheme.defaultTextStyle]. */
    @Composable
    public fun h1TextStyle(): TextStyle =
        JewelTheme.defaultTextStyle.copy(fontSize = labelTextSize() + 9.sp, fontWeight = FontWeight.Bold)

    /** The text style to use for h2 titles. Derived from [JewelTheme.defaultTextStyle]. */
    @Composable
    public fun h2TextStyle(): TextStyle = JewelTheme.defaultTextStyle.copy(fontSize = labelTextSize() + 5.sp)

    /** The text style to use for h3 titles. Derived from [JewelTheme.defaultTextStyle]. */
    @Composable
    public fun h3TextStyle(): TextStyle = JewelTheme.defaultTextStyle.copy(fontSize = labelTextSize() + 3.sp)

    /** The text style to use for h4 titles. Derived from [JewelTheme.defaultTextStyle]. */
    @Composable
    public fun h4TextStyle(): TextStyle =
        JewelTheme.defaultTextStyle.copy(fontSize = labelTextSize() + 1.sp, fontWeight = FontWeight.Bold)

    /** The text style used for code editors. Usually is a monospaced font. */
    @Composable public fun editorTextStyle(): TextStyle = JewelTheme.editorTextStyle

    /** The text style used for code consoles. Usually is a monospaced font. Can be the same as [editorTextStyle]. */
    @Composable public fun consoleTextStyle(): TextStyle = JewelTheme.consoleTextStyle
}

public operator fun TextUnit.plus(other: TextUnit): TextUnit =
    when {
        isSp && other.isSp -> TextUnit(value + other.value, TextUnitType.Sp)
        isEm && other.isEm -> TextUnit(value + other.value, TextUnitType.Em)
        isUnspecified && other.isUnspecified -> TextUnit(value + other.value, TextUnitType.Unspecified)
        else -> error("Can't add together different TextUnits. Got $type and ${other.type}")
    }

public operator fun TextUnit.minus(other: TextUnit): TextUnit =
    when {
        isSp && other.isSp -> TextUnit(value - other.value, TextUnitType.Sp)
        isEm && other.isEm -> TextUnit(value - other.value, TextUnitType.Em)
        isUnspecified && other.isUnspecified -> TextUnit(value - other.value, TextUnitType.Unspecified)
        else -> error("Can't subtract different TextUnits. Got $type and ${other.type}")
    }
