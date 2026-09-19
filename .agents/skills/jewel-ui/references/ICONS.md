# Icon Loading Patterns

## Always prefer `IconKey`

Use Jewel key-based APIs:

```kotlin
Icon(key = MyIcons.Settings, contentDescription = "Settings")
Image(iconKey = MyIcons.Banner, contentDescription = null)
```

Prefer key-based APIs over raw `painterResource` for Jewel UIs: keys integrate with Jewel's icon patching (dark mode, new UI variants, density). Raw `painterResource` bypasses that pipeline.

## Choose the right key type

1. `PathIconKey(path, iconClass)`: use when old/new UI share the same path.
2. `IntelliJIconKey(oldUiPath, newUiPath, iconClass)`: use when path differs between old/new UI.
3. Custom `IconKey`: use only for specialized path resolution.

## Define your icon key holder

```kotlin
object MyIcons {
    val Settings = PathIconKey("icons/settings.svg", MyIcons::class.java)
    val Welcome = IntelliJIconKey(
        oldUiPath = "icons/welcome.svg",
        newUiPath = "icons/expui/welcome.svg",
        iconClass = MyIcons::class.java,
    )
}
```

Keep icon resources on classpath under the same paths used in keys.

## Use IntelliJ platform icons

Use `AllIconsKeys` for platform icons.

In IntelliJ plugins:
- Icons are already on classpath.

In standalone apps:
- Add `com.jetbrains.intellij.platform:icons:[ijpVersion]`.
- Add IntelliJ repository matching chosen platform build.

## Use `PainterHint` intentionally

Apply hints for stateful and variant behavior:

```kotlin
Icon(
    key = MyIcons.Toggle,
    contentDescription = "Toggle",
    Selected(state),
    Stateful(state),
)
```

Use hints only when needed; default runtime patching already handles common dark/light, new-ui, and density behavior.

## Common pitfalls

1. Using wrong `iconClass` so resources are not found.
2. Using path strings directly in UI instead of key holders.
3. Assuming standalone has IntelliJ platform icons without explicit dependency.
4. Forgetting content descriptions for meaningful icons.

## Canonical Source Links

- [README icons section](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/README.md)
- [IconKey implementations (`PathIconKey`, `IntelliJIconKey`)](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/ui/src/main/kotlin/org/jetbrains/jewel/ui/icon/IconKey.kt)
- [Icon composable overloads](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/ui/src/main/kotlin/org/jetbrains/jewel/ui/component/Icon.kt)
- [Image composable for `IconKey`](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/ui/src/main/kotlin/org/jetbrains/jewel/ui/component/Image.kt)
- [Sample icon holder (`ShowcaseIcons`)](https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/samples/showcase/src/main/kotlin/org/jetbrains/jewel/samples/showcase/ShowcaseIcons.kt)
