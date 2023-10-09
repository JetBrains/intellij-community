[![JetBrains incubator](https://img.shields.io/badge/JetBrains-incubator-yellow)](https://github.com/JetBrains#jetbrains-on-github) [![CI checks](https://img.shields.io/github/actions/workflow/status/JetBrains/jewel/build.yml?logo=github)](https://github.com/JetBrains/jewel/actions/workflows/build.yml) [![Licensed under Apache 2.0](https://img.shields.io/github/license/JetBrains/jewel)](https://github.com/JetBrains/jewel/blob/main/LICENSE) [![Latest release](https://img.shields.io/github/v/release/JetBrains/jewel?include_prereleases&label=Latest%20Release&logo=github)](https://github.com/JetBrains/jewel/releases/latest)


# Jewel: a Compose for Desktop theme

<img alt="Jewel logo" src="art/jewel-logo.svg" width="20%"/>

Jewel aims at recreating the IntelliJ Platform's _New UI_ Swing Look and Feel in Compose for Desktop, providing a
desktop-optimized theme and set of components.

> [!WARNING]
>
> This project is in active development, and caution is advised when considering it for production uses. You _can_,
> but you should expect APIs to change often, things to move around and/or break, and all that jazz. Binary
> compatibility is not currently guaranteed across releases, but it is an eventual aim for 1.0, if it is possible.
>
> Use at your own risk! (but have fun if you do!)

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

### Swing interoperability

As this is Compose for Desktop, you get a good degree of interoperability with Swing. To avoid glitches and z-order
issues, you should enable the
[experimental Swing rendering pipeline](https://blog.jetbrains.com/kotlin/2023/08/compose-multiplatform-1-5-0-release/#enhanced-swing-interop)
before you initialize Compose content.

The `ToolWindow.addComposeTab()` extension function provided by the `ide-laf-bridge` module will take care of that for
you, but if you want to also enable it in other scenarios and in standalone applications, you can call the
`enableNewSwingCompositing()` function in your Compose entry points (that is, right before creating a `ComposePanel`).

> [!NOTE]
> The new Swing rendering pipeline is experimental and may have performance repercussions when using infinitely
> repeating animations. This is a known issue by the Compose Multiplatform team, that requires changes in the Java
> runtime to fix. Once the required changes are made in the JetBrains Runtime, we'll remove this notice.

## Need help?

You can find help on the [`#jewel`](https://app.slack.com/client/T09229ZC6/C05T8U2C31T) channel on the Kotlin Slack.
If you don't already have access to the Kotlin Slack, you can request it
[here](https://surveys.jetbrains.com/s3/kotlin-slack-sign-up).

## License
Jewel is licensed under the [Apache 2.0 license](https://github.com/JetBrains/jewel/blob/main/LICENSE).

```
   Copyright 2022–3 JetBrains s.r.o.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
```
