# Jewel Release Notes

## v0.28 (2025-04-30)

**Supported IJP versions:** 2025.1.1+, 2024.3.5+
**Compose Multiplatform version:** 1.8.0-alpha0

> [!IMPORTANT]
> Jewel 0.28 is the first Jewel version to be published since the migration into the IJP codebase.
> Users of the library are expected to use the bundled dependencies when running in the IJP, and the
> Maven artefacts for running in non-IJP scenarios (standalone).
> A sample setup using the IJP Gradle plugin is available [here](https://github.com/rock3r/jewel-ijp-template).

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
