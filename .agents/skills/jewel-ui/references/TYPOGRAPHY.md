# Typography

Use `JewelTheme.typography` for UI text hierarchy and `JewelTheme.editorTextStyle` / `consoleTextStyle` for code-like text.

## Available Styles

From `JewelTheme.typography`:

1. `h0TextStyle`
2. `h1TextStyle`
3. `h2TextStyle`
4. `h3TextStyle`
5. `h4TextStyle`
6. `regular`
7. `medium`
8. `small`
9. `labelTextStyle`
10. `editorTextStyle`
11. `consoleTextStyle`

## When To Use Which

1. `h0` / `h1`: screen-level and primary section titles.
2. `h2` / `h3` / `h4`: nested section headers.
3. `regular`: default body text.
4. `medium`: secondary emphasis (metadata, inline hints, compact labels).
5. `small`: dense UI footnotes or helper text.
6. `labelTextStyle`: form labels and control text.
7. `editorTextStyle`: text/code editors, text areas with code-like content.
8. `consoleTextStyle`: console/log output.

## Usage Example

```kotlin
Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text("Project Settings", style = JewelTheme.typography.h1TextStyle)
    Text("General", style = JewelTheme.typography.h3TextStyle)
    Text("Choose your defaults", style = JewelTheme.typography.regular)
    Text("Requires restart", style = JewelTheme.typography.small)
}
```

## Customizing Theme Text Styles

Customize at theme-definition level, not per-component by default.

```kotlin
val defaultText = JewelTheme.createDefaultTextStyle(fontSize = 14.sp)
val editorText = JewelTheme.createEditorTextStyle(fontSize = 13.sp)

val themeDefinition = JewelTheme.darkThemeDefinition(
    defaultTextStyle = defaultText,
    editorTextStyle = editorText,
)

IntUiTheme(theme = themeDefinition, styling = ComponentStyling.default()) {
    App()
}
```

## Guidance

1. Keep one consistent text hierarchy per screen.
2. Avoid mixing arbitrary custom `TextStyle`s in component bodies.
3. Use component `textStyle` parameters only when requirements differ from app defaults.
4. Use editor/console styles for monospaced or code-oriented content.
5. Prefer typography tokens over ad-hoc `fontSize` and `fontWeight` in-place.

## Canonical Source Links

- [Typography interface and `JewelTheme.typography`](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/ui/src/main/kotlin/org/jetbrains/jewel/ui/Typography.kt)
- [IntUiTypography default mappings](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/int-ui/int-ui-standalone/src/main/kotlin/org/jetbrains/jewel/intui/standalone/IntUiTypography.kt)
- [Default/editor text style creation APIs](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/int-ui/int-ui-standalone/src/main/kotlin/org/jetbrains/jewel/intui/standalone/theme/TextStyles.kt)
- [Sample heading usage in layout](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/views/ComponentsView.kt)
