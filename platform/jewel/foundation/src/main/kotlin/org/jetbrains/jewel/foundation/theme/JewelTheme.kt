package org.jetbrains.jewel.foundation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import java.util.UUID
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jewel.foundation.GlobalColors
import org.jetbrains.jewel.foundation.GlobalMetrics
import org.jetbrains.jewel.foundation.InternalJewelApi
import org.jetbrains.jewel.foundation.LocalDisabledAppearanceValues
import org.jetbrains.jewel.foundation.LocalGlobalColors
import org.jetbrains.jewel.foundation.LocalGlobalMetrics

/** Entry point for accessing the current Jewel theme values from any composable via composition locals. */
public interface JewelTheme {
    /** Provides composable-accessible properties for the current theme's colors, metrics, text styles, and flags. */
    public companion object {
        /** The name of the current theme. */
        public val name: String
            @Composable @ReadOnlyComposable get() = LocalThemeName.current

        /** @see LocalThemeInstanceUuid */
        public val instanceUuid: UUID
            @Composable @ReadOnlyComposable get() = LocalThemeInstanceUuid.current

        /** The global colors for the current theme. */
        public val globalColors: GlobalColors
            @Composable @ReadOnlyComposable get() = LocalGlobalColors.current

        /** The global metrics for the current theme. */
        public val globalMetrics: GlobalMetrics
            @Composable @ReadOnlyComposable get() = LocalGlobalMetrics.current

        /** The default text style for the current theme. */
        public val defaultTextStyle: TextStyle
            @Composable @ReadOnlyComposable get() = LocalTextStyle.current

        /** The text style used in editor components. */
        public val editorTextStyle: TextStyle
            @Composable @ReadOnlyComposable get() = LocalEditorTextStyle.current

        /** The text style used in console/terminal components. */
        public val consoleTextStyle: TextStyle
            @Composable @ReadOnlyComposable get() = LocalConsoleTextStyle.current

        /** The default content (foreground) color for the current theme. */
        public val contentColor: Color
            @Composable @ReadOnlyComposable get() = LocalContentColor.current

        /** Whether the current theme is a dark theme. */
        public val isDark: Boolean
            @Composable
            @ReadOnlyComposable
            get() = LocalIsDarkTheme.current ?: error("No IsDarkTheme provided. Have you forgotten the theme?")

        /** Whether Swing compatibility mode is enabled, which disables hover and press state changes. */
        public val isSwingCompatMode: Boolean
            @Composable @ReadOnlyComposable get() = LocalSwingCompatMode.current

        /** Whether the Islands rendering mode is enabled. Always `false` in standalone. */
        public val isIslands: Boolean
            @Composable @ReadOnlyComposable get() = LocalIsIslandsTheme.current
    }
}

/**
 * Applies the given [theme] and [swingCompatMode] flag to the [content] composition tree.
 *
 * @param theme The [ThemeDefinition] describing colors, metrics, and text styles.
 * @param swingCompatMode Whether to enable Swing compatibility mode (disables hover/press state changes).
 * @param content The composable content to render under this theme.
 */
@Composable
public fun JewelTheme(theme: ThemeDefinition, swingCompatMode: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSwingCompatMode provides swingCompatMode) { JewelTheme(theme, content) }
}

/**
 * Applies the given [theme] to the [content] composition tree, providing all theme composition locals.
 *
 * @param theme The [ThemeDefinition] describing colors, metrics, and text styles.
 * @param content The composable content to render under this theme.
 */
@Composable
public fun JewelTheme(theme: ThemeDefinition, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalThemeName provides theme.name,
        LocalThemeInstanceUuid provides remember(theme) { UUID.randomUUID() },
        LocalIsDarkTheme provides theme.isDark,
        LocalContentColor provides theme.contentColor,
        LocalTextStyle provides theme.defaultTextStyle,
        LocalEditorTextStyle provides theme.editorTextStyle,
        LocalConsoleTextStyle provides theme.consoleTextStyle,
        LocalGlobalColors provides theme.globalColors,
        LocalGlobalMetrics provides theme.globalMetrics,
        LocalDisabledAppearanceValues provides theme.disabledAppearanceValues,
        LocalIsIslandsTheme provides theme.isIslands,
        LocalForcedIconVariant provides ForcedIconVariant.of(theme.isDark),
        content = content,
    )
}

/** Composition local providing the name of the current theme. */
public val LocalThemeName: ProvidableCompositionLocal<String> = staticCompositionLocalOf {
    error("No ThemeName provided")
}

