package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.intui.core.theme.IntUiDarkTheme
import org.jetbrains.jewel.intui.core.theme.IntUiLightTheme
import org.jetbrains.jewel.ui.component.styling.BadgeColors
import org.jetbrains.jewel.ui.component.styling.BadgeMetrics
import org.jetbrains.jewel.ui.component.styling.BadgeStyle
import org.jetbrains.jewel.ui.component.styling.BadgeStyles

public fun BadgeStyles.Companion.light(
    blue: BadgeStyle = BadgeStyle.Blue.light(),
    blueSecondary: BadgeStyle = BadgeStyle.BlueSecondary.light(),
    green: BadgeStyle = BadgeStyle.Green.light(),
    greenSecondary: BadgeStyle = BadgeStyle.GreenSecondary.light(),
    purpleSecondary: BadgeStyle = BadgeStyle.PurpleSecondary.light(),
    graySecondary: BadgeStyle = BadgeStyle.GraySecondary.light(),
): BadgeStyles =
    BadgeStyles(
        blue = blue,
        blueSecondary = blueSecondary,
        green = green,
        greenSecondary = greenSecondary,
        purpleSecondary = purpleSecondary,
        graySecondary = graySecondary,
    )

public fun BadgeStyles.Companion.dark(
    blue: BadgeStyle = BadgeStyle.Blue.dark(),
    blueSecondary: BadgeStyle = BadgeStyle.BlueSecondary.dark(),
    green: BadgeStyle = BadgeStyle.Green.dark(),
    greenSecondary: BadgeStyle = BadgeStyle.GreenSecondary.dark(),
    purpleSecondary: BadgeStyle = BadgeStyle.PurpleSecondary.dark(),
    graySecondary: BadgeStyle = BadgeStyle.GraySecondary.dark(),
): BadgeStyles =
    BadgeStyles(
        blueSecondary = blueSecondary,
        blue = blue,
        green = green,
        greenSecondary = greenSecondary,
        purpleSecondary = purpleSecondary,
        graySecondary = graySecondary,
    )

public val BadgeStyle.Companion.Blue: IntUiBlueBadgeStyleFactory
    get() = IntUiBlueBadgeStyleFactory

public object IntUiBlueBadgeStyleFactory {
    public fun light(
        colors: BadgeColors = BadgeColors.Blue.light(),
        metrics: BadgeMetrics = BadgeMetrics.default(),
    ): BadgeStyle = BadgeStyle(colors = colors, metrics = metrics)

    public fun dark(
        colors: BadgeColors = BadgeColors.Blue.dark(),
        metrics: BadgeMetrics = BadgeMetrics.default(),
    ): BadgeStyle = BadgeStyle(colors = colors, metrics = metrics)
}

public val BadgeColors.Companion.Blue: IntUiBlueBadgeColorsFactory
    get() = IntUiBlueBadgeColorsFactory

public object IntUiBlueBadgeColorsFactory {
    private val lightBackground = IntUiLightTheme.colors.blueOrNull(4) ?: Color(0xFF3574F0)
    private val lightContent = IntUiLightTheme.colors.grayOrNull(14) ?: Color(0xFFFFFFFF)
    private val lightDisabled = IntUiDisabledBadgeColorsFactory.light()

    private val darkBackground = IntUiDarkTheme.colors.blueOrNull(8) ?: Color(0xFF548AF7)
    private val darkContent = IntUiDarkTheme.colors.grayOrNull(1) ?: Color(0xFF1E1F22)
    private val darkDisabled = IntUiDisabledBadgeColorsFactory.dark()

