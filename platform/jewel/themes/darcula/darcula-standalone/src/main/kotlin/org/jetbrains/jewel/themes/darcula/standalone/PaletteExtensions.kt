package org.jetbrains.jewel.themes.darcula.standalone

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.IntelliJPalette
import org.jetbrains.jewel.toBrush
import org.jetbrains.jewel.util.isMacOs

@Suppress("unused")
val Color.Companion.IntelliJWhite
    get() = Color(0xFFF2F2F2)

@Suppress("unused")
val Color.Companion.IntelliJGrey
    get() = Color(0xFF3C3F41)

val Color.Companion.IntelliJDarculaSelection
    get() = Color(0xFF4B6EAF)

val Color.Companion.IntelliJLightSelection
    get() = Color(0xFF2675BF)

val IntelliJPalette.Checkbox.Companion.light
    get() = IntelliJPalette.Checkbox(
        background = Color.IntelliJWhite,
        foreground = Color.Black,
        foregroundDisabled = Color(0xFF8C8C8C)
    )

val IntelliJPalette.Checkbox.Companion.darcula
    get() = IntelliJPalette.Checkbox(
        background = Color.IntelliJGrey,
        foreground = Color(0xFFBBBBBB),
        foregroundDisabled = Color(0xFF999999)
    )

val IntelliJPalette.RadioButton.Companion.light
    get() = IntelliJPalette.RadioButton(
        background = Color.IntelliJWhite,
        foreground = Color.Black,
        foregroundDisabled = Color(0xFF8C8C8C)
    )

val IntelliJPalette.RadioButton.Companion.darcula
    get() = IntelliJPalette.RadioButton(
        background = Color.IntelliJGrey,
        foreground = Color(0xFFBBBBBB),
        foregroundDisabled = Color(0xFF999999)
    )

val IntelliJPalette.TextField.Companion.light
    get() = IntelliJPalette.TextField(
        background = Color(0xFFFFFFFF),
        backgroundDisabled = Color.IntelliJWhite,
        foreground = Color.Black,
        foregroundDisabled = Color(0xFF8C8C8C)
    )

val IntelliJPalette.TextField.Companion.darcula
    get() = IntelliJPalette.TextField(
        background = Color(0xFF45494A),
        backgroundDisabled = Color.IntelliJGrey,
        foreground = Color(0xFFBBBBBB),
        foregroundDisabled = Color(0xFF777777)
    )

val IntelliJPalette.Button.Companion.light
    get() = IntelliJPalette.Button(
        background = Color(0xFFFFFFFF).toBrush(),
        foreground = Color.Black,
        foregroundDisabled = Color(0xFF8C8C8C),
        shadow = Color(0x00A6A6A6),
        stroke = Color(0XFFC4C4C4).toBrush(),
        strokeFocused = Color(0xFF87AFDA),
        strokeDisabled = Color(0xFFCFCFCF),
        defaultBackground = Brush.verticalGradient(listOf(Color(0xFF528CC7), Color(0xFF4989CC))),
        defaultForeground = Color.White,
        defaultStroke = Color(0xFF487EB8).toBrush(), // Brush.verticalGradient(listOf(Color(0xFF487EB8), Color(0xFF346DAD))),
        defaultStrokeFocused = Color(0xFFA9C9F5),
        defaultShadow = Color(0x00A6A6A6)
    )

val IntelliJPalette.Button.Companion.darcula
    get() = IntelliJPalette.Button(
        background = Color(0xFF4C5052).toBrush(),
        foreground = Color(0xFFBBBBBB),
        foregroundDisabled = Color(0xFF777777),
        shadow = Color(0xFF999999),
        stroke = Color(0XFF5E6060).toBrush(),
        strokeFocused = Color(0xFF466D94),
        strokeDisabled = Color(0xFF5E6060),
        defaultBackground = Color(0xFF365880).toBrush(),
        defaultForeground = Color(0xFFBBBBBB),
        defaultStroke = Color(0xFF4C708C).toBrush(),
        defaultStrokeFocused = Color(0xFFA9C9F5),
        defaultShadow = Color.Unspecified
    )

val IntelliJPalette.Separator.Companion.light
    get() = IntelliJPalette.Separator(
        color = Color(0xFFD1D1D1),
        background = Color.Unspecified
    )

val IntelliJPalette.Separator.Companion.darcula
    get() = IntelliJPalette.Separator(
        color = Color(0xFF515151),
        background = Color.Unspecified
    )

