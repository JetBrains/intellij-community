# Standalone Vs Swing Bridge

## Standalone Compose Desktop

Use for regular Compose Desktop applications.

### Dependencies

```kotlin
dependencies {
    implementation("org.jetbrains.jewel:jewel-int-ui-standalone:[jewelVersion]")

    // Optional, only for custom decorated windows
    implementation("org.jetbrains.jewel:jewel-int-ui-decorated-window:[jewelVersion]")

    // Keep Material out when using Jewel
    implementation(compose.desktop.currentOs) {
        exclude(group = "org.jetbrains.compose.material")
    }
}
```

### Theme wrapper

```kotlin
IntUiTheme(isDark = false) {
    // Jewel UI
}
```

### Advanced standalone wrapper

```kotlin
val textStyle = JewelTheme.createDefaultTextStyle()
val editorStyle = JewelTheme.createEditorTextStyle()
val themeDefinition = JewelTheme.lightThemeDefinition(
    defaultTextStyle = textStyle,
    editorTextStyle = editorStyle,
)

IntUiTheme(
    theme = themeDefinition,
    styling = ComponentStyling.default(),
    swingCompatMode = false,
) {
    // Jewel UI
}
```

### Runtime requirement

Jewel requires JetBrains Runtime for full functionality (including font-related behavior).

## IntelliJ Platform Plugin

Use for Compose UI embedded in an IntelliJ plugin.

### Dependencies

Use IntelliJ bundled modules (do not add standalone Jewel artifacts in plugin context):

```kotlin
dependencies {
    intellijPlatform {
        bundledModule("intellij.platform.jewel.foundation")
        bundledModule("intellij.platform.jewel.ui")
        bundledModule("intellij.platform.jewel.ideLafBridge")
    }
}
```

Add markdown-related bundled modules only when needed.

### Theme wrapper

```kotlin
SwingBridgeTheme {
    // Jewel UI
}
```

This bridges IntelliJ Swing LaF values (colors, typography, metrics, and icon behavior) into Compose/Jewel.

## Canonical Source Links

- [README standalone setup](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/README.md)
- [SwingBridgeTheme implementation](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/ide-laf-bridge/src/main/kotlin/org/jetbrains/jewel/bridge/theme/SwingBridgeTheme.kt)
