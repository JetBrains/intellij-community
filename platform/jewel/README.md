[![JetBrains incubator](https://img.shields.io/badge/JetBrains-incubator-yellow?logo=data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIGhlaWdodD0iMzIuMDAwMDEiIHZpZXdCb3g9IjAgMCAzMiAzMi4wMDAwMSIgd2lkdGg9IjMyIj48c2NyaXB0IHhtbG5zPSIiLz48cGF0aCBkPSJtMCAwaDMydjMyLjAwMDAxaC0zMnoiLz48cGF0aCBkPSJtNCAyNi4wMDAwMWgxMnYyaC0xMnoiIGZpbGw9IiNmZmYiLz48L3N2Zz4=)](https://github.com/JetBrains#jetbrains-on-github)
[![Licensed under Apache 2.0](https://img.shields.io/github/license/JetBrains/jewel?logo=data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIGZpbGw9Im5vbmUiIHN0cm9rZT0iI0ZGRiIgdmlld0JveD0iMCAwIDI0IDI0Ij48cGF0aCBzdHJva2UtbGluZWNhcD0icm91bmQiIHN0cm9rZS1saW5lam9pbj0icm91bmQiIHN0cm9rZS13aWR0aD0iMiIgZD0ibTMgNiAzIDFtMCAwLTMgOWE1LjAwMiA1LjAwMiAwIDAgMCA2LjAwMSAwTTYgN2wzIDlNNiA3bDYtMm02IDIgMy0xbS0zIDEtMyA5YTUuMDAyIDUuMDAyIDAgMCAwIDYuMDAxIDBNMTggN2wzIDltLTMtOS02LTJtMC0ydjJtMCAxNlY1bTAgMTZIOW0zIDBoMyIvPjwvc3ZnPg==)](https://github.com/JetBrains/intellij-community/blob/master/LICENSE.txt)
[![Latest release](https://img.shields.io/badge/Latest%20Release-0.34.0-orange?logo=github)](RELEASE%20NOTES.md)

# Jewel: a Compose for Desktop theme

<img alt="Jewel logo" src="art/jewel-logo.svg" width="20%"/>

Jewel aims at recreating the IntelliJ Platform's _New UI_ Swing Look and Feel in Compose for Desktop, providing a
desktop-optimized theme and set of components.

---

> [!WARNING]
>
> This project experimental and in active development, and caution is advised when considering it for production
> uses. You _can_ use it, but you should expect APIs to change often, things to move around and/or break, and all
> that jazz. Binary compatibility is not guaranteed across releases, and APIs are still in flux and subject to change.
>
> Writing 3rd party IntelliJ Plugins in Compose for Desktop is currently **not officially supported** by the IntelliJ
> Platform. It should work, but your mileage may vary, and if things break you're on your own.
>
> Use at your own risk!

Jewel provides an implementation of the IntelliJ Platform themes that can be used in any Compose for Desktop
application. Additionally, it has a Swing LaF Bridge that only works in the IntelliJ Platform (i.e., used to create IDE
plugins), but automatically mirrors the current Swing LaF into Compose for a native-looking, consistent UI.

> [!TIP]
> <a href="https://www.youtube.com/watch?v=2H1jMn_SGcA">
> <img src="https://i3.ytimg.com/vi/2H1jMn_SGcA/hqdefault.jpg?" align="left" width="150" />
> </a>
>
> If you want to learn more about Jewel and Compose for Desktop and why they're a great, modern solution for your
> desktop
> UI needs, check out [this talk](https://www.youtube.com/watch?v=2H1jMn_SGcA) by Jewel
> contributors Sebastiano and Chris.
>
> It covers why Compose is a viable choice, and an overview of the Jewel project, plus
> some real-life use cases.<br clear="left" />

<br/>

## Getting started

Jewel can be used in two scenarios:

1. A **standalone** Compose for Desktop app
2. An **IntelliJ Platform** plugin

The setup differs significantly between the two.

### Standalone app

Add the Compose Multiplatform repository to your `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}
```

Then add the necessary plugins in your `build.gradle.kts`:

```kotlin
plugins {
    // MUST align with the Kotlin and Compose dependencies in Jewel
    kotlin("jvm") version "..."
    id("org.jetbrains.compose") version "..."
}
```

> [!WARNING]
> If you use convention plugins to configure your project you might run into issues such as
> [this](https://github.com/JetBrains/compose-multiplatform/issues/3748). To solve it, make sure the
> plugins are only initialized once — for example, by declaring them in the root `build.gradle.kts`
> with `apply false`, and then applying them in all the submodules that need them.

And the dependency:

```kotlin
dependencies {
    // Standalone versions follow the format [jewel-version]-[ijp-build] — see VERSIONS.md for the version mapping
    // See https://github.com/JetBrains/intellij-community/blob/master/platform/jewel/RELEASE%20NOTES.md for the release notes
    implementation("org.jetbrains.jewel:jewel-int-ui-standalone:[jewel-version]-[ijp-build]")

    // Optional, for custom decorated windows:
    implementation("org.jetbrains.jewel:jewel-int-ui-decorated-window:[jewel-version]-[ijp-build]")

    // Do not bring in Material (we use Jewel)
    implementation(compose.desktop.currentOs) {
        exclude(group = "org.jetbrains.compose.material")
    }
}
```

### IntelliJ Platform Plugin

No additional repository or plugin setup is needed. Jewel ships bundled with the IntelliJ Platform since 251.2+.
Just declare the bundled modules:

```kotlin
dependencies {
    intellijPlatform {
        //...
        bundledModule("intellij.platform.jewel.foundation")
        bundledModule("intellij.platform.jewel.ui")
        bundledModule("intellij.platform.jewel.ideLafBridge")
        bundledModule("intellij.platform.jewel.markdown.core")
        bundledModule("intellij.platform.jewel.markdown.ideLafBridgeStyling")
        bundledModule("intellij.libraries.compose.foundation.desktop")
        bundledModule("intellij.libraries.skiko")
    }
}
```

## Using ProGuard/obfuscation/minification

Jewel doesn't officially support using ProGuard to minimize and/or obfuscate your code, and there is currently no plan
to. That said, some people are using it. Please note that there is no guarantee that it will keep working,
and you most definitely need to have some rules in place.

We don't provide any official rule set, but these have been known to work for
some: https://github.com/romainguy/kotlin-explorer/blob/main/compose-desktop.pro

> [!IMPORTANT]
> We won't accept bug reports for issues caused by the use of ProGuard or similar tools.

## Dependencies matrix

Jewel is in continuous development, and we focus on supporting only the Compose version we use internally.
You can see what Compose version each Jewel release is built with in the [release notes](RELEASE%20NOTES.md).

Different versions of Compose are not guaranteed to work with different versions of Jewel, especially across
major versions of Compose. When running Jewel in the IJ Platform, you must use the dependencies provided by
the platform itself. You can shadow/jarjar everything and ship your own copy of CMP, Skiko, and Jewel, with
your plugin, but that is not a supported scenario.

The minimum supported Kotlin version is dictated by the minimum supported IntelliJ IDEA platform.

### Version scheme

Standalone Jewel versions follow the format `[jewel-version]-[ijp-build]`, where `[ijp-build]` is the IntelliJ Platform
build number the artifact was compiled against. For example, `0.34.0-253.31033.149` is Jewel 0.34 targeting IJP
build `253.31033.149`.

See [VERSIONS.md](VERSIONS.md) for the full mapping between Jewel and IJP versions.

## Project structure

The project is split in modules:

1. `foundation` contains the foundational Jewel functionality:
   * Basic components without strong styling (e.g., `SelectableLazyColumn`, `BasicLazyTree`)
   * The `JewelTheme` interface with a few basic composition locals
   * The state management primitives
   * The Jewel annotations
   * A few other primitives
2. `ui` contains all the styled components and custom painters logic
   * `ui-tests` contains all the tests for the `ui` module
3. `decorated-window` contains basic, unstyled functionality to have custom window decoration on the JetBrains Runtime
4. `int-ui` contains two modules:
   * `int-ui-standalone` has a standalone version of the Int UI styling values that can be used in any Compose for
     Desktop app
   * `int-ui-decorated-window` has a standalone version of the Int UI styling values for the custom window decoration
     that can be used in any Compose for Desktop app
5. `ide-laf-bridge` contains the Swing LaF bridge to use in IntelliJ Platform plugins (see more below)
6. `markdown` contains a few modules:
   * `core` the core logic for parsing and rendering Markdown documents with Jewel, using GitHub-like styling
   * `extension` contains several extensions to the base CommonMark specs that can be used to add more features
   * `ide-laf-bridge-styling` contains the IntelliJ Platform bridge theming for the Markdown renderer
   * `int-ui-standalone-styling` contains the standalone Int UI theming for the Markdown renderer
7. `samples` contains the example apps, which showcase the available components:
   * `standalone` is a regular CfD app, using the standalone theme definitions and custom window decoration. See DevKit plugin for demo inside IntelliJ IDEA plugin.
   * `showcase` contains the shared component showcase code, used by both the IDE plugin and the standalone sample

## Branching strategy and IJ Platforms

Code on the main branch is developed and tested against the current latest IntelliJ Platform version.

When the EAP for a new major version starts, we cut a `releases/xxx` release branch, where `xxx` is the tracked major
IJP version. At that point, the main branch starts tracking the latest available major IJP version, and changes are
cherry-picked into each release branch as needed. All active release branches have the same functionality (where
supported by the corresponding IJP version), but might differ in platform version-specific fixes and internals.

The standalone Int UI theme will always work the same way as the latest major IJP version; release branches will not
include the `int-ui` module, which is always released from the main branch.

Releases of Jewel are always cut from a tag on the main branch; the HEAD of each `releases/xxx` branch is then tagged
as `[mainTag]-xxx`, and used to publish the artifacts for that major IJP version.

> [!IMPORTANT]
> We only support the latest build of IJP for each major IJP version. If the latest 233 version is 2023.3.3, for
> example, we will only guarantee that Jewel works on that. Versions 2023.3.0–2023.3.2 might or might not work.

> [!CAUTION]
> When you target Android Studio, you might encounter issues due to Studio shipping its own (older) version of Jewel
> and Compose for Desktop. If you want to target Android Studio, you'll need to shadow the CfD and Jewel dependencies
> until that dependency isn't leaked on the classpath by Studio anymore. You can look at how the
> [Package Search](https://github.com/JetBrains/package-search-intellij-plugin) plugin implements shadowing.

## Int UI Standalone theme

The standalone theme can be used in any Compose for Desktop app. You use it as a normal theme, and you can customise it
to your heart's content. By default, it matches the official Int UI specs.

For an example on how to set up a standalone app, you can refer to
the [`standalone` sample](samples/standalone/build.gradle.kts).

> [!WARNING]
> Note that Jewel **requires** the JetBrains Runtime to work correctly. Some features like font loading depend on it,
> as it has extra features and patches for UI functionalities that aren't available in other JDKs.
> We **do not support** running Jewel on any other JDK.

To use Jewel components in a non-IntelliJ Platform environment, you need to wrap your UI hierarchy in a `IntUiTheme`
composable:

```kotlin
IntUiTheme(isDark = false) {
    // ...
}
```

If you want more control over the theming, you can use other `IntUiTheme` overloads, like the standalone sample does.

### Custom window decoration

The JetBrains Runtime allows windows to have a custom decoration instead of the regular title bar.

![A screenshot of the custom window decoration in the standalone sample](art/docs/custom-chrome.png)

The standalone sample app shows how to easily get something that looks like a JetBrains IDE; if you want to go _very_
custom, you only need to depend on the `decorated-window` module, which contains all the required primitives, but not
the Int UI styling.

To get an IntelliJ-like custom title bar, you need to pass the window decoration styling to your theme call, and add the
`DecoratedWindow` composable at the top level of the theme:

```kotlin
IntUiTheme(
    theme = themeDefinition,
    styling = ComponentStyling.default().decoratedWindow(
        titleBarStyle = TitleBarStyle.light()
    ),
) {
    DecoratedWindow(
        onCloseRequest = { exitApplication() },
    ) {
        // ...
    }
}
```

## Running on the IntelliJ Platform: the Swing bridge

Jewel includes a crucial element for proper integration with the IDE: a bridge between the Swing components — theme
and LaF — and the Compose world.

This bridge ensures that we pick up the colours, typography, metrics, and images as defined in the current IntelliJ
theme, and apply them to the Compose components as well. This means Jewel will automatically adapt to IntelliJ Platform
themes that use the [standard theming](https://plugins.jetbrains.com/docs/intellij/themes-getting-started.html)
mechanisms.

> [!NOTE]
> IntelliJ themes that use non-standard mechanisms (such as providing custom UI implementations for Swing components)
> are not, and can never, be supported.

If you're writing an IntelliJ Platform plugin, you should use the `SwingBridgeTheme` instead of the standalone theme:

```kotlin
SwingBridgeTheme {
    // ...
}
```

### Supported IntelliJ Platform versions

Jewel is now shipping as part of the IntelliJ Platform, starting from 251.2+. The Jewel API version (e.g., 0.28) is what determines
binary compatibility of the Jewel APIs across the various supported IJP versions, and standalone.

Please refer to the [release notes](RELEASE%20NOTES.md) to determine the compatibility of each Jewel version with the IJ Platform.

## Icons

Loading icons is best done with the `Icon` composable, which offers a key-based API that is portable across bridge and
standalone modules. Icon keys implement the `IconKey` interface, which is then internally used to obtain a resource path
to load the icon from.

```kotlin
Icon(key = MyIconKeys.myIcon, contentDescription = "My icon")
```

### Loading icons from the IntelliJ Platform

If you want to load an IJ platform icon, you can use `AllIconsKeys`, which is generated from the `AllIcons` platform
file. When using this in an IJ plugin, make sure you are using a version of the Jewel library matching the platform
version, because icons are known to shift between major versions — and sometimes, minor versions, too.

To use icons from `AllIconsKeys` in an IJ plugin, you don't need to do anything, as the icons are in the classpath by
default. If you want to use icons in a standalone app, you'll need to make sure the icons you want are on the classpath.
You can either copy the necessary icons in your resources, matching exactly the path they have in the IDE, or you can
add a dependency to the `com.jetbrains.intellij.platform:icons` artifact, which contains all the icons that end up in
`AllIconsKeys`. The latter is the recommended approach, since it's easy and the icons don't take up much disk space.

Add this to your **standalone app** build script:

```kotlin
dependencies {
    implementation("com.jetbrains.intellij.platform:icons:[ijpVersion]")
    // ...
}

repositories {
    // Choose either of these two, depending on whether you're using a stable IJP or not
    maven("https://www.jetbrains.com/intellij-repository/releases")
    maven("https://www.jetbrains.com/intellij-repository/snapshots")
}
```

> [!NOTE]
> If you are targeting an IntelliJ plugin, you don't need this additional setup since the icons are provided by the
> platform itself.

### Loading your own icons

To access your own icons, you'll need to create and maintain the `IconKey`s for them. We found that the easiest way when
you have up to a few dozen icons is to manually create an icon keys holder, like the ones we have in our samples. If you
have many more, you should consider generating these holders, instead.

In your holders, you can choose which implementation of `IconKey` to use:
* If your icons do not need to change between old UI and new UI, you can use the simpler `PathIconKey`
* If your icons are different in old and new UI, you should use `IntelliJIconKey`, which accepts two paths, one per
  variant
* If you have different needs, you can also implement your own version of `IconKey`

### Painter hints

Jewel has an API to influence the loading and drawing of icons, called `PainterHint`. `Icon` composables have overloads
that take zero, one or more `PainterHint`s that will be used to compute the end result that shows up on screen.

`PainterHint`s can change the icon path (by adding a prefix/suffix, or changing it completely), tweak the contents of an
image (SVG patching, XML patching, bitmap patching), add decorations (e.g., badges), or do nothing at all (`None`). We
have several types of built-in `PainterHint`s which should cover all needs; if you find some use case that is not yet
handled, please file a feature request and we'll evaluate it.

Both standalone and bridge themes provide a default set of implicit `PainterHint`s, for example to implement runtime
patching, like the IDE does. You can also use `PainterHint`s to affect how an icon will be drawn, or to select a
specific icon file, based on some criteria (e.g., `Size`).

If you have a _stateful_ icon, that is if you need to display different icons based on some state, you can use the
`Icon(..., hint)` and `Icon(..., hints)` overloads. You can then use one of the state-mapping `PainterHint` to let
Jewel load the appropriate icon automatically:

```kotlin
// myState implements SelectableComponentState and has a ToggleableState property
val indeterminateHint =
    if (myState.toggleableState == ToggleableState.Indeterminate) {
        IndeterminateHint
    } else {
        PainterHint.None
    }

Icon(
    key = myKey,
    contentDescription = "My icon",
    indeterminateHint,
    Selected(myState),
    Stateful(myState),
)
```

Where the `IndeterminateHint` looks like this:

```kotlin
private object IndeterminateHint : PainterSuffixHint() {
    override fun suffix(): String = "Indeterminate"
}
```

Assuming the PainterProvider has a base path of `components/myIcon.svg`, Jewel will automatically translate it to the
right path based on the state. If you want to learn more about this system, look at the `PainterHint` interface and its
implementations.

Please look at the `PainterHint` implementations and our samples for further information.

### Default icon runtime patching

Jewel emulates the under-the-hood machinations that happen in the IntelliJ Platform when loading icons. Specifically,
the resource will be subject to some transformations before being loaded. This is built on the `PainterHint` API we
described above.

For example, in the IDE, if New UI is active, the icon path may be replaced with a different one. Some key colors in SVG
icons will also be replaced based on the current theme. See
[the docs](https://plugins.jetbrains.com/docs/intellij/work-with-icons-and-images.html#new-ui-icons).

Beyond that, even in standalone, Jewel will pick up icons with the appropriate dark/light variant for the current theme,
and for bitmap icons it will try to pick the 2x variants based on the `LocalDensity`.

## Fonts

To load a system font, you can obtain it by its family name:

```kotlin
val myFamily = FontFamily("My Family")
```

If you want to use a font embedded in the JetBrains Runtime, you can use the `EmbeddedFontFamily` API instead:

```kotlin
// Will return null if no matching font family exists in the JBR
val myEmbeddedFamily = EmbeddedFontFamily("Embedded family")

// It's recommended to load a fallback family when dealing with embedded familes
val myFamily = myEmbeddedFamily ?: FontFamily("Fallback family")
```

You can obtain a `FontFamily` from any `java.awt.Font` — including from `JBFont`s — by using the `asComposeFontFamily()`
API:

```kotlin
val myAwtFamily = myFont.asComposeFontFamily()

// This will attempt to resolve the logical AWT font
val myLogicalFamily = Font("Dialog").asComposeFontFamily()

// This only works in the IntelliJ Platform,
// since JBFont is only available there
val myLabelFamily = JBFont.label().asComposeFontFamily()
```

## Swing interoperability

As this is Compose for Desktop, you get a good degree of interoperability with Swing. To avoid glitches and z-order
issues, you should enable the
[experimental Swing rendering pipeline](https://blog.jetbrains.com/kotlin/2023/08/compose-multiplatform-1-5-0-release/#enhanced-swing-interop)
before you initialize Compose content.

The `ToolWindow.addComposeTab()` extension function provided by the `ide-laf-bridge` module will take care of that for
you. However, if you want to also enable it in other scenarios and in standalone applications, you can call the
`enableNewSwingCompositing()` function in your Compose entry points (that is, right before creating a `ComposePanel`).

> [!NOTE]
> The new Swing rendering pipeline is experimental and may have performance repercussions when using infinitely
> repeating animations. This is a known issue by the Compose Multiplatform team, that requires changes in the Java
> runtime to fix. Once the required changes are made in the JetBrains Runtime, we'll remove this notice.

## Written with Jewel

Here is a small selection of projects that use Compose for Desktop and Jewel:

* [Junie](https://jetbrains.com/junie)
* [Journeys](https://developer.android.com/studio/preview/gemini/journeys)
* Android Studio profilers (Android Studio Koala+)
* [Gemini in Android Studio](https://developer.android.com/studio/preview/gemini)
* [Gemini Code Assist](https://codeassist.google/) for IntelliJ-based IDEs
* [Kotlin Explorer](https://github.com/romainguy/kotlin-explorer) (standalone app)
* [Package Search](https://github.com/JetBrains/package-search-intellij-plugin) (now discontinued)
* ...and more to come!

## Need help?

You can find help on the [`#jewel`](https://app.slack.com/client/T09229ZC6/C05T8U2C31T) channel on the Kotlin Slack.
If you don't already have access to the Kotlin Slack, you can request it
[here](https://surveys.jetbrains.com/s3/kotlin-slack-sign-up).

You can find a series of guides on the Jewel processes in the [`docs`](docs) folder.
