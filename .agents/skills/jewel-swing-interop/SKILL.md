---
name: jewel-swing-interop
description: Integrate Jewel Compose UI with Swing in IntelliJ Platform plugins or desktop apps. Use when requests involve ComposePanel/JComponent hosting, ToolWindow tabs, SwingBridgeTheme, enableNewSwingCompositing, LocalComponent, popup rendering, clipboard/action-system bridges, or AWT/Compose conversion helpers.
---

# Jewel Swing Interop

Embed Compose/Jewel into Swing with bridge primitives from `ide-laf-bridge` and `foundation`.

## Quick Snippets

Tool window tab:

```kotlin
toolWindow.addComposeTab(tabDisplayName = "My Tab") {
    SwingBridgeTheme {
        MyToolWindowContent()
    }
}
```

Generic Swing host:

```kotlin
val panel = JewelComposePanel {
    MyPluginComposable()
}
```

Manual compositing enablement (non-toolwindow entrypoint):

```kotlin
enableNewSwingCompositing()
val panel = JewelComposePanel { MyPluginComposable() }
```

## Use The Highest-Level API First

Pick the simplest valid entrypoint:

1. Tool window tab: `ToolWindow.addComposeTab(...)`.
2. Generic Swing host: `compose(...)` / `JewelComposePanel(...)`.
3. Custom theming flow: `composeWithoutTheme(...)` / `JewelComposeNoThemePanel(...)`.

Default to themed entrypoints; use no-theme variants only when theme wrapping is intentionally external.

## Enable New Swing Compositing Early

Call `enableNewSwingCompositing()` before attaching Compose content when needed.

Notes:
1. `ToolWindow.addComposeTab(...)` already does this internally.
2. Function is idempotent and safe at multiple entry points.
3. This improves z-order/resizing behavior but can affect performance with infinitely repeating animations.
4. Treat `enableNewSwingCompositing()` and no-theme entrypoints as advanced/experimental paths.

## Apply Bridge Theme In Plugin Context

Use `SwingBridgeTheme` for IntelliJ plugin UI:

1. Pulls Swing LaF colors/metrics/typography into Compose.
2. Provides bridge locals (icon/new-ui behavior, clipboard, density scaling, shortcuts, URI handling).

Do not use standalone `IntUiTheme` for plugin-hosted Swing integration.

## Bridge Compose To Swing/AWT Safely

For bidirectional operations:

1. Use `LocalComponent` to obtain host Swing component when APIs require `Component`.
2. Use bridge utilities for color conversion and LaF key lookup when styling custom interop elements.
3. Keep file chooser and dialog operations delegated to Swing (`JFileChooser`, etc.) from Compose callbacks.

## Version Discipline

Treat this skill as Jewel-version scoped.

1. Ask for target IntelliJ Platform baseline and Jewel API version.
2. Validate behavior against [Jewel release notes](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/RELEASE%20NOTES.md) when APIs differ by platform generation.
3. Prefer high-level bridge entrypoints that are stable in target versions.
4. For shipped skill updates, refresh reference links to the corresponding release tag.

## Interop Checklist

Before finishing:

1. Confirm compositing flag is set at proper entrypoints.
2. Confirm theme wrapper is `SwingBridgeTheme` (unless deliberately no-theme).
3. Confirm host component access uses `LocalComponent`.
4. Confirm toolwindow integrations use `addComposeTab` when applicable.
5. Confirm interop code avoids creating extra unmanaged `ComposePanel` wrappers.

## References

Read these source files when implementing:

1. [ToolWindowExtensions.kt](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/ide-laf-bridge/src/main/kotlin/org/jetbrains/jewel/bridge/ToolWindowExtensions.kt#L18-L44)
2. [JewelComposePanelWrapper.kt](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/ide-laf-bridge/src/main/kotlin/org/jetbrains/jewel/bridge/JewelComposePanelWrapper.kt#L36-L199)
3. [SwingBridgeTheme.kt](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/ide-laf-bridge/src/main/kotlin/org/jetbrains/jewel/bridge/theme/SwingBridgeTheme.kt#L39-L61)
4. [Compatibility.kt (`enableNewSwingCompositing`)](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/foundation/src/main/kotlin/org/jetbrains/jewel/foundation/Compatibility.kt#L5-L17)
5. [README Swing interoperability section](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/README.md#L424-L439)
