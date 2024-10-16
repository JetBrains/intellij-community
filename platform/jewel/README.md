[![JetBrains incubator](https://img.shields.io/badge/JetBrains-incubator-yellow?logo=data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIGhlaWdodD0iMzIuMDAwMDEiIHZpZXdCb3g9IjAgMCAzMiAzMi4wMDAwMSIgd2lkdGg9IjMyIj48c2NyaXB0IHhtbG5zPSIiLz48cGF0aCBkPSJtMCAwaDMydjMyLjAwMDAxaC0zMnoiLz48cGF0aCBkPSJtNCAyNi4wMDAwMWgxMnYyaC0xMnoiIGZpbGw9IiNmZmYiLz48L3N2Zz4=)](https://github.com/JetBrains#jetbrains-on-github) [![CI checks](https://img.shields.io/github/actions/workflow/status/JetBrains/jewel/build.yml?logo=github)](https://github.com/JetBrains/jewel/actions/workflows/build.yml) [![Licensed under Apache 2.0](https://img.shields.io/github/license/JetBrains/jewel?logo=data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIGZpbGw9Im5vbmUiIHN0cm9rZT0iI0ZGRiIgdmlld0JveD0iMCAwIDI0IDI0Ij48cGF0aCBzdHJva2UtbGluZWNhcD0icm91bmQiIHN0cm9rZS1saW5lam9pbj0icm91bmQiIHN0cm9rZS13aWR0aD0iMiIgZD0ibTMgNiAzIDFtMCAwLTMgOWE1LjAwMiA1LjAwMiAwIDAgMCA2LjAwMSAwTTYgN2wzIDlNNiA3bDYtMm02IDIgMy0xbS0zIDEtMyA5YTUuMDAyIDUuMDAyIDAgMCAwIDYuMDAxIDBNMTggN2wzIDltLTMtOS02LTJtMC0ydjJtMCAxNlY1bTAgMTZIOW0zIDBoMyIvPjwvc3ZnPg==)](https://github.com/JetBrains/jewel/blob/main/LICENSE) [![Latest release](https://img.shields.io/github/v/release/JetBrains/jewel?include_prereleases&label=Latest%20Release&logo=github)](https://github.com/JetBrains/jewel/releases/latest) ![Compose for Desktop version](https://img.shields.io/badge/Compose%20for%20Desktop-1.6.0-dev1369?logo=data%3Aimage%2Fsvg%2Bxml%3Bbase64%2CPHN2ZyB2aWV3Qm94PSIwIDAgNjcgNzQiIGZpbGw9Im5vbmUiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI%2BPHBhdGggZmlsbC1ydWxlPSJldmVub2RkIiBjbGlwLXJ1bGU9ImV2ZW5vZGQiIGQ9Ik0zNS45OTkgMi42NjNhNS4wMSA1LjAxIDAgMCAwLTQuOTk4IDBsLTI2LjUgMTUuMjUzYTQuOTk0IDQuOTk0IDAgMCAwLTEuMTk4Ljk2MmwxMS4xMDggNi4zNjZjLjI2OC0uMjkuNTgtLjU0LjkzMS0uNzQ0bDE2LjE1Ni05LjM0MmE0IDQgMCAwIDEgNC4wMDQgMEw1MS42NTcgMjQuNWMuMzUxLjIwMy42NjQuNDU1LjkzMi43NDRsMTEuMTA4LTYuMzY2YTQuOTkxIDQuOTkxIDAgMCAwLTEuMTk4LS45NjJsLTI2LjUtMTUuMjUzWm0yOC43MjMgMTcuOTMzLTExLjE4MyA2LjQwOGMuMDc2LjMxLjExNi42MzIuMTE2Ljk1OXYxNy43OTRhNCA0IDAgMCAxLTEuOTU4IDMuNDRsLTE2LjIzNSA5LjYzOGEzLjk5OCAzLjk5OCAwIDAgMS0uOTYyLjQxMnYxMi42M2E1LjAwNSA1LjAwNSAwIDAgMCAxLjQyOC0uNTY5bDI2LjYyLTE1LjczQTQuOTg2IDQuOTg2IDAgMCAwIDY1IDUxLjI4NFYyMi4yMzdjMC0uNTY3LS4wOTctMS4xMi0uMjc4LTEuNjRaTTIgMjIuMjM3YzAtLjU2Ny4wOTctMS4xMi4yNzgtMS42NGwxMS4xODMgNi40MDdjLS4wNzYuMzEtLjExNi42MzItLjExNi45NTl2MTguNjMzYTQgNCAwIDAgMCAyLjA4IDMuNTA5bDE2LjA3NCA4LjhjLjMyLjE3NC42NTYuMzAyIDEuMDAxLjM4NHYxMi42MzhhNS4wMDUgNS4wMDUgMCAwIDEtMS41MTctLjUzM0w0LjYwMyA1Ny4wMkE0Ljk4NyA0Ljk4NyAwIDAgMSAyIDUyLjY0MlYyMi4yMzdaTTMwLjAwMi45MzVhNy4wMTQgNy4wMTQgMCAwIDEgNi45OTYgMGwyNi41IDE1LjI1M0E2Ljk4IDYuOTggMCAwIDEgNjcgMjIuMjM4djI5LjA0N2E2Ljk4IDYuOTggMCAwIDEtMy40MzMgNi4wMDlsLTI2LjYyIDE1LjczMWE3LjAxNCA3LjAxNCAwIDAgMS02LjkyMy4xMkwzLjY0NCA1OC43NzFBNi45ODEgNi45ODEgMCAwIDEgMCA1Mi42NDFWMjIuMjM4YTYuOTggNi45OCAwIDAgMSAzLjUwMi02LjA1TDMwLjAwMi45MzZabS04LjYwNCAyNy41NTIgMTAuNTgyLTYuMTFjLjk0LS41NDIgMi4xLS41NDIgMy4wNCAwbDEwLjU4MiA2LjExYTIuOTk2IDIuOTk2IDAgMCAxIDEuNTAzIDIuNTkzdjExLjY1M2MwIDEuMDU2LS41NiAyLjAzNC0xLjQ3MyAyLjU3NmwtMTAuNjQzIDYuMzA4YTMuMDQ0IDMuMDQ0IDAgMCAxLTMuMDA5LjA1MmwtMTAuNTItNS43NWEyLjk5NiAyLjk5NiAwIDAgMS0xLjU2NS0yLjYyN1YzMS4wOGMwLTEuMDY4LjU3My0yLjA1NiAxLjUwMy0yLjU5M1oiIGZpbGw9IiNmZmYiLz48L3N2Zz4%3D)

# Jewel: a Compose for Desktop theme

<img alt="Jewel logo" src="art/jewel-logo.svg" width="20%"/>

Jewel aims at recreating the IntelliJ Platform's _New UI_ Swing Look and Feel in Compose for Desktop, providing a
desktop-optimized theme and set of components.

> [!WARNING]
>
> This project is in active development, and caution is advised when considering it for production uses. You _can_ use
> it, but you should expect APIs to change often, things to move around and/or break, and all that jazz. Binary
> compatibility is not guaranteed across releases, and APIs are still in flux and subject to change.
>
> Writing 3rd party IntelliJ Plugins in Compose for Desktop is currently **not officially supported** by the IntelliJ
> Platform. It should work, but your mileage may vary, and if things break you're on your own.
>
> Use at your own risk!

Jewel provides an implementation of the IntelliJ Platform themes that can be used in any Compose for Desktop
application. Additionally, it has a Swing LaF Bridge that only works in the IntelliJ Platform (i.e., used to create IDE
plugins), but automatically mirrors the current Swing LaF into Compose for a native-looking, consistent UI.

> [!TIP]
> <a href="https://www.droidcon.com/2023/11/15/meet-jewelcreate-ide-plugins-in-compose/">
> <img src="https://i.vimeocdn.com/video/1749849437-f275e0337faca5cedab742ea157abbafe5a0207d3a59db891a72b6180ce13a6c-d?mh=120" align="left" />
> </a>
>
> If you want to learn more about Jewel and Compose for Desktop and why they're a great, modern solution for your
> desktop
> UI needs, check out [this talk](https://www.droidcon.com/2023/11/15/meet-jewelcreate-ide-plugins-in-compose/) by Jewel
> contributors Sebastiano and Chris.
>
> It covers why Compose is a viable choice, and an overview of the Jewel project, plus
> some real-life use cases.<br clear="left" />

<br/>

## Getting started

The first thing to add is the necessary Gradle plugins, including the Compose Multiplatform plugin. You need to add a
custom repository for it in `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        mavenCentral()
    }
}
```

Then, in your app's `build.gradle.kts`:

```kotlin
plugins {
    // MUST align with the Kotlin and Compose dependencies in Jewel
    kotlin("jvm") version "..."
    id("org.jetbrains.compose") version "..."
}

repositories {
    maven("https://packages.jetbrains.team/maven/p/kpm/public/")
    // Any other repositories you need (e.g., mavenCentral())
}
```

> [!WARNING]
> If you use convention plugins to configure your project you might run into issues such as
> [this](https://github.com/JetBrains/compose-multiplatform/issues/3748). To solve it, make sure the
> plugins are only initialized once — for example, by declaring them in the root `build.gradle.kts`
> with `apply false`, and then applying them in all the submodules that need them.

To use Jewel in your app, you only need to add the relevant dependency. There are two scenarios: standalone Compose for
Desktop app, and IntelliJ Platform plugin.

If you're writing a **standalone app**, then you should depend on the latest `int-ui-standalone-*` artifact:

```kotlin
dependencies {
    // See https://github.com/JetBrains/Jewel/releases for the release notes
    implementation("org.jetbrains.jewel:jewel-int-ui-standalone-[latest platform version]:[jewel version]")

    // Optional, for custom decorated windows:
    implementation("org.jetbrains.jewel:jewel-int-ui-decorated-window-[latest platform version]:[jewel version]")

    // Do not bring in Material (we use Jewel)
    implementation(compose.desktop.currentOs) {
        exclude(group = "org.jetbrains.compose.material")
    }
}
```

For an **IntelliJ Platform plugin**, then you should depend on the appropriate `ide-laf-bridge-*` artifact:

```kotlin
dependencies {
    // See https://github.com/JetBrains/Jewel/releases for the release notes
    // The platform version is a supported major IJP version (e.g., 232 or 233 for 2023.2 and 2023.3 respectively)
    implementation("org.jetbrains.jewel:jewel-ide-laf-bridge-[platform version]:[jewel version]")

    // Do not bring in Material (we use Jewel) and Coroutines (the IDE has its own)
    api(compose.desktop.currentOs) {
        exclude(group = "org.jetbrains.compose.material")
        exclude(group = "org.jetbrains.kotlinx")
    }
}
```

<br/>

> [!TIP]
> It's easier to use version catalogs — you can use the Jewel [version catalog](gradle/libs.versions.toml) as reference.

<br/>

## Using ProGuard/obfuscation/minification

Jewel doesn't officially support using ProGuard to minimize and/or obfuscate your code, and there is currently no plan
to.
That said, people are reporting successes in using it. Please note that there is no guarantee that it will keep working,
and you most definitely need to have some rules in place. We don't provide any official rule set, but these have been
known
to work for some: https://github.com/romainguy/kotlin-explorer/blob/main/compose-desktop.pro

> [!IMPORTANT]
> We won't accept bug reports for issues caused by the use of ProGuard or similar tools.

## Dependencies matrix

Jewel is in continuous development and we focus on supporting only the Compose version we use internally.
You can see the latest supported version
in [libs.versions.toml](https://github.com/JetBrains/jewel/blob/main/gradle/libs.versions.toml).

Different versions of Compose are not guaranteed to work with different versions of Jewel.

The Compose Compiler version used is the latest compatible with the given Kotlin version. See
[here](https://developer.android.com/jetpack/androidx/releases/compose-compiler) for the Compose
Compiler release notes, which indicate the compatibility.

The minimum supported Kotlin version is dictated by the minimum supported IntelliJ IDEA platform.

## Project structure

The project is split in modules:

1. `buildSrc` contains the build logic, including:
    * The `jewel` and `jewel-publish` configuration plugins
    * The `jewel-check-public-api` and `jewel-linting` configuration plugins
    * The Theme Palette generator plugin
    * The Studio Releases generator plugin
2. `foundation` contains the foundational Jewel functionality:
    * Basic components without strong styling (e.g., `SelectableLazyColumn`, `BasicLazyTree`)
    * The `JewelTheme` interface with a few basic composition locals
    * The state management primitives
    * The Jewel annotations
    * A few other primitives
3. `ui` contains all the styled components and custom painters logic
4. `decorated-window` contains basic, unstyled functionality to have custom window decoration on the JetBrains Runtime
5. `int-ui` contains two modules:
    * `int-ui-standalone` has a standalone version of the Int UI styling values that can be used in any Compose for
      Desktop app
    * `int-ui-decorated-window` has a standalone version of the Int UI styling values for the custom window decoration
      that can be used in any Compose for Desktop app
6. `ide-laf-bridge` contains the Swing LaF bridge to use in IntelliJ Platform plugins (see more below)
7. `markdown` contains a few modules:
    * `core` the core logic for parsing and rendering Markdown documents with Jewel, using GitHub-like styling
    * `extension` contains several extensions to the base CommonMark specs that can be used to add more features
    * `ide-laf-bridge-styling` contains the IntelliJ Platform bridge theming for the Markdown renderer
    * `int-ui-standalone-styling` contains the standalone Int UI theming for the Markdown renderer
8. `samples` contains the example apps, which showcase the available components:
    * `standalone` is a regular CfD app, using the standalone theme definitions and custom window decoration
    * `ide-plugin` is an IntelliJ plugin that showcases the use of the Swing Bridge

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

To use Jewel in the IntelliJ Platform, you should depend on the appropriate `jewel-ide-laf-bridge-*` artifact, which
will bring in the necessary transitive dependencies. These are the currently supported versions of the IntelliJ Platform
and the branch on which the corresponding bridge code lives:

| IntelliJ Platform version(s) | Branch to use           |
|------------------------------|-------------------------|
| 2024.3 (EAP 6+)              | `main`                  |
| 2024.2 (beta 1+)             | `releases/242`          |
| 2024.1 (EAP 3+)              | `releases/241`          |
| 2023.3 (**archived**)        | `archived-releases/233` |
| 2023.2 (**archived**)        | `archived-releases/232` |
| 2023.1 or older              | **Not supported**       |

For an example on how to set up an IntelliJ Plugin, you can refer to
the [`ide-plugin` sample](samples/ide-plugin/build.gradle.kts).

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
import javax.swing.text.StyledEditorKit.FontFamilyAction

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

* [Package Search](https://github.com/JetBrains/package-search-intellij-plugin) (IntelliJ Platform plugin)
* [Kotlin Explorer](https://github.com/romainguy/kotlin-explorer) (standalone app)
* New task-based Profiler UI in Android Studio Koala
* ...and more to come!

## Throubleshooting

### Git push hook is not working?

On git push you see:

```bash
error: cannot spawn .git/hooks/pre-push: No such file or directory
error: waitpid for (NULL) failed: No child processes
```

Try running `git lfs update --force`.

## Need help?

You can find help on the [`#jewel`](https://app.slack.com/client/T09229ZC6/C05T8U2C31T) channel on the Kotlin Slack.
If you don't already have access to the Kotlin Slack, you can request it
[here](https://surveys.jetbrains.com/s3/kotlin-slack-sign-up).

## License

Jewel is licensed under the [Apache 2.0 license](https://github.com/JetBrains/jewel/blob/main/LICENSE).

```
Copyright 2022–4 JetBrains s.r.o.

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