    public fun light(
        background: Brush = SolidColor(lightBackground),
        backgroundDisabled: Brush = SolidColor(lightDisabled.background),
        backgroundFocused: Brush = SolidColor(lightBackground),
        backgroundPressed: Brush = SolidColor(lightBackground),
        backgroundHovered: Brush = SolidColor(lightBackground),
        content: Color = lightContent,
        contentDisabled: Color = lightDisabled.content,
        contentFocused: Color = lightContent,
        contentPressed: Color = lightContent,
        contentHovered: Color = lightContent,
    ): BadgeColors =
        BadgeColors(
            background = background,
            backgroundDisabled = backgroundDisabled,
            backgroundFocused = backgroundFocused,
            backgroundPressed = backgroundPressed,
            backgroundHovered = backgroundHovered,
            content = content,
            contentDisabled = contentDisabled,
            contentFocused = contentFocused,
            contentPressed = contentPressed,
            contentHovered = contentHovered,
        )

    public fun dark(
        background: Brush = SolidColor(darkBackground),
        backgroundDisabled: Brush = SolidColor(darkDisabled.background),
        backgroundFocused: Brush = SolidColor(darkBackground),
        backgroundPressed: Brush = SolidColor(darkBackground),
        backgroundHovered: Brush = SolidColor(darkBackground),
        content: Color = darkContent,
        contentDisabled: Color = darkDisabled.content,
        contentFocused: Color = darkContent,
        contentPressed: Color = darkContent,
        contentHovered: Color = darkContent,
    ): BadgeColors =
        BadgeColors(
            background = background,
            backgroundDisabled = backgroundDisabled,
            backgroundFocused = backgroundFocused,
            backgroundPressed = backgroundPressed,
            backgroundHovered = backgroundHovered,
            content = content,
            contentDisabled = contentDisabled,
            contentFocused = contentFocused,
            contentPressed = contentPressed,
            contentHovered = contentHovered,
        )
}

public val BadgeStyle.Companion.BlueSecondary: IntUiBlueSecondaryBadgeStyleFactory
    get() = IntUiBlueSecondaryBadgeStyleFactory

public object IntUiBlueSecondaryBadgeStyleFactory {
    public fun light(
        colors: BadgeColors = BadgeColors.BlueSecondary.light(),
        metrics: BadgeMetrics = BadgeMetrics.default(),
    ): BadgeStyle = BadgeStyle(colors = colors, metrics = metrics)

    public fun dark(
        colors: BadgeColors = BadgeColors.BlueSecondary.dark(),
        metrics: BadgeMetrics = BadgeMetrics.default(),
    ): BadgeStyle = BadgeStyle(colors = colors, metrics = metrics)
}

public val BadgeColors.Companion.BlueSecondary: IntUiBlueSecondaryBadgeColorsFactory
    get() = IntUiBlueSecondaryBadgeColorsFactory

public object IntUiBlueSecondaryBadgeColorsFactory {
    private val lightBackground = Color(0x293574F0)
    private val lightContent = IntUiLightTheme.colors.blueOrNull(1) ?: Color(0xFF2E55A3)
    private val lightDisabled = IntUiDisabledBadgeColorsFactory.light()

    private val darkBackground = Color(0xCC35538F)
    private val darkContent = IntUiDarkTheme.colors.blueOrNull(12) ?: Color(0xFFB5CEFF)
    private val darkDisabled = IntUiDisabledBadgeColorsFactory.dark()

    public fun light(
        background: Brush = SolidColor(lightBackground),
        backgroundDisabled: Brush = SolidColor(lightDisabled.background),
        backgroundFocused: Brush = SolidColor(lightBackground),
        backgroundPressed: Brush = SolidColor(lightBackground),
        backgroundHovered: Brush = SolidColor(lightBackground),
        content: Color = lightContent,
        contentDisabled: Color = lightDisabled.content,
        contentFocused: Color = lightContent,
        contentPressed: Color = lightContent,
        contentHovered: Color = lightContent,
    ): BadgeColors =
        BadgeColors(
            background = background,
            backgroundDisabled = backgroundDisabled,
            backgroundFocused = backgroundFocused,
            backgroundPressed = backgroundPressed,
            backgroundHovered = backgroundHovered,
            content = content,
            contentDisabled = contentDisabled,
            contentFocused = contentFocused,
            contentPressed = contentPressed,
            contentHovered = contentHovered,
        )

