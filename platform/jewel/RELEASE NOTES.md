# Jewel Release Notes

## v0.28 (2025-05-16)

Supported IJP versions | Compose Multiplatform version
--- | ---
2025.2 EAP1+, 2025.1.1+, 2024.3.6+ | 1.8.0-alpha04

> [!IMPORTANT]
> Jewel 0.28 is the first Jewel version to be published since the migration into the IJP codebase.
> Users of the library are expected to use the **bundled dependencies** when running in the IJP, and the
> Maven artefacts for running in non-IJP scenarios (standalone).
>
> A sample setup using the IJP Gradle plugin is available [here](https://github.com/rock3r/jewel-ijp-template).
> If you need to support IJP 243.5 or lower, you'll need to use the bridge Maven artefacts. An example is available
> in that repository's [`243-and-251-compat`](https://github.com/rock3r/jewel-ijp-template/tree/243-and-251-compat) branch.

### New versioning scheme and Maven coordinates
Starting from Jewel 0.28, artefacts are published to Maven Central. It is no longer necessary to add a custom repository to your project to access Jewel artefacts. You can remove the `https://packages.jetbrains.team/maven/p/kpm/public` repository from your builds.

Artefact IDs are also changing, removing the `-[ijpVersion]` suffixes. Now, the target IJP version is included in the version number. The new version format is: `[jewelVersion]-[ijpBuild]`. The `jewelVersion` tells you that a certain set of features and APIs are available, and the `ijpBuild` tells you which IJP build this was derived from. For example, `0.28-243.15667` means that the artifact contains the Jewel `0.28` features and APIs, and was built from the IJP `243.15567` build. From the `ijpBuild` you can derive two pieces of information; the first is that this artefact is targeting IJP 243, and the second is that it is guaranteed to work for 243 builds with ID >= 15567.

As mentioned in the Jewel readme, if you're building a purely non-IJP app with Jewel, it's always recommended to use the version with the highest `jewelVersion` and `ijpBuild`s, as to get the latest version of Jewel with all the features supported in standalone mode. If, however, you are using the standalone artefacts in conjunction with bridge artefacts, you must always use the same version of the standalone and bridge artefacts to avoid incompatibilities. An example use case is using standalone artefacts in UI unit tests to avoid having to spin up the entire IJ platform, which can cause flakiness and obscure issues in UI tests.

### Binary and source compatibility
Artefacts with the same `jewelVersion` number are generally binary and source compatible with each other across IJP versions, with the exception of deprecated and scheduled-for-removal APIs, which can be removed in newer versions.

Jewel APIs marked as experimental can change at any time and no guarantees are made on their binary and source compatibility. We'll try to minimise these changes as much as possible, but by using these APIs you acknowledge there might be disruptions. APIs marked as internal must never be used by clients and are subject to change at any point without notice, and no compatibility guarantees of any kind are made about them.

### New Features & Bug Fixes

**Dependency Updates:** Key dependencies have been updated, including Compose to 1.8.0-alpha04 and Gradle to 8.13. ([#2927](https://github.com/JetBrains/intellij-community/pull/2927), [#2992](https://github.com/JetBrains/intellij-community/pull/2992))

**New Features & Improvements:**

* **UI Components:**
    * `ListComboBox` component family expanded with `EditableComboBox` and generic variants. Includes fixes for selection clearing and popup closing behavior. ([#2965](https://github.com/JetBrains/intellij-community/pull/2965), [#2952](https://github.com/JetBrains/intellij-community/pull/2952))
    * Added new Banner components (Default and Inline) with info, success, warning, and error variants. ([#3012](https://github.com/JetBrains/intellij-community/pull/3012), [#711](https://github.com/JetBrains/jewel/pull/711), [#2906](https://github.com/JetBrains/intellij-community/pull/2906))
    * Added slot APIs to `GroupHeader`. ([#719](https://github.com/JetBrains/jewel/pull/719))
    * Added `SplitButton` component. ([#2909](https://github.com/JetBrains/intellij-community/pull/2909))
* **Markdown:**
    * Added support for strikethrough, tables (GFM), synchronized scrolling, and modifiers for block renderers. ([#2915](https://github.com/JetBrains/intellij-community/pull/2915), [#2913](https://github.com/JetBrains/intellij-community/pull/2913), [#2908](https://github.com/JetBrains/intellij-community/pull/2908), [#2993](https://github.com/JetBrains/intellij-community/pull/2993))
    * Fixed issues with nested inlines and table block inheritance. ([#2994](https://github.com/JetBrains/intellij-community/pull/2994), [#2941](https://github.com/JetBrains/intellij-community/pull/2941))
* **Accessibility:** Improved accessibility, including iterations on Tree a11y and investigation into limitations. ([#2928](https://github.com/JetBrains/intellij-community/pull/2928), [#2976](https://github.com/JetBrains/intellij-community/pull/2976), JEWEL-756)
* **Misc:**
    * Massive KDOC improvements across public APIs. ([#2935](https://github.com/JetBrains/intellij-community/pull/2935))
    * Tuned logging configuration. ([#704](https://github.com/JetBrains/jewel/pull/704))

**Notable Bug Fixes:**

* Fixed indeterminate state not working in Checkboxes. ([#705](https://github.com/JetBrains/jewel/pull/705))
* Fixed excessive recompositions in `SelectableLazyColumn`. ([#2905](https://github.com/JetBrains/intellij-community/pull/2905), [#723](https://github.com/JetBrains/jewel/pull/723))
* Fixed disabled state colors for dropdown menu items. ([#2904](https://github.com/JetBrains/intellij-community/pull/2904), [#717](https://github.com/JetBrains/jewel/pull/717))
* Fixed `DecoratedWindow` behavior on Windows. ([#2920](https://github.com/JetBrains/intellij-community/pull/2920))
* Fixed modifier handling in `Link`. ([#2942](https://github.com/JetBrains/intellij-community/pull/2942))
* Fixed incorrect index assignment in `toggleKeySelection`. ([#2900](https://github.com/JetBrains/intellij-community/pull/2900))
* Fixed wrong registry key for `TooltipMetrics` delay. ([#3011](https://github.com/JetBrains/intellij-community/pull/3011))
* Fixed git hooks on non-Windows OSes. ([#2943](https://github.com/JetBrains/intellij-community/pull/2943))
* Fixed log paths in run configurations. ([#2996](https://github.com/JetBrains/intellij-community/pull/2996))

### Breaking Changes

* Removed deprecated and scheduled for removal APIs. ([#2998](https://github.com/JetBrains/intellij-community/pull/2998))
* Renamed the `extension` module and package to `extensions`. Renamed `Alert` class to `GitHubAlert`. ([#2995](https://github.com/JetBrains/intellij-community/pull/2995))
* Reallocated styles in appropriate files (may require import updates). ([#2910](https://github.com/JetBrains/intellij-community/pull/2910))
* Rewritten and cleaned up `ListComboBox` internals (behavior should be consistent, but internal structure changed). ([#2912](https://github.com/JetBrains/intellij-community/pull/2912), [#715](https://github.com/JetBrains/jewel/pull/715))

### Deprecated API

* Deprecated `Dropdown` component; use `ListComboBox` instead. ([#2911](https://github.com/JetBrains/intellij-community/pull/2911))
* Moved the `thenIf` modifier function from `ui` to `foundation` module. ([#2923](https://github.com/JetBrains/intellij-community/pull/2923))
* Introduced new stateless `ListComboBox` variants; older stateful ones may be deprecated later. ([#2955](https://github.com/JetBrains/intellij-community/pull/2955))
* Removed context receivers from `KeyActions`. ([#3014](https://github.com/JetBrains/intellij-community/pull/3014))

## Earlier versions (0.27 and lower)

Please refer to the [releases page](https://github.com/JetBrains/jewel/releases) on the (archived) Jewel repository.
