# Theming Patterns

## Minimal standalone theme

```kotlin
IntUiTheme(isDark = false) {
    App()
}
```

Use this for quick prototypes and small apps.

## Standalone theme with explicit `ThemeDefinition`

```kotlin
val textStyle = JewelTheme.createDefaultTextStyle()
val editorStyle = JewelTheme.createEditorTextStyle()

val themeDefinition = if (isDark) {
    JewelTheme.darkThemeDefinition(
        defaultTextStyle = textStyle,
        editorTextStyle = editorStyle,
    )
} else {
    JewelTheme.lightThemeDefinition(
        defaultTextStyle = textStyle,
        editorTextStyle = editorStyle,
    )
}

IntUiTheme(
    theme = themeDefinition,
    styling = ComponentStyling.default(),
    swingCompatMode = false,
) {
    App()
}
```

Use this when text styles, palettes, icon data, or disabled appearance values need customization.

## IntelliJ plugin theme

```kotlin
SwingBridgeTheme {
    App()
}
```

Use this in plugin UI so Compose follows current IntelliJ look-and-feel and scale.

## Decorated window styling (standalone)

Customizing a title bar requires configuring `titleBarStyle` through `ComponentStyling.default().decoratedWindow(...)` — the `IntUiTheme(isDark = ...)` quick-start wrapper does not give you this hook. When the user asks for a custom title bar, reach for this form.

```kotlin
IntUiTheme(
    theme = themeDefinition,
    styling = ComponentStyling.default().decoratedWindow(
        titleBarStyle = TitleBarStyle.light()
    ),
) {
    DecoratedWindow(onCloseRequest = ::exitApplication) {
        App()
    }
}
```

Use only in standalone/JBR scenarios that need custom title bars. For dark theme, swap `TitleBarStyle.light()` for `TitleBarStyle.dark()`.

## Customization guidance

1. Keep color and metrics logic in theme definitions instead of hardcoding in components.
2. Keep component look changes in `ComponentStyling` composition.
3. Use `swingCompatMode = true` only when compatibility behavior is explicitly required.
4. Prefer existing style builders from `int-ui-standalone` and `ide-laf-bridge` modules before creating ad-hoc styles.

## Canonical Source Links

- [IntUiTheme overloads and locals](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/int-ui/int-ui-standalone/src/main/kotlin/org/jetbrains/jewel/intui/standalone/theme/IntUiTheme.kt)
- [Standalone sample theme composition](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/standalone/src/main/kotlin/org/jetbrains/jewel/samples/standalone/Main.kt)
- [Swing bridge theme entrypoint](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/ide-laf-bridge/src/main/kotlin/org/jetbrains/jewel/bridge/theme/SwingBridgeTheme.kt)