val IntelliJPalette.Scrollbar.Companion.light
    get() = IntelliJPalette.Scrollbar(
        thumbIdleColor = if (isMacOs()) Color(0x00000000) else Color(0x33737373),
        thumbHoverColor = if (isMacOs()) Color(0x80000000) else Color(0x47737373)
    )

val IntelliJPalette.Scrollbar.Companion.darcula
    get() = IntelliJPalette.Scrollbar(
        thumbIdleColor = if (isMacOs()) Color(0x00808080) else Color(0x47A6A6A6),
        thumbHoverColor = if (isMacOs()) Color(0x8C808080) else Color(0x59A6A6A6)
    )

val IntelliJPalette.Tab.Companion.light
    get() = IntelliJPalette.Tab(
        underlineColor = Color(0xFF4083c9),
        hoveredBackgroundColor = Color(0xFFD9D9D9),
        tabSelectionHeight = 3.dp
    )

val IntelliJPalette.Tab.Companion.darcula
    get() = IntelliJPalette.Tab(
        underlineColor = Color(0xFF4A88C7),
        hoveredBackgroundColor = Color(0xFF2E3133),
        tabSelectionHeight = 3.dp
    )

val IntelliJPalette.Companion.light
    get() = IntelliJPalette(
        isLight = true,
        button = IntelliJPalette.Button.light,
        checkbox = IntelliJPalette.Checkbox.light,
        radioButton = IntelliJPalette.RadioButton.light,
        textField = IntelliJPalette.TextField.light,
        background = Color.IntelliJWhite,
        text = Color.Black,
        textDisabled = Color(0xFF8C8C8C),
        controlStroke = Color(0xFFC4C4C4),
        controlStrokeDisabled = Color(0xFFCFCFCF),
        controlStrokeFocused = Color(0XFF87AFDA), // Component.focusedBorderColor
        controlFocusHalo = Color(0XFF97C3F3),
        controlInactiveHaloError = Color(0XFFEBBCBC),
        controlInactiveHaloWarning = Color(0XFFFFD385),
        controlHaloError = Color(0XFFE53E4D),
        controlHaloWarning = Color(0XFFE2A53A),
        separator = IntelliJPalette.Separator.light,
        scrollbar = IntelliJPalette.Scrollbar.light,
        treeView = IntelliJPalette.TreeView.Companion.light,
        slider = IntelliJPalette.Slider.Companion.light,
        tab = IntelliJPalette.Tab.light
    )

val IntelliJPalette.Companion.darcula
    get() = IntelliJPalette(
        isLight = false,
        button = IntelliJPalette.Button.darcula,
        checkbox = IntelliJPalette.Checkbox.darcula,
        textField = IntelliJPalette.TextField.darcula,
        radioButton = IntelliJPalette.RadioButton.darcula,
        background = Color.IntelliJGrey,
        text = Color(0xFFBBBBBB),
        textDisabled = Color(0xFF777777),
        controlStroke = Color(0xFF646464),
        controlStrokeDisabled = Color(0xFF646464),
        controlStrokeFocused = Color(0XFF466D94),
        controlFocusHalo = Color(0XFF3D6185),
        controlInactiveHaloError = Color(0XFF725252),
        controlInactiveHaloWarning = Color(0XFF6E5324),
        controlHaloError = Color(0XFF8B3C3C),
        controlHaloWarning = Color(0XFFAC7920),
        separator = IntelliJPalette.Separator.darcula,
        scrollbar = IntelliJPalette.Scrollbar.darcula,
        treeView = IntelliJPalette.TreeView.darcula,
        slider = IntelliJPalette.Slider.darcula,
        tab = IntelliJPalette.Tab.darcula
    )

val IntelliJPalette.TreeView.Companion.light
    get() = IntelliJPalette.TreeView(
        focusedSelectedElementBackground = Color.IntelliJLightSelection,
        background = Color.White
    )

val IntelliJPalette.TreeView.Companion.darcula
    get() = IntelliJPalette.TreeView(
        focusedSelectedElementBackground = Color.IntelliJDarculaSelection,
        background = Color.IntelliJGrey
    )

val IntelliJPalette.Slider.Companion.light
    get() = IntelliJPalette.Slider(
        foreground = Color.Black,
        background = Color.IntelliJWhite
    )

val IntelliJPalette.Slider.Companion.darcula
    get() = IntelliJPalette.Slider(
        foreground = Color(0xFFBBBBBB),
        background = Color.IntelliJGrey
    )