    public fun dark(
        background: Brush = SolidColor(darkBackground),
        backgroundDisabled: Brush = SolidColor(darkDisabled.background),
        backgroundFocused: Brush = SolidColor(darkBackground),
        backgroundPressed: Brush = SolidColor(darkBackground),
        backgroundHovered: Brush = SolidColor(darkBackground),
        content: Color = darkContent,
        contentDisabled: Color = darkDisabled.content,
        contentFocused: Color = darkContent,
        contentPressed: Color = darkContent,
        contentHovered: Color = darkContent,
    ): BadgeColors =
        BadgeColors(
            background = background,
            backgroundDisabled = backgroundDisabled,
            backgroundFocused = backgroundFocused,
            backgroundPressed = backgroundPressed,
            backgroundHovered = backgroundHovered,
            content = content,
            contentDisabled = contentDisabled,
            contentFocused = contentFocused,
            contentPressed = contentPressed,
            contentHovered = contentHovered,
        )
}

public val BadgeStyle.Companion.PurpleSecondary: IntUiPurpleSecondaryBadgeStyleFactory
    get() = IntUiPurpleSecondaryBadgeStyleFactory

public object IntUiPurpleSecondaryBadgeStyleFactory {
    public fun light(
        colors: BadgeColors = BadgeColors.PurpleSecondary.light(),
        metrics: BadgeMetrics = BadgeMetrics.default(),
    ): BadgeStyle = BadgeStyle(colors = colors, metrics = metrics)

    public fun dark(
        colors: BadgeColors = BadgeColors.PurpleSecondary.dark(),
        metrics: BadgeMetrics = BadgeMetrics.default(),
    ): BadgeStyle = BadgeStyle(colors = colors, metrics = metrics)
}

public val BadgeColors.Companion.PurpleSecondary: IntUiPurpleSecondaryBadgeColorsFactory
    get() = IntUiPurpleSecondaryBadgeColorsFactory

public object IntUiPurpleSecondaryBadgeColorsFactory {
    private val lightBackground = Color(0x29834DF0)
    private val lightContent = IntUiLightTheme.colors.purpleOrNull(1) ?: Color(0xFF55339C)
    private val lightDisabled = IntUiDisabledBadgeColorsFactory.light()

    private val darkBackground = Color(0xCC6C469C)
    private val darkContent = IntUiDarkTheme.colors.purpleOrNull(12) ?: Color(0xFFE4CEFF)
    private val darkDisabled = IntUiDisabledBadgeColorsFactory.dark()

    public fun light(
        background: Brush = SolidColor(lightBackground),
        backgroundDisabled: Brush = SolidColor(lightDisabled.background),
        backgroundFocused: Brush = SolidColor(lightBackground),
        backgroundPressed: Brush = SolidColor(lightBackground),
        backgroundHovered: Brush = SolidColor(lightBackground),
        content: Color = lightContent,
        contentDisabled: Color = lightDisabled.content,
        contentFocused: Color = lightContent,
        contentPressed: Color = lightContent,
        contentHovered: Color = lightContent,
    ): BadgeColors =
        BadgeColors(
            background = background,
            backgroundDisabled = backgroundDisabled,
            backgroundFocused = backgroundFocused,
            backgroundPressed = backgroundPressed,
            backgroundHovered = backgroundHovered,
            content = content,
            contentDisabled = contentDisabled,
            contentFocused = contentFocused,
            contentPressed = contentPressed,
            contentHovered = contentHovered,
        )

    public fun dark(
        background: Brush = SolidColor(darkBackground),
        backgroundDisabled: Brush = SolidColor(darkDisabled.background),
        backgroundFocused: Brush = SolidColor(darkBackground),
        backgroundPressed: Brush = SolidColor(darkBackground),
        backgroundHovered: Brush = SolidColor(darkBackground),
        content: Color = darkContent,
        contentDisabled: Color = darkDisabled.content,
        contentFocused: Color = darkContent,
        contentPressed: Color = darkContent,
        contentHovered: Color = darkContent,
    ): BadgeColors =
        BadgeColors(
            background = background,
            backgroundDisabled = backgroundDisabled,
            backgroundFocused = backgroundFocused,
            backgroundPressed = backgroundPressed,
            backgroundHovered = backgroundHovered,
            content = content,
            contentDisabled = contentDisabled,
            contentFocused = contentFocused,
            contentPressed = contentPressed,
            contentHovered = contentHovered,
        )
}

