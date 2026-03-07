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
    default: BadgeStyle = BadgeStyle.Default.light(),
    new: BadgeStyle = BadgeStyle.New.light(),
    beta: BadgeStyle = BadgeStyle.Beta.light(),
    free: BadgeStyle = BadgeStyle.Free.light(),
    trial: BadgeStyle = BadgeStyle.Trial.light(),
    information: BadgeStyle = BadgeStyle.Information.light(),
): BadgeStyles =
    BadgeStyles(default = default, new = new, beta = beta, free = free, trial = trial, information = information)

public fun BadgeStyles.Companion.dark(
    default: BadgeStyle = BadgeStyle.Default.dark(),
    new: BadgeStyle = BadgeStyle.New.dark(),
    beta: BadgeStyle = BadgeStyle.Beta.dark(),
    free: BadgeStyle = BadgeStyle.Free.dark(),
    trial: BadgeStyle = BadgeStyle.Trial.dark(),
    information: BadgeStyle = BadgeStyle.Information.dark(),
): BadgeStyles =
    BadgeStyles(default = default, new = new, beta = beta, free = free, trial = trial, information = information)

// region Default Badge

public val BadgeStyle.Companion.Default: IntUiDefaultBadgeStyleFactory
    get() = IntUiDefaultBadgeStyleFactory

public object IntUiDefaultBadgeStyleFactory {
    public fun light(
        colors: BadgeColors = BadgeColors.Default.light(),
        metrics: BadgeMetrics = BadgeMetrics.default(),
    ): BadgeStyle = BadgeStyle(colors = colors, metrics = metrics)

    public fun dark(
        colors: BadgeColors = BadgeColors.Default.dark(),
        metrics: BadgeMetrics = BadgeMetrics.default(),
    ): BadgeStyle = BadgeStyle(colors = colors, metrics = metrics)
}

public val BadgeColors.Companion.Default: IntUiDefaultBadgeColorsFactory
    get() = IntUiDefaultBadgeColorsFactory

public object IntUiDefaultBadgeColorsFactory {
    private val lightBackground = IntUiLightTheme.colors.blueOrNull(4) ?: Color(0xFF3574F0)
    private val lightContent = IntUiLightTheme.colors.blueOrNull(1) ?: Color(0xFF2E55A3)
    private val lightDisabled = IntUiDisabledBadgeColorsFactory.light()

    private val darkBackground = IntUiDarkTheme.colors.blueOrNull(3) ?: Color(0xFF35538F)
    private val darkContent = IntUiDarkTheme.colors.blueOrNull(12) ?: Color(0xFFB5CEFF)
    private val darkDisabled = IntUiDisabledBadgeColorsFactory.dark()

