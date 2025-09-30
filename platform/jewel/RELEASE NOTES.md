# Jewel Release Notes

## v0.29 (2025-07-22)

| Supported IJP versions | Compose Multiplatform version |
|------------------------|-------------------------------|
| 2025.2.1+, 2025.1.4.1+ | 1.8.2                         |

### ⚠️ Important Changes

* **IJP 243 is no longer supported** as of this release. Jewel policy remains to support the current stable version and the upcoming IJP version.
* **JEWEL-97** We added _very_ experimental support for native popups in the IDE, based on `JBPopup`. You can enable this feature via the new
  `JewelFlags` API (may take effect only after a recomposition), but we do not recommend doing so
  yet ([#3105](https://github.com/JetBrains/intellij-community/pull/3105))
  * This has no effect in standalone; we'll work on that in the future.
  * This flag means all usages of the Compose `Popup` component, such as tooltips and menus, will render in separate native windows.
  * Enabling this flag _will_ have impacts on several aspects of your application and UI unit tests.
* **JEWEL-464** The standalone build does not have IJP icons inside anymore, you need to bring your own icons. In standalone, you can add a dependency
  on [`com.jetbrains.intellij.platform:icons`](https://mvnrepository.com/artifact/com.jetbrains.intellij.platform/icons)
  instead. ([#3097](https://github.com/JetBrains/intellij-community/pull/3097))
* **JEWEL-504** The "CMP 1.7.1+ blank toolwindow" bug workaround has been removed as the issue does not reproduce in Compose 1.8.1+. Please let us
  know if it still reproduces for you! ([#3083](https://github.com/JetBrains/intellij-community/pull/3083))
* **JEWEL-798** Updated the default `Tooltip` component auto-hide behavior to match Swing (auto-hide after 10 seconds). To restore the previous
  behaviour, please use the `AutoHideBehavior.Never` option in the style. ([#3085](https://github.com/JetBrains/intellij-community/pull/3085))
* **JEWEL-832** Several Markdown APIs that had mistakenly not been annotated as experimental have been marked as
  experimental ([#3077](https://github.com/JetBrains/intellij-community/pull/3077))
  * Several of these experimental Markdown APIs have incompatibly changed in this release; if you use custom Markdown rendering and customise the
    styling or parsing, you may have to manually fix breakages in affected code.
* **JEWEL-837** The `Typography` APIs have changed. The new way to access typography is via `JewelTheme.typography` instead of directly accessing
  the (now deprecated) `Typography` object. The `[...].ui.component.Typography` API is deprecated and should be replaced by `JewelTheme.typography`.
  Automatic replacement is provided. ([#3073](https://github.com/JetBrains/intellij-community/pull/3073))
* **IJPL-158073** We've introduced a new `compose { }` API for IJ bridge users to easily inject Compose UI in a Swing layout. It is an alias for the
  existing `JewelComposePanel`. We also provide `composeWithoutTheme { }`, an alias for
  `JewelComposeNoThemePanel`. ([#3099](https://github.com/JetBrains/intellij-community/pull/3099))

### New features

* **JEWEL-97** Added support to use `JBPopup` API for `Popup`s, `Tooltip`s and `Menu`s. This allows popups to grow larger than, and go outside the
  composable area, if needed ([#3105](https://github.com/JetBrains/intellij-community/pull/3105))
* **JEWEL-525** Added level-based bullet points and ordered items styling, up to the third level. This closely mirrors how Markdown lists are rendered
  in IJ and GitHub. ([#3063](https://github.com/JetBrains/intellij-community/pull/3063))
* **JEWEL-558** Added support for contrast scrollbars in the bridge ([#3019](https://github.com/JetBrains/intellij-community/pull/3019))
* **JEWEL-472** Added an extension to load images in Markdown, using Coil3 ([#2924](https://github.com/JetBrains/intellij-community/pull/2924))
* **JEWEL-766** Added support for navigating tabs in a `TabStrip` with left/right
  arrows ([#3016](https://github.com/JetBrains/intellij-community/pull/3016))
* **JEWEL-776** Added a `ContextMenuItemOption` API to provide icons and shortcut data (a hint and the actual keystroke) to menu
  items ([#3091](https://github.com/JetBrains/intellij-community/pull/3091))
* **JEWEL-788** Added API to customize code language detection in
  `MarkdownProcessor` ([#3054](https://github.com/JetBrains/intellij-community/pull/3054))
* **JEWEL-812** Added an improved clipboard that delegates to the IDE's clipboard in the bridge, instead of to AWT's
  clipboard ([#3050](https://github.com/JetBrains/intellij-community/pull/3050))
* **JEWEL-827** Enabled full keyboard navigation in the tab-strip component ([#3066](https://github.com/JetBrains/intellij-community/pull/3066))
* **JEWEL-845** Added a context menu with two default options when right-clicking `ExternalLink`, like the Swing
  equivalents ([#3105](https://github.com/JetBrains/intellij-community/pull/3105))
* **IJPL-188624** Added a single `intellij.platform.compose.markdown` module to include all Markdown dependencies, including extensions
  ([`bad53dd9`](https://github.com/JetBrains/intellij-community/commit/bad53dd9))

### Bug fixes

* **JEWEL-640** Fixed undecorated `TextField` and `TextArea` to show placeholders (if they have
  any) ([#3111](https://github.com/JetBrains/intellij-community/pull/3111))
* **JEWEL-797** Improved `Tooltip` delay duration when appearing and disappearing to better match the
  IDE ([#3029](https://github.com/JetBrains/intellij-community/pull/3029))
* **JEWEL-803** Fixed a crash in `ThemeColorPalette` with IJ themes that have no palette
  defined ([#3015](https://github.com/JetBrains/intellij-community/pull/3015))
* **JEWEL-809** Fixed fullscreen mode for decorated windows on macOS ([#3034](https://github.com/JetBrains/intellij-community/pull/3034))
* **JEWEL-817** Fixed `iconClass` parameter defaults for `*IconActionButton` to use
  `iconKey.iconClass` ([#3046](https://github.com/JetBrains/intellij-community/pull/3046))
* **JEWEL-818** Fixed crashes with malformed third-party themes (e.g., Material) that declare invalid values, by hardening the bridge LaF reading
  logic ([#3072](https://github.com/JetBrains/intellij-community/pull/3072))
* **JEWEL-819** Fixed crash in Markdown when editing while using
  `MarkdownMode.EditorPreview` ([#3051](https://github.com/JetBrains/intellij-community/pull/3051))
* **JEWEL-821** Fixed Jewel Maven publications to include all transitive
  dependencies ([#3056](https://github.com/JetBrains/intellij-community/pull/3056))
* **JEWEL-827** Fixed horizontal scroll issues in the `TabStrip` component ([#3066](https://github.com/JetBrains/intellij-community/pull/3066))
* **JEWEL-829** Fixed bridge theme not working correctly after JEWEL-558 ([#3062](https://github.com/JetBrains/intellij-community/pull/3062))
* **JEWEL-830** Fixed editor `TextStyle` line height being too short in bridge ([#3064](https://github.com/JetBrains/intellij-community/pull/3064))
* **JEWEL-836** Fixed `SegmentedControlButton` in bridge to align with specs and
  Swing ([#3103](https://github.com/JetBrains/intellij-community/pull/3103))
* **JEWEL-837** Fixed default text styles height in standalone ([#3073](https://github.com/JetBrains/intellij-community/pull/3073))
* **JEWEL-840** Fixed styling not always updating in bridge (e.g., when the editor font
  changes) ([#3078](https://github.com/JetBrains/intellij-community/pull/3078))
* **JEWEL-843** Improved disabled appearance for icons; now it looks exactly like the IntelliJ
  counterpart ([#3094](https://github.com/JetBrains/intellij-community/pull/3094))
* **JEWEL-852** Fixed crash with keyboard navigation on `ComboBox`es ([#3106](https://github.com/JetBrains/intellij-community/pull/3106))
* **JEWEL-868** Fixed parameter value leak in `Modifier.onHover` and
  `Modifier.onMove` ([#3116](https://github.com/JetBrains/intellij-community/pull/3116))
* **JEWEL-874** Fixed `reverseLayout` behaviour for non-lazy `ScrollableContainer`
  variants ([#3118](https://github.com/JetBrains/intellij-community/pull/3118))
* **IJPL-175720** Fixed Markdown scrolling sync to handle multiple identical blocks
  correctly ([#2990](https://github.com/JetBrains/intellij-community/pull/2990))

### Deprecated API

* **JEWEL-776** `MenuManager` is now deprecated. You should use `MenuController` or `BaseMenuController`. This new interface now handles shortcut key
  presses ([#3091](https://github.com/JetBrains/intellij-community/pull/3091))
* **JEWEL-798** The `Tooltip` composable with an `AutoHideBehavior` argument was deprecated; please use the version without it, and set the behavior
  in the
  style parameter instead ([#3085](https://github.com/JetBrains/intellij-community/pull/3085))
* **JEWEL-837** `org.jetbrains.jewel.ui.component.Typography` and related API have been deprecated, please migrate usages to
  `org.jetbrains.jewel.ui.Typography` and `JewelTheme.typography` ([#3073](https://github.com/JetBrains/intellij-community/pull/3073))
* **JEWEL-843** `ColorFilter.Companion.disabled()` was deprecated. Avoid using this function and use the `disabledAppearance()` modifier instead to
  make something look disabled ([#3094](https://github.com/JetBrains/intellij-community/pull/3094))

## v0.28 (2025-05-16)

| Supported IJP versions             | Compose Multiplatform version |
|------------------------------------|-------------------------------|
| 2025.2 EAP1+, 2025.1.1+, 2024.3.6+ | 1.8.0-alpha04                 |

> [!IMPORTANT]
> Jewel 0.28 is the first Jewel version to be published since the migration into the IJP codebase.
> Users of the library are expected to use the **bundled dependencies** when running in the IJP, and the
> Maven artefacts for running in non-IJP scenarios (standalone).
>
> A sample setup using the IJP Gradle plugin is available [here](https://github.com/rock3r/jewel-ijp-template).
> If you need to support IJP 243.5 or lower, you'll need to use the bridge Maven artefacts. An example is available
> in that repository's [`243-and-251-compat`](https://github.com/rock3r/jewel-ijp-template/tree/243-and-251-compat) branch.

### New versioning scheme and Maven coordinates

Starting from Jewel 0.28, artefacts are published to Maven Central. It is no longer necessary to add a custom repository to your project to access
Jewel artefacts. You can remove the `https://packages.jetbrains.team/maven/p/kpm/public` repository from your builds.

Artefact IDs are also changing, removing the `-[ijpVersion]` suffixes. Now, the target IJP version is included in the version number. The new version
format is: `[jewelVersion]-[ijpBuild]`. The `jewelVersion` tells you that a certain set of features and APIs are available, and the `ijpBuild` tells
you which IJP build this was derived from. For example, `0.28-243.15667` means that the artifact contains the Jewel `0.28` features and APIs, and was
built from the IJP `243.15567` build. From the `ijpBuild` you can derive two pieces of information; the first is that this artefact is targeting IJP
243, and the second is that it is guaranteed to work for 243 builds with ID >= 15567.

As mentioned in the Jewel readme, if you're building a purely non-IJP app with Jewel, it's always recommended to use the version with the highest
`jewelVersion` and `ijpBuild`s, as to get the latest version of Jewel with all the features supported in standalone mode. If, however, you are using
the standalone artefacts in conjunction with bridge artefacts, you must always use the same version of the standalone and bridge artefacts to avoid
incompatibilities. An example use case is using standalone artefacts in UI unit tests to avoid having to spin up the entire IJ platform, which can
cause flakiness and obscure issues in UI tests.

> [!NOTE]
> For standalone projects, until [JEWEL-821] is resolved, you'll need to add a custom repository to your build, in order to let Gradle find the
> custom-built `skiko-awt-runtime-all` dependency:
>
> ```kotlin
> maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
> ```
>
> You will also need to add the required transitive dependencies that have erroneously marked as _runtime_ in the POMs. For example, for
`int-ui-standalone`, you need to add:
>
> ```toml
> jewel-ui = { module = "org.jetbrains.jewel:jewel-ui", version.ref = "jewel" }
> jewel-foundation = { module = "org.jetbrains.jewel:jewel-foundation", version.ref = "jewel" }
> kotlin-stdlib = { module = "org.jetbrains.kotlin:kotlin-stdlib", version.ref = "kotlin" }
> skiko-awt = { module = "org.jetbrains.skiko:skiko-awt", version = "0.9.2" }
> ```

### Binary and source compatibility

Artefacts with the same `jewelVersion` number are generally binary and source compatible with each other across IJP versions, with the exception of
deprecated and scheduled-for-removal APIs, which can be removed in newer versions.

Jewel APIs marked as experimental can change at any time and no guarantees are made on their binary and source compatibility. We'll try to minimise
these changes as much as possible, but by using these APIs you acknowledge there might be disruptions. APIs marked as internal must never be used by
clients and are subject to change at any point without notice, and no compatibility guarantees of any kind are made about them.

### New Features & Bug Fixes

**Dependency Updates:** Key dependencies have been updated, including Compose to 1.8.0-alpha04 and Gradle to
8.13. ([#2927](https://github.com/JetBrains/intellij-community/pull/2927), [#2992](https://github.com/JetBrains/intellij-community/pull/2992))

**New Features & Improvements:**

* **UI Components:**
  * `ListComboBox` component family expanded with `EditableComboBox` and generic variants. Includes fixes for selection clearing and popup closing
    behavior. ([#2965](https://github.com/JetBrains/intellij-community/pull/2965), [#2952](https://github.com/JetBrains/intellij-community/pull/2952))
  * Added new Banner components (Default and Inline) with info, success, warning, and error
    variants. ([#3012](https://github.com/JetBrains/intellij-community/pull/3012), [#711](https://github.com/JetBrains/jewel/pull/711), [#2906](https://github.com/JetBrains/intellij-community/pull/2906))
  * Added slot APIs to `GroupHeader`. ([#719](https://github.com/JetBrains/jewel/pull/719))
  * Added `SplitButton` component. ([#2909](https://github.com/JetBrains/intellij-community/pull/2909))
* **Markdown:**
  * Added support for strikethrough, tables (GFM), synchronized scrolling, and modifiers for block
    renderers. ([#2915](https://github.com/JetBrains/intellij-community/pull/2915), [#2913](https://github.com/JetBrains/intellij-community/pull/2913), [#2908](https://github.com/JetBrains/intellij-community/pull/2908), [#2993](https://github.com/JetBrains/intellij-community/pull/2993))
  * Fixed issues with nested inlines and table block
    inheritance. ([#2994](https://github.com/JetBrains/intellij-community/pull/2994), [#2941](https://github.com/JetBrains/intellij-community/pull/2941))
* **Accessibility:** Improved accessibility, including iterations on Tree a11y and investigation into
  limitations. ([#2928](https://github.com/JetBrains/intellij-community/pull/2928), [#2976](https://github.com/JetBrains/intellij-community/pull/2976),
  JEWEL-756)
* **Misc:**
  * Massive KDOC improvements across public APIs. ([#2935](https://github.com/JetBrains/intellij-community/pull/2935))
  * Tuned logging configuration. ([#704](https://github.com/JetBrains/jewel/pull/704))

**Notable Bug Fixes:**

* Fixed indeterminate state not working in Checkboxes. ([#705](https://github.com/JetBrains/jewel/pull/705))
* Fixed excessive recompositions in
  `SelectableLazyColumn`. ([#2905](https://github.com/JetBrains/intellij-community/pull/2905), [#723](https://github.com/JetBrains/jewel/pull/723))
* Fixed disabled state colors for dropdown menu
  items. ([#2904](https://github.com/JetBrains/intellij-community/pull/2904), [#717](https://github.com/JetBrains/jewel/pull/717))
* Fixed `DecoratedWindow` behavior on Windows. ([#2920](https://github.com/JetBrains/intellij-community/pull/2920))
* Fixed modifier handling in `Link`. ([#2942](https://github.com/JetBrains/intellij-community/pull/2942))
* Fixed incorrect index assignment in `toggleKeySelection`. ([#2900](https://github.com/JetBrains/intellij-community/pull/2900))
* Fixed wrong registry key for `TooltipMetrics` delay. ([#3011](https://github.com/JetBrains/intellij-community/pull/3011))
* Fixed git hooks on non-Windows OSes. ([#2943](https://github.com/JetBrains/intellij-community/pull/2943))
* Fixed log paths in run configurations. ([#2996](https://github.com/JetBrains/intellij-community/pull/2996))

### Breaking Changes

* Removed deprecated and scheduled for removal APIs. ([#2998](https://github.com/JetBrains/intellij-community/pull/2998))
* Renamed the `extension` module and package to `extensions`. Renamed `Alert` class to
  `GitHubAlert`. ([#2995](https://github.com/JetBrains/intellij-community/pull/2995))
* Reallocated styles in appropriate files (may require import updates). ([#2910](https://github.com/JetBrains/intellij-community/pull/2910))
* Rewritten and cleaned up `ListComboBox` internals (behavior should be consistent, but internal structure
  changed). ([#2912](https://github.com/JetBrains/intellij-community/pull/2912), [#715](https://github.com/JetBrains/jewel/pull/715))

### Deprecated API

* Deprecated `Dropdown` component; use `ListComboBox` instead. ([#2911](https://github.com/JetBrains/intellij-community/pull/2911))
* Moved the `thenIf` modifier function from `ui` to `foundation` module. ([#2923](https://github.com/JetBrains/intellij-community/pull/2923))
* Introduced new stateless `ListComboBox` variants; older stateful ones may be deprecated
  later. ([#2955](https://github.com/JetBrains/intellij-community/pull/2955))
* Removed context receivers from `KeyActions`. ([#3014](https://github.com/JetBrains/intellij-community/pull/3014))

## Earlier versions (0.27 and lower)

Please refer to the [releases page](https://github.com/JetBrains/jewel/releases) on the (archived) Jewel repository.