public val BadgeStyle.Companion.Green: IntUiGreenBadgeStyleFactory
    get() = IntUiGreenBadgeStyleFactory

public object IntUiGreenBadgeStyleFactory {
    public fun light(
        colors: BadgeColors = BadgeColors.Green.light(),
        metrics: BadgeMetrics = BadgeMetrics.default(),
    ): BadgeStyle = BadgeStyle(colors = colors, metrics = metrics)

    public fun dark(
        colors: BadgeColors = BadgeColors.Green.dark(),
        metrics: BadgeMetrics = BadgeMetrics.default(),
    ): BadgeStyle = BadgeStyle(colors = colors, metrics = metrics)
}

public val BadgeColors.Companion.Green: IntUiGreenBadgeColorsFactory
    get() = IntUiGreenBadgeColorsFactory

public object IntUiGreenBadgeColorsFactory {
    private val lightBackground = IntUiLightTheme.colors.greenOrNull(4) ?: Color(0xFF208A3C)
    private val lightContent = IntUiLightTheme.colors.grayOrNull(14) ?: Color(0xFFFFFFFF)
    private val lightDisabled = IntUiDisabledBadgeColorsFactory.light()

    private val darkBackground = IntUiDarkTheme.colors.greenOrNull(6) ?: Color(0xFF57965C)
    private val darkContent = IntUiDarkTheme.colors.grayOrNull(1) ?: Color(0xFF1E1F22)
    private val darkDisabled = IntUiDisabledBadgeColorsFactory.dark()

    public fun light(
        background: Brush = SolidColor(lightBackground),
        backgroundDisabled: Brush = SolidColor(lightDisabled.background),
        backgroundFocused: Brush = SolidColor(lightBackground),
        backgroundPressed: Brush = SolidColor(lightBackground),
        backgroundHovered: Brush = SolidColor(lightBackground),
        content: Color = lightContent,
        contentDisabled: Color = lightDisabled.content,
        contentFocused: Color = lightContent,
        contentPressed: Color = lightContent,
        contentHovered: Color = lightContent,
    ): BadgeColors =
        BadgeColors(
            background = background,
            backgroundDisabled = backgroundDisabled,
            backgroundFocused = backgroundFocused,
            backgroundPressed = backgroundPressed,
            backgroundHovered = backgroundHovered,
            content = content,
            contentDisabled = contentDisabled,
            contentFocused = contentFocused,
            contentPressed = contentPressed,
            contentHovered = contentHovered,
        )

    public fun dark(
        background: Brush = SolidColor(darkBackground),
        backgroundDisabled: Brush = SolidColor(darkDisabled.background),
        backgroundFocused: Brush = SolidColor(darkBackground),
        backgroundPressed: Brush = SolidColor(darkBackground),
        backgroundHovered: Brush = SolidColor(darkBackground),
        content: Color = darkContent,
        contentDisabled: Color = darkDisabled.content,
        contentFocused: Color = darkContent,
        contentPressed: Color = darkContent,
        contentHovered: Color = darkContent,
    ): BadgeColors =
        BadgeColors(
            background = background,
            backgroundDisabled = backgroundDisabled,
            backgroundFocused = backgroundFocused,
            backgroundPressed = backgroundPressed,
            backgroundHovered = backgroundHovered,
            content = content,
            contentDisabled = contentDisabled,
            contentFocused = contentFocused,
            contentPressed = contentPressed,
            contentHovered = contentHovered,
        )
}

public val BadgeStyle.Companion.GreenSecondary: IntUiGreenSecondaryBadgeStyleFactory
    get() = IntUiGreenSecondaryBadgeStyleFactory

