# Theming Colors

Use Jewel color APIs in this order: semantic colors first, palette colors second, hardcoded colors last.

## Core Color APIs

1. `JewelTheme.globalColors`: semantic app-wide colors.
2. `JewelTheme.colorPalette`: indexed color ramps (`gray`, `blue`, `green`, `red`, `yellow`, `orange`, `purple`, `teal`).
3. Component style colors (for component-specific states) via `JewelTheme.<component>Style`.

## Semantic Colors (`globalColors`)

Use these first:

1. `globalColors.text`: `normal`, `selected`, `disabled`, `disabledSelected`, `info`, `error`, `warning`.
2. `globalColors.borders`: `normal`, `focused`, `disabled`.
3. `globalColors.outlines`: `focused`, `focusedWarning`, `focusedError`, `warning`, `error`.
4. `globalColors.panelBackground` and `globalColors.toolwindowBackground`.

Example:

```kotlin
val colors = JewelTheme.globalColors

Column(Modifier.background(colors.panelBackground)) {
    Text("Settings", color = colors.text.normal)
    Text("Validation error", color = colors.text.error)
}
```

## Palette Colors (`colorPalette`)

Use palette ramps for accent/illustrative usage, not for primary semantic meaning.

Important:

1. Palette indices are 1-based.
2. Look-and-feels can have partial palettes.
3. Prefer `*OrNull()` accessors over direct indexed access.

Example:

```kotlin
val palette = JewelTheme.colorPalette
val accent = palette.blueOrNull(6) ?: JewelTheme.globalColors.outlines.focused

Spacer(Modifier.fillMaxWidth().height(1.dp).background(accent))
```

## Overriding Color Systems

Use `GlobalColors.light(...)` / `GlobalColors.dark(...)` and pass into theme definitions.

```kotlin
val customGlobalColors = GlobalColors.light(
    text = TextColors.light(error = Color(0xFFD32F2F)),
    outlines = OutlineColors.light(error = Color(0xFFD32F2F), focusedError = Color(0xFFFF5F56)),
)

val theme = JewelTheme.lightThemeDefinition(colors = customGlobalColors)

IntUiTheme(theme = theme, styling = ComponentStyling.default()) {
    App()
}
```

## Guidance

1. Use semantic tokens (`globalColors`) for meaning (success/warning/error/focus).
2. Use palette ramps (`colorPalette`) for decorative accents and gradients.
3. Keep state colors coherent: text, border, and outline should communicate the same state.
4. Avoid random hex values in component bodies; centralize overrides in theme creation.
5. Use component style objects for component-specific color behavior before overriding global colors.

## Canonical Source Links

- [GlobalColors, TextColors, BorderColors, OutlineColors](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/foundation/src/main/kotlin/org/jetbrains/jewel/foundation/GlobalColors.kt)
- [Standalone light/dark global color defaults](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/int-ui/int-ui-standalone/src/main/kotlin/org/jetbrains/jewel/intui/standalone/theme/IntUiGlobalColors.kt)
- [ThemeColorPalette and safe accessors](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/foundation/src/main/kotlin/org/jetbrains/jewel/foundation/theme/ThemeColorPalette.kt)
- [JewelTheme color palette access](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/ui/src/main/kotlin/org/jetbrains/jewel/ui/theme/JewelTheme.kt)
- [Theme definition APIs (`lightThemeDefinition` / `darkThemeDefinition`)](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/int-ui/int-ui-standalone/src/main/kotlin/org/jetbrains/jewel/intui/standalone/theme/IntUiTheme.kt)