    public fun light(
        background: Brush = SolidColor(lightBackground.copy(alpha = .16f)),
        backgroundDisabled: Brush = SolidColor(lightDisabled.background),
        backgroundFocused: Brush = SolidColor(lightBackground.copy(alpha = .16f)),
        backgroundPressed: Brush = SolidColor(lightBackground.copy(alpha = .16f)),
        backgroundHovered: Brush = SolidColor(lightBackground.copy(alpha = .16f)),
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
        background: Brush = SolidColor(darkBackground.copy(alpha = .80f)),
        backgroundDisabled: Brush = SolidColor(darkDisabled.background),
        backgroundFocused: Brush = SolidColor(darkBackground.copy(alpha = .80f)),
        backgroundPressed: Brush = SolidColor(darkBackground.copy(alpha = .80f)),
        backgroundHovered: Brush = SolidColor(darkBackground.copy(alpha = .80f)),
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

// endregion

// region New Badge

public val BadgeStyle.Companion.New: IntUiNewBadgeStyleFactory
    get() = IntUiNewBadgeStyleFactory

public object IntUiNewBadgeStyleFactory {
    public fun light(
        colors: BadgeColors = BadgeColors.New.light(),
        metrics: BadgeMetrics = BadgeMetrics.default(),
    ): BadgeStyle = BadgeStyle(colors = colors, metrics = metrics)

    public fun dark(
        colors: BadgeColors = BadgeColors.New.dark(),
        metrics: BadgeMetrics = BadgeMetrics.default(),
    ): BadgeStyle = BadgeStyle(colors = colors, metrics = metrics)
}

public val BadgeColors.Companion.New: IntUiNewBadgeColorsFactory
    get() = IntUiNewBadgeColorsFactory

public object IntUiNewBadgeColorsFactory {
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

// endregion

// region Beta Badge

public val BadgeStyle.Companion.Beta: IntUiBetaBadgeStyleFactory
    get() = IntUiBetaBadgeStyleFactory

public object IntUiBetaBadgeStyleFactory {
    public fun light(
        colors: BadgeColors = BadgeColors.Beta.light(),
        metrics: BadgeMetrics = BadgeMetrics.default(),
    ): BadgeStyle = BadgeStyle(colors = colors, metrics = metrics)

    public fun dark(
        colors: BadgeColors = BadgeColors.Beta.dark(),
        metrics: BadgeMetrics = BadgeMetrics.default(),
    ): BadgeStyle = BadgeStyle(colors = colors, metrics = metrics)
}

public val BadgeColors.Companion.Beta: IntUiBetaBadgeColorsFactory
    get() = IntUiBetaBadgeColorsFactory

public object IntUiBetaBadgeColorsFactory {
    private val lightBackground = IntUiLightTheme.colors.purpleOrNull(4) ?: Color(0xFF834DF0)
    private val lightContent = IntUiLightTheme.colors.purpleOrNull(1) ?: Color(0xFF55339C)
    private val lightDisabled = IntUiDisabledBadgeColorsFactory.light()

    private val darkBackground = IntUiDarkTheme.colors.purpleOrNull(5) ?: Color(0xFF6C469C)
    private val darkContent = IntUiDarkTheme.colors.purpleOrNull(12) ?: Color(0xFFE4CEFF)
    private val darkDisabled = IntUiDisabledBadgeColorsFactory.dark()

    public fun light(
        background: Brush = SolidColor(lightBackground.copy(alpha = .16f)),
        backgroundDisabled: Brush = SolidColor(lightDisabled.background),
        backgroundFocused: Brush = SolidColor(lightBackground.copy(alpha = .16f)),
        backgroundPressed: Brush = SolidColor(lightBackground.copy(alpha = .16f)),
        backgroundHovered: Brush = SolidColor(lightBackground.copy(alpha = .16f)),
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
        background: Brush = SolidColor(darkBackground.copy(alpha = .80f)),
        backgroundDisabled: Brush = SolidColor(darkDisabled.background),
        backgroundFocused: Brush = SolidColor(darkBackground.copy(alpha = .80f)),
        backgroundPressed: Brush = SolidColor(darkBackground.copy(alpha = .80f)),
        backgroundHovered: Brush = SolidColor(darkBackground.copy(alpha = .80f)),
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

// endregion

// region Free Badge

public val BadgeStyle.Companion.Free: IntUiFreeBadgeStyleFactory
    get() = IntUiFreeBadgeStyleFactory

public object IntUiFreeBadgeStyleFactory {
    public fun light(
        colors: BadgeColors = BadgeColors.Free.light(),
        metrics: BadgeMetrics = BadgeMetrics.default(),
    ): BadgeStyle = BadgeStyle(colors = colors, metrics = metrics)

    public fun dark(
        colors: BadgeColors = BadgeColors.Free.dark(),
        metrics: BadgeMetrics = BadgeMetrics.default(),
    ): BadgeStyle = BadgeStyle(colors = colors, metrics = metrics)
}

public val BadgeColors.Companion.Free: IntUiFreeBadgeColorsFactory
    get() = IntUiFreeBadgeColorsFactory

public object IntUiFreeBadgeColorsFactory {
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

// endregion

// region Trial Badge

public val BadgeStyle.Companion.Trial: IntUiTrialBadgeStyleFactory
    get() = IntUiTrialBadgeStyleFactory

public object IntUiTrialBadgeStyleFactory {
    public fun light(
        colors: BadgeColors = BadgeColors.Trial.light(),
        metrics: BadgeMetrics = BadgeMetrics.default(),
    ): BadgeStyle = BadgeStyle(colors = colors, metrics = metrics)

    public fun dark(
        colors: BadgeColors = BadgeColors.Trial.dark(),
        metrics: BadgeMetrics = BadgeMetrics.default(),
    ): BadgeStyle = BadgeStyle(colors = colors, metrics = metrics)
}

public val BadgeColors.Companion.Trial: IntUiTrialBadgeColorsFactory
    get() = IntUiTrialBadgeColorsFactory

public object IntUiTrialBadgeColorsFactory {
    private val lightBackground = IntUiLightTheme.colors.greenOrNull(4) ?: Color(0xFF208A3C)
    private val lightContent = IntUiLightTheme.colors.greenOrNull(1) ?: Color(0xFF1E6B33)
    private val lightDisabled = IntUiDisabledBadgeColorsFactory.light()

    private val darkBackground = IntUiDarkTheme.colors.greenOrNull(4) ?: Color(0xFF436946)
    private val darkContent = IntUiDarkTheme.colors.greenOrNull(12) ?: Color(0xFFD4FAD7)
    private val darkDisabled = IntUiDisabledBadgeColorsFactory.dark()

    public fun light(
        background: Brush = SolidColor(lightBackground.copy(alpha = .16f)),
        backgroundDisabled: Brush = SolidColor(lightDisabled.background),
        backgroundFocused: Brush = SolidColor(lightBackground.copy(alpha = .16f)),
        backgroundPressed: Brush = SolidColor(lightBackground.copy(alpha = .16f)),
        backgroundHovered: Brush = SolidColor(lightBackground.copy(alpha = .16f)),
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
        background: Brush = SolidColor(darkBackground.copy(alpha = .80f)),
        backgroundDisabled: Brush = SolidColor(darkDisabled.background),
        backgroundFocused: Brush = SolidColor(darkBackground.copy(alpha = .80f)),
        backgroundPressed: Brush = SolidColor(darkBackground.copy(alpha = .80f)),
        backgroundHovered: Brush = SolidColor(darkBackground.copy(alpha = .80f)),
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

// endregion

// region Information Badge

public val BadgeStyle.Companion.Information: IntUiInformationBadgeStyleFactory
    get() = IntUiInformationBadgeStyleFactory

public object IntUiInformationBadgeStyleFactory {
    public fun light(
        colors: BadgeColors = BadgeColors.Information.light(),
        metrics: BadgeMetrics = BadgeMetrics.default(),
    ): BadgeStyle = BadgeStyle(colors = colors, metrics = metrics)

    public fun dark(
        colors: BadgeColors = BadgeColors.Information.dark(),
        metrics: BadgeMetrics = BadgeMetrics.default(),
    ): BadgeStyle = BadgeStyle(colors = colors, metrics = metrics)
}

public val BadgeColors.Companion.Information: IntUiInformationBadgeColorsFactory
    get() = IntUiInformationBadgeColorsFactory

public object IntUiInformationBadgeColorsFactory {
    private val lightBackground = IntUiLightTheme.colors.grayOrNull(6) ?: Color(0xFF6C707E)
    private val lightContent = IntUiLightTheme.colors.grayOrNull(6) ?: Color(0xFF6C707E)
    private val lightDisabled = IntUiDisabledBadgeColorsFactory.light()

    private val darkBackground = IntUiDarkTheme.colors.grayOrNull(10) ?: Color(0xFFB4B8BF)
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

// endregion

public fun BadgeMetrics.Companion.default(
    cornerSize: CornerSize = CornerSize(100),
    padding: PaddingValues = PaddingValues(horizontal = 6.dp),
    height: Dp = 16.dp,
): BadgeMetrics = BadgeMetrics(cornerSize = cornerSize, padding = padding, height = height)

private data class DisabledBadgeColors(val background: Color, val content: Color)

private object IntUiDisabledBadgeColorsFactory {
    private val lightBackground = IntUiLightTheme.colors.grayOrNull(6) ?: Color(0xFF6C707E)
    private val lightContent = IntUiLightTheme.colors.grayOrNull(8) ?: Color(0xFFA8ADBD)

    private val darkBackground = IntUiDarkTheme.colors.grayOrNull(10) ?: Color(0xFFB4B8BF)
    private val darkContent = IntUiDarkTheme.colors.grayOrNull(8) ?: Color(0xFF868A91)

    fun light(): DisabledBadgeColors =
        DisabledBadgeColors(background = lightBackground.copy(alpha = .12f), content = lightContent)

    fun dark(): DisabledBadgeColors =
        DisabledBadgeColors(background = darkBackground.copy(alpha = .20f), content = darkContent)
}