public object IntUiGreenSecondaryBadgeStyleFactory {
    public fun light(
        colors: BadgeColors = BadgeColors.GreenSecondary.light(),
        metrics: BadgeMetrics = BadgeMetrics.default(),
    ): BadgeStyle = BadgeStyle(colors = colors, metrics = metrics)

    public fun dark(
        colors: BadgeColors = BadgeColors.GreenSecondary.dark(),
        metrics: BadgeMetrics = BadgeMetrics.default(),
    ): BadgeStyle = BadgeStyle(colors = colors, metrics = metrics)
}

public val BadgeColors.Companion.GreenSecondary: IntUiGreenSecondaryBadgeColorsFactory
    get() = IntUiGreenSecondaryBadgeColorsFactory

public object IntUiGreenSecondaryBadgeColorsFactory {
    private val lightBackground = Color(0x29208A3C)
    private val lightContent = IntUiLightTheme.colors.greenOrNull(1) ?: Color(0xFF1E6B33)
    private val lightDisabled = IntUiDisabledBadgeColorsFactory.light()

    private val darkBackground = Color(0xCC436946)
    private val darkContent = IntUiDarkTheme.colors.greenOrNull(12) ?: Color(0xFFD4FAD7)
    private val darkDisabled = IntUiDisabledBadgeColorsFactory.dark()

    public fun light(
        background: Brush = SolidColor(lightBackground),
        backgroundDisabled: Brush = SolidColor(lightDisabled.background),
        backgroundFocused: Brush = SolidColor(lightBackground),
        backgroundPressed: Brush = SolidColor(lightBackground),
        backgroundHovered: Brush = SolidColor(lightBackground),
        content: Color = lightContent,
        contentDisabled: Color = lightDisabled.content,
        contentFocused: Color = lightContent,
        contentPressed: Color = lightContent,
        contentHovered: Color = lightContent,
    ): BadgeColors =
        BadgeColors(
            background = background,
            backgroundDisabled = backgroundDisabled,
            backgroundFocused = backgroundFocused,
            backgroundPressed = backgroundPressed,
            backgroundHovered = backgroundHovered,
            content = content,
            contentDisabled = contentDisabled,
            contentFocused = contentFocused,
            contentPressed = contentPressed,
            contentHovered = contentHovered,
        )

    public fun dark(
        background: Brush = SolidColor(darkBackground),
        backgroundDisabled: Brush = SolidColor(darkDisabled.background),
        backgroundFocused: Brush = SolidColor(darkBackground),
        backgroundPressed: Brush = SolidColor(darkBackground),
        backgroundHovered: Brush = SolidColor(darkBackground),
        content: Color = darkContent,
        contentDisabled: Color = darkDisabled.content,
        contentFocused: Color = darkContent,
        contentPressed: Color = darkContent,
        contentHovered: Color = darkContent,
    ): BadgeColors =
        BadgeColors(
            background = background,
            backgroundDisabled = backgroundDisabled,
            backgroundFocused = backgroundFocused,
            backgroundPressed = backgroundPressed,
            backgroundHovered = backgroundHovered,
            content = content,
            contentDisabled = contentDisabled,
            contentFocused = contentFocused,
            contentPressed = contentPressed,
            contentHovered = contentHovered,
        )
}

public val BadgeStyle.Companion.GraySecondary: IntUiGraySecondaryBadgeStyleFactory
    get() = IntUiGraySecondaryBadgeStyleFactory

public object IntUiGraySecondaryBadgeStyleFactory {
    public fun light(
        colors: BadgeColors = BadgeColors.GraySecondary.light(),
        metrics: BadgeMetrics = BadgeMetrics.default(),
    ): BadgeStyle = BadgeStyle(colors = colors, metrics = metrics)

    public fun dark(
        colors: BadgeColors = BadgeColors.GraySecondary.dark(),
        metrics: BadgeMetrics = BadgeMetrics.default(),
    ): BadgeStyle = BadgeStyle(colors = colors, metrics = metrics)
}

