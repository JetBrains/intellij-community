[![JetBrains incubator](https://camo.githubusercontent.com/be6f8b50b2400e8b0dc74e58dd9a68803fe6698f5f30d843a7504888879f8392/68747470733a2f2f6a622e67672f6261646765732f696e63756261746f722d706c61737469632e737667)](https://github.com/JetBrains#jetbrains-on-github) [![CI checks](https://github.com/JetBrains/jewel/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/JetBrains/jewel/actions/workflows/build.yml)

# Jewel: a Compose for Desktop theme

<img alt="Jewel logo" src="art/jewel-logo.svg" width="20%"/>

Jewel aims at recreating the IntelliJ Platform's _New UI_ Swing Look and Feel in Compose for Desktop, providing a
desktop-optimized theme and set of components.

> [!WARNING]
>
> This project is in very early development and is probably not ready to be used in production projects. You _can_, but
> you should expect APIs to change fairly often, things to move around and/or break, and all that jazz.
>
> Use at your risk!

Jewel provides stand-alone implementations of the IntelliJ Platform themes that can be used in any Compose for Desktop
application, and a Swing LaF Bridge that only works in the IntelliJ Platform (i.e., used to create IDE plugins), but
automatically mirrors the current Swing LaF into Compose for a native-looking, consistent UI.

## Project structure

The project is split in modules:

1. `buildSrc` contains the build logic, including:
    * The `jewel` and `jewel-publish` configuration plugins
    * The Theme Palette generator plugin
2. `core` contains the foundational Jewel functionality, including the components and their styling primitives
3. `int-ui` implements the standalone version of the IntelliJ New UI, which implements the
   ["Int UI" design system](https://www.figma.com/community/file/1227732692272811382/int-ui-kit), and can be used
   anywhere
4. `ide-laf-bridge` contains the Swing LaF bridge to use in IntelliJ Platform plugins (see more below)
5. `samples` contains the example apps, which showcase the available components:
    1. `standalone` is a regular CfD app, using the predefined "base" theme definitions
    2. `ide-plugin` is an IntelliJ plugin, adding some UI to the IDE, and showcasing the use of the Swing Bridge

### Int UI Standalone theme

The standalone theme can be used in any Compose for Desktop app. You use it as a normal theme, and you can customise it
to your heart's content. By default, it matches the official Int UI specs.

> [!WARNING]
> Note that Jewel **requires** the JetBrains Runtime to work correctly. Some features like font loading depend on it,
> as it has extra features and patches for UI functionalities that aren't available in other JDKs.
> We **do not support** running Jewel on any other JDK.

### The Swing Bridge

Jewel includes a crucial element for proper integration with the IDE: a bridge between the Swing components, theme
and LaF, and the Compose world.

This bridge ensures that we pick up the colours, typography, metrics, and images as defined in the current IntelliJ
theme, and apply them to the Compose components as well — at least for themes that use the
standard [IntelliJ theming](https://plugins.jetbrains.com/docs/intellij/themes-getting-started.html) mechanisms.

> [!NOTE]
> IntelliJ themes that use non-standard mechanisms (such as providing custom UI implementations for Swing components)
> are not, and will never, be supported.

If you're writing an IntelliJ Platform plugin, you should use the `SwingBridgeTheme` instead of a standalone theme.

#### Accessing icons

When you want to draw an icon from the resources, you should use a `PainterProvider`. Reading an icon from the IDE is
as easy as using the `retrieveStatefulIcon()` and `retrieveStatelessIcon()`:

```kotlin
val svgLoader = service<SwingBridgeService>().svgLoader
val painterProvider = retrieveStatelessIcon("icons/bot-toolwindow.svg", svgLoader, iconData)
```