/**
 * A [UUID] that's unique to any instance of the [ThemeDefinition], and can be used as a signal to invalidate any
 * remembered value that needs to be refreshed when the theme changes. Note that this can change even if the rest of the
 * theme does not change, so you should only use this when you can't key the remembers to anything else in the theme
 * (e.g., you need to re-read values from the environment whose changes can't be listened to).
 *
 * The provided value should be random, and must change every time the theme definition is changed, and be different
 * from all previous values.
 */
public val LocalThemeInstanceUuid: ProvidableCompositionLocal<UUID> = staticCompositionLocalOf {
    error("No ThemeInstanceUuid provided. Have you forgotten the theme?")
}

/** Composition local providing the default content (foreground) color for the current theme. */
public val LocalContentColor: ProvidableCompositionLocal<Color> = staticCompositionLocalOf {
    error("No ContentColor provided. Have you forgotten the theme?")
}

/** Whether the current theme is dark, or `null` outside a theme. [JewelTheme.isDark] fails outside a theme. */
internal val LocalIsDarkTheme: ProvidableCompositionLocal<Boolean?> = staticCompositionLocalOf { null }

/** The artwork that an icon from an icon descriptor uses, see [LocalForcedIconVariant]. */
@InternalJewelApi
@ApiStatus.Internal
public enum class ForcedIconVariant(public val isDark: Boolean) {
    Light(isDark = false),
    Dark(isDark = true);

    public companion object {
        /** The variant for a theme that is dark if [isDark] is `true`. */
        public fun of(isDark: Boolean): ForcedIconVariant = if (isDark) Dark else Light
    }
}

/**
 * The artwork that an icon from an icon descriptor uses, or `null` when the icon follows the IDE theme. [JewelTheme]
 * sets it from the dark state of its theme. `SwingBridgeTheme` resets it to `null`, so the icons follow the IDE theme
 * as the Swing icons around them do. [OverrideDarkMode] sets it when it changes the dark state.
 */
@InternalJewelApi
@get:ApiStatus.Internal
public val LocalForcedIconVariant: ProvidableCompositionLocal<ForcedIconVariant?> = staticCompositionLocalOf { null }

internal val LocalSwingCompatMode: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf {
    // By default, Swing compat is not enabled
    false
}

/** Composition local providing the color palette for the current theme. */
public val LocalColorPalette: ProvidableCompositionLocal<ThemeColorPalette> = staticCompositionLocalOf {
    ThemeColorPalette.Empty
}

/** Composition local providing icon data (mappings and overrides) for the current theme. */
public val LocalIconData: ProvidableCompositionLocal<ThemeIconData> = staticCompositionLocalOf { ThemeIconData.Empty }

/** Composition local providing the default text style for the current theme. */
public val LocalTextStyle: ProvidableCompositionLocal<TextStyle> = staticCompositionLocalOf {
    error("No TextStyle provided. Have you forgotten the theme?")
}

/** Composition local providing the text style used in editor components. */
public val LocalEditorTextStyle: ProvidableCompositionLocal<TextStyle> = staticCompositionLocalOf {
    error("No EditorTextStyle provided. Have you forgotten the theme?")
}

/** Composition local providing the text style used in console/terminal components. */
public val LocalConsoleTextStyle: ProvidableCompositionLocal<TextStyle> = staticCompositionLocalOf {
    error("No ConsoleTextStyle provided. Have you forgotten the theme?")
}

/** Composition local providing whether the Islands rendering mode is enabled. */
internal val LocalIsIslandsTheme: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }

/**
 * Overrides the [isDark] value for the [content]. It is used to inject a different dark mode style in a sub-tree.
 *
 * Note: this does _not_ change the theme. If you want to change the theme, you need to do it by yourself. For example,
 * in standalone:
 * ```kotlin
 * IntUiTheme(isDark = false) {
 *   Text("I am light")
 *
 *   IntUiTheme(isDark = true) {
 *     Text("I am dark")
 *   }
 * }
 * ```
 */
@Composable
public fun OverrideDarkMode(isDark: Boolean, content: @Composable () -> Unit) {
    // an icon that follows the IDE theme keeps following it when the dark state stays the same, for example in a popup
    val followsIdeTheme = LocalForcedIconVariant.current == null && LocalIsDarkTheme.current == isDark
    val forcedIconVariant = if (followsIdeTheme) null else ForcedIconVariant.of(isDark)
    CompositionLocalProvider(
        LocalIsDarkTheme provides isDark,
        LocalForcedIconVariant provides forcedIconVariant,
        content = content,
    )
}