public val BadgeColors.Companion.GraySecondary: IntUiGraySecondaryBadgeColorsFactory
    get() = IntUiGraySecondaryBadgeColorsFactory

public object IntUiGraySecondaryBadgeColorsFactory {
    private val lightBackground = Color(0x1F6C707E)
    private val lightContent = IntUiLightTheme.colors.grayOrNull(6) ?: Color(0xFF6C707E)
    private val lightDisabled = IntUiDisabledBadgeColorsFactory.light()

    private val darkBackground = Color(0x33B4B8BF)
    private val darkContent = IntUiDarkTheme.colors.grayOrNull(10) ?: Color(0xFFB4B8BF)
    private val darkDisabled = IntUiDisabledBadgeColorsFactory.dark()

    public fun light(
        background: Brush = SolidColor(lightBackground.copy(alpha = .12f)),
        backgroundDisabled: Brush = SolidColor(lightDisabled.background),
        backgroundFocused: Brush = SolidColor(lightBackground.copy(alpha = .12f)),
        backgroundPressed: Brush = SolidColor(lightBackground.copy(alpha = .12f)),
        backgroundHovered: Brush = SolidColor(lightBackground.copy(alpha = .12f)),
        content: Color = lightContent,
        contentDisabled: Color = lightDisabled.content,
        contentFocused: Color = lightContent,
        contentPressed: Color = lightContent,
        contentHovered: Color = lightContent,
    ): BadgeColors =
        BadgeColors(
            background = background,
            backgroundDisabled = backgroundDisabled,
            backgroundFocused = backgroundFocused,
            backgroundPressed = backgroundPressed,
            backgroundHovered = backgroundHovered,
            content = content,
            contentDisabled = contentDisabled,
            contentFocused = contentFocused,
            contentPressed = contentPressed,
            contentHovered = contentHovered,
        )

    public fun dark(
        background: Brush = SolidColor(darkBackground.copy(alpha = .20f)),
        backgroundDisabled: Brush = SolidColor(darkDisabled.background),
        backgroundFocused: Brush = SolidColor(darkBackground.copy(alpha = .20f)),
        backgroundPressed: Brush = SolidColor(darkBackground.copy(alpha = .20f)),
        backgroundHovered: Brush = SolidColor(darkBackground.copy(alpha = .20f)),
        content: Color = darkContent,
        contentDisabled: Color = darkDisabled.content,
        contentFocused: Color = darkContent,
        contentPressed: Color = darkContent,
        contentHovered: Color = darkContent,
    ): BadgeColors =
        BadgeColors(
            background = background,
            backgroundDisabled = backgroundDisabled,
            backgroundFocused = backgroundFocused,
            backgroundPressed = backgroundPressed,
            backgroundHovered = backgroundHovered,
            content = content,
            contentDisabled = contentDisabled,
            contentFocused = contentFocused,
            contentPressed = contentPressed,
            contentHovered = contentHovered,
        )
}

public fun BadgeMetrics.Companion.default(
    cornerSize: CornerSize = CornerSize(100),
    padding: PaddingValues = PaddingValues(horizontal = 6.dp),
    minHeight: Dp = 16.dp,
): BadgeMetrics = BadgeMetrics(cornerSize = cornerSize, padding = padding, minHeight = minHeight)

private data class DisabledBadgeColors(val background: Color, val content: Color)

private object IntUiDisabledBadgeColorsFactory {
    private val lightBackground = Color(0x1F6C707E)
    private val lightContent = IntUiLightTheme.colors.grayOrNull(8) ?: Color(0xFFA8ADBD)

    private val darkBackground = Color(0x33B4B8BF)
    private val darkContent = IntUiDarkTheme.colors.grayOrNull(8) ?: Color(0xFF868A91)

    fun light(): DisabledBadgeColors = DisabledBadgeColors(background = lightBackground, content = lightContent)

    fun dark(): DisabledBadgeColors = DisabledBadgeColors(background = darkBackground, content = darkContent)
}
