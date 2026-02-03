# Jewel Release Notes

## v0.33 (2025-12-19)

| Min supported IJP versions | Compose Multiplatform version |
|----------------------------|-------------------------------|
| 2025.3.2, 2026.1 EAP       | 1.10.0-rc01                   |

Last release of 2025, with plenty of fixes and new features. See you in 2026! 👋

### ⚠️ Important Changes

* **JEWEL-941** If a Markdown paragraph contains only image components, it will use a `FlowRow` with `Image`s instead of inlines in `Text` ([#3285](https://github.com/JetBrains/intellij-community/pull/3285))
    * This allows for better performance and for some nice visual polish, too
* **JEWEL-1058** The default Markdown image resolver is now capable of resolving local images by a relative path from the source file, or by absolute path ([#3292](https://github.com/JetBrains/intellij-community/pull/3292))
* **JEWEL-1136** ComboBoxes now only use the width required by the label if no width modifier is applied to them ([#3301](https://github.com/JetBrains/intellij-community/pull/3301))
    * Note that it's highly recommended to set a fixed width to ensure UI consistency (i.e., always apply one of these modifiers: `width`, `widthIn`, `fillMaxWidth`, `weight`, ...)
* **JEWEL-1185** Bumped CMP version to 1.10.0-rc01 ([#3329](https://github.com/JetBrains/intellij-community/pull/3329))

### New features

* **JEWEL-876** Improved default string resources handling for standalone ([#3289](https://github.com/JetBrains/intellij-community/pull/3289))
    * We implemented the new `DynamicBundle` API to support loading string from resources in standalone
    * This API is modeled after the IJPL's version of the same name
* **JEWEL-1007** Updated `ComboBox` padding values to better match Swing ([#3290](https://github.com/JetBrains/intellij-community/pull/3290))
* **JEWEL-1058** Introduced `ImageSourceResolver.default`, which allows setting the resolver capabilities (e.g., external URIs, .jar resources, and local images), as well as whether to log resolve issues ([#3292](https://github.com/JetBrains/intellij-community/pull/3292))
* **JEWEL-1070** Annotated UI text parameters with the `@Nls` annotations for better IJPL tooling support ([#3292](https://github.com/JetBrains/intellij-community/pull/3292))
* **JEWEL-1072** Added the experimental `EmbeddedToInlineCssStyleSvgPatchHint` painter hint to support rendering SVG files with embedded CSS class selectors exported from vector graphics editors ([#3335](https://github.com/JetBrains/intellij-community/pull/3335))
    * Converts CSS <style> blocks with .className selectors to inline style attributes during SVG loading
    * Supports CSS cascade (multiple classes, inline style precedence), minified CSS, comments, CDATA sections, and URL references for gradients/patterns
    * Interactive showcase demo added to Icons panel demonstrating the feature
    * For more information, see the `EmbeddedToInlineCssStyleSvgPatchHint` documentation and the [PR](https://github.com/JetBrains/intellij-community/pull/3335)
* **JEWEL-1074** Support center and right alignment in HTML blocks embedded in Markdown ([#3313](https://github.com/JetBrains/intellij-community/pull/3313))

### Bug fixes

* **JEWEL-553** Fixed `TabStrip` scrollbar to better align with Swing's ([#3289](https://github.com/JetBrains/intellij-community/pull/3289))
  * In particular, `TabStrip` now only shows the scrollbar when the container is hovered
* **JEWEL-941** Fixed Markdown images taking up too much space when they fail to load ([#3285](https://github.com/JetBrains/intellij-community/pull/3285))
* **JEWEL-1007** Fixed `ListComboBox` to now visually match Swing when the list is empty ([#3290](https://github.com/JetBrains/intellij-community/pull/3290))
* **JEWEL-1013, JEWEL-1016** Fixed missing popup shadows for CMP-based popups ([#3253](https://github.com/JetBrains/intellij-community/pull/3253))
    * This does not affect native popups, as they delegate the shadow drawing to the OS and they always had the correct shadow
* **JEWEL-1057** Fixed a bug in decorated window action icons (close, maximize, and minimize) where they weren't properly showing on Linux targets ([#3310](https://github.com/JetBrains/intellij-community/pull/3310))
* **JEWEL-1061** Fixed several issues with buttons, most of whom only impact split buttons ([#3283](https://github.com/JetBrains/intellij-community/pull/3283))
    * Fixed a bug with `*SplitButton`s where the chevron would get squashed if the button wasn't wide enough to fit both the main and secondary content
    * Fixed a bug with `*SplitButton`s where the divider height was limited, and would not grow with the button's height if it gets taller than the minimum height
    * Fixed several bugs with `*Button` colours in standalone, especially in the disabled state, to realign them with Swing
    * Fixed the semantics role of the secondary action in `*SplitButton`s to be the same as in Swing
    * Fixed the modifier passed to `*SplitButton`s being applied to the wrong level, causing all sorts of unexpected behaviour (e.g., if you set a height, the button's visual height would not grow)
* **JEWEL-1067** Fixed synchronised scrolling behavior for Markdown ([#3287](https://github.com/JetBrains/intellij-community/pull/3287))
* **JEWEL-1143** Fixed missing support for hovered and pressed states in `TextField` and `TextArea` styling ([#3334](https://github.com/JetBrains/intellij-community/pull/3334))
    * Note that, due to [JEWEL-1193](https://youtrack.jetbrains.com/issue/JEWEL-1193), the hover state _for now_ only works on focused text fields/areas
* **JEWEL-1146** Fixed a Markdown issue where HTML images would not get rendered when there's nothing else in a paragraph ([#3311](https://github.com/JetBrains/intellij-community/pull/3311))
* **JEWEL-1155** Fixed a Markdown tables issue where a cell's content would draw beyond the cell bounds when it's too wide for the cell ([#3317](https://github.com/JetBrains/intellij-community/pull/3317))
* **JEWEL-1158, JEWEL-1159** Fixed multiple issues with text context menus ([#3322](https://github.com/JetBrains/intellij-community/pull/3322))
    * Fixed a crash with the context menu when a `SelectionContainer` has no selection
        * The copy/cut context menu items should not show/be enabled in that case, but due to [CMP-9329](https://youtrack.jetbrains.com/issue/CMP-9329) the copy menu was always visible
        * When the copy or cut actions are clicked, there is a crash
        * Making sure the actions are disabled if visible when the selection is empty fixes the issue
    * Fixed a bug where, in the IDE, the `BasicTextField` context menu would not show a Paste action when it should
        * This was because of an undocumented CMP requirement causing an internal casting to fail
    * Fixed a bug where the context menu could show as empty in some cases, such as the one described above
        * Now, it checks whether it's empty before showing; it only shows if there are any items.
        * As an additional cosmetic improvement, we now show a divider in the context menu between _Cut_/_Copy_/_Paste_, and _Select All_.
    * Fixed a cosmetic issue where the disabled shortcuts and icons in menu items were not appropriately looking disabled
* **JEWEL-1163, JEWEL-1165** Fixed context menu issues with native custom renderer ([#3324](https://github.com/JetBrains/intellij-community/pull/3324))
    * Fixed the positioning logic on `BasicTextField`s context menus
    * Fixed spurious calls to `onGloballyPositioned` when the popup container (Swing `JDialog`, or IJP's `JBPopup`) gets dismissed
    * Fixed issues where the "Key Up" event from the click that displayed the popup was immediately dismissing it

### Deprecated API

* **JEWEL-1067** `ScrollingSynchronizer#scrollToLine` is now marked as non-extendable (`@ApiStatus.NonExtendable`), in favor of new methods `ScrollingSynchronizer#scrollToCoordinate` and `ScrollingSynchronizer#findYCoordinateToScroll` ([#3287](https://github.com/JetBrains/intellij-community/pull/3287)) ([#3287](https://github.com/JetBrains/intellij-community/pull/3287))

## v0.32.1 (2025-12-01)

| Min supported IJP versions | Compose Multiplatform version |
|----------------------------|-------------------------------|
| 2025.3, (2026.1)           | 1.10.0-alpha01                |

Hotfix release for an issue introduced by CMP 1.10.0-alpha01.

### Bug fixes
* **JEWEL-1160** Disabled the (broken) new context menus API introduced in Compose Foundation
  * The CMP flag was enabled in 1.10.0-alpha01 and causes context menus to be broken across Jewel, as we do not support
    the new API yet (we're still missing work on the CMP side to be able to adopt it)
  * CMP disables the flag in 1.10.0-alpha03, but we've postponed bumping the CMP version to the 0.33 release due to other
    issues in later CMP 1.10.0 builds that need to be addressed
* **JEWEL-1158, JEWEL-1159** Fixed multiple issues with text context menus
  * The copy/cut context menu items should not show/be enabled in that case, but due to [CMP-9329](https://youtrack.jetbrains.com/issue/CMP-9329) the copy menu is always visible
    * When the copy or cut actions are clicked, there is a crash
    * Making sure the actions are disabled if visible when the selection is empty fixes the issue
  * Fixed a bug where, in the IDE, the `BasicTextField` context menu would not show a Paste action when it should
    * This was because of an undocumented CMP requirement causing an internal casting to fail
  * Fixed a bug where the context menu could show as empty in some cases, such as the one described above.
    * Now, it checks whether it's empty before showing; it only shows if there are any items.
    * As an additional cosmetic improvement, we now show a divider in the context menu between _Cut_/_Copy_/_Paste_, and _Select All_.
  * Fixed a cosmetic issue where the disabled shortcuts and icons in menu items were not appropriately looking disabled.

## v0.32 (2025-11-25)

| Min supported IJP versions | Compose Multiplatform version |
|----------------------------|-------------------------------|
| 2025.3, (2026.1)           | 1.10.0-alpha01                |

This is a small release, not too much going on. But we still shipped a big improvement to Markdown rendering, which now supports some basic HTML too!

### ⚠️ Important Changes

* **This version is not available on IJP 252** since there has not been any 252 release since the Jewel 0.31 release.
  * The upcoming IJP 2026.1 will include all changes from this version.
* The CMP version is now [1.10.0-beta01](https://kotlinlang.org/docs/multiplatform/whats-new-compose-110.html) (new major version)
* **JEWEL-1043** A few deprecated Markdown APIs have been hidden to promote new non-deprecated overloads ([#3267](https://github.com/JetBrains/intellij-community/pull/3267))
  * The change is non-breaking, both in terms of binary and source compatibility
* **IJPL-214896** The Compose Runtime dependency has been moved out of the `intellij.libraries.compose.foundation.desktop` module and into the `intellij.libraries.compose.runtime.desktop` module
  * You will likely need to add that to your plugin's `plugin.xml` dependencies
  * If you use Jewel in a plugin built through the IntelliJ Platform Gradle plugin, you'll also need to add a `bundledModule` dependency entry until the plugin includes it in the `composeUI` helper

### New features

* **JEWEL-1018** Added `SpeedSearchableComboBox` component that supports speed search ([#3250](https://github.com/JetBrains/intellij-community/pull/3250))
  * It is available inside a `SpeedSearchArea` and has a similar syntax to a normal `ListComboBox`
* **JEWEL-1043** Added initial support for basic HTML in the Markdown renderer ([#3267](https://github.com/JetBrains/intellij-community/pull/3267))
  * Supported tags: `h1..6`, `b`/`strong`, `i`/`em`, `s`/`strike`/`del`, `p`, `br`, `code`, `pre`, `ol`/`ul`/`li`, `a`, `img`, `table`/`th`/`tr`/`td`
  * Supported scroll syncing for code blocks and lists
  * Attributes and custom CSS styling are out of scope of this feature
  * Known issue: ordered and unordered list items use the top-level style regardless of their actual level (JEWEL-1056)
  * Known issue: horizontal alignments are not supported yet (JEWEL-1074)

### Bug fixes
 * **JEWEL-1054** Fixed a race condition with selection management in the dropdown component if used in a non-canonical way

## v0.31 (2025-10-14)

| Min supported IJP versions | Compose Multiplatform version |
|----------------------------|-------------------------------|
| 2025.2.4, 2025.3 EAP       | 1.9.0                         |

### ⚠️ Important Changes

* **IJP 251 is no longer supported** as of this release. Jewel policy remains to support the current stable version and the upcoming IJP version.
* **JEWEL-996** Updated Compose Multiplatform version to 1.9.0 stable ([#3241](https://github.com/JetBrains/intellij-community/pull/3241))

### New features

* **JEWEL-146** Created `SpeedSearchArea` container to provide [speed search](https://www.jetbrains.com/help/idea/speed-search-in-the-tool-windows.html) functionality
  * Speed search is implemented for `SelectableLazyColumn` (in ([#3214](https://github.com/JetBrains/intellij-community/pull/3214))) and `Tree` (in ([#3242](https://github.com/JetBrains/intellij-community/pull/3242))) components
  * `ComboBox`es are not yet supported, but should be in 0.32
  * By default, it does IDE-like "smart matching", implemented by `SpeedSearchMatcher.patternMatcher()`
  * A more classical substring matching behaviour is also optionally available via `SpeedSearchMatcher.substringMatcher()`
  * You can implement `SpeedSearchMatcher` to provide your own matching logic
* **JEWEL-964** Added a `PopupMenu` variant that takes a `PopupPositionProvider` as parameter ([#3202](https://github.com/JetBrains/intellij-community/pull/3202))

### Bug fixes

* **JEWEL-146** Fixed an issue in the `LazyListState.visibleItemsRange` and  `SelectableLazyListState.visibleItemsRange` methods that were returning one more item than they should have ([#3214](https://github.com/JetBrains/intellij-community/pull/3214))
* **JEWEL-146** Fixed potential crashes in the IDE when reading `ComboBox.padding`, `TabbedPane.tabInsets`, and `SpeedSearch.borderInsets` from the LaF ([#3235](https://github.com/JetBrains/intellij-community/pull/3235))
* **JEWEL-174** Fixed toolwindows in the IDE not getting focused when clicking in an empty area or a non-focusable child composable ([#3228](https://github.com/JetBrains/intellij-community/pull/3228))
* **JEWEL-857** Fixed `SimpleListItem`'s semantics to ensure screen readers will read the content description from the children elements ([#3227](https://github.com/JetBrains/intellij-community/pull/3227))
* **JEWEL-857** Fixed `SelectableLazyColumn`'s semantics to ensure screen readers will read the content description from the children elements ([#3227](https://github.com/JetBrains/intellij-community/pull/3227))
* **JEWEL-857** Fixed `SelectableLazyColumn`'s semantics to ensure screen readers will read the selected/unselected state of items correctly ([#3227](https://github.com/JetBrains/intellij-community/pull/3227))
  * If the list uses multiple selection mode, items will be considered as checkboxes
  * If the list uses single selection mode, items will be considered as radio buttons
* **JEWEL-914** Fixed the `Chip` component's appearance in the IDE ([#3239](https://github.com/JetBrains/intellij-community/pull/3239))
* **JEWEL-952** Fixed an issue with menus where multiple sub-menus could be opened at the same time ([#3201](https://github.com/JetBrains/intellij-community/pull/3201))
* **JEWEL-953** Fixed keyboard navigation on custom native popups, in both standalone and the IDE ([#3208](https://github.com/JetBrains/intellij-community/pull/3208))
  * Now, clicking the left arrow only closes the top level
  * Also fixes incorrect popup focus forcing in the IDE
* **JEWEL-953** Fixed keyboard navigation on custom native popups in standalone to cycle around like it does the IDE ([#3208](https://github.com/JetBrains/intellij-community/pull/3208))
* **JEWEL-953** Fixed behaviour when `dismissOnClickOutside = false` for custom native popups in standalone ([#3208](https://github.com/JetBrains/intellij-community/pull/3208))
* **JEWEL-961,JEWEL-984** Fixed issues where `ListComboBox`es were not making focused entries fully visible on keyboard navigation ([#3237](https://github.com/JetBrains/intellij-community/pull/3237))
* **JEWEL-961,JEWEL-984** Fixed issues with string-based `ListComboBox`es not closing when pressing enter ([#3237](https://github.com/JetBrains/intellij-community/pull/3237))
* **JEWEL-961,JEWEL-984** Fixed issues in `ListComboBox`es scrolling to only move by one item at a time, to match Swing implementations ([#3237](https://github.com/JetBrains/intellij-community/pull/3237))
* **JEWEL-962** Fixed `ListComboBox` to properly scroll to ensure the selected item is visible when first opening the popup ([#3243](https://github.com/JetBrains/intellij-community/pull/3243))
* **JEWEL-962** Fixed `ListComboBox` to properly reset the scroll position when the popup is dismissed ([#3234](https://github.com/JetBrains/intellij-community/pull/3234))
* **JEWEL-1001** Fixed the text line height calculation in the IDE when the IDE zoom level is not 100% ([#3236](https://github.com/JetBrains/intellij-community/pull/3236))
* **JEWEL-1002** Fixed an issue that required explicitly providing a theme `instanceUuid` in standalone themes and could cause crashing at startup ([#3216](https://github.com/JetBrains/intellij-community/pull/3216))
* **JEWEL-1006** Fixed an issue in the custom popup renderer in standalone that was incorrectly triggering the "click outside" callback when a submenu was clicked ([#3238](https://github.com/JetBrains/intellij-community/pull/3238))

### Deprecated API

* **JEWEL-146** Deprecated `SelectableLazyColumn` overload that does not accept an `interactionSource` as parameter ([#3214](https://github.com/JetBrains/intellij-community/pull/3214))

## v0.30 (2025-09-04)

| Supported IJP versions | Compose Multiplatform version |
|------------------------|-------------------------------|
| 2025.2.2+, 2025.1.5+   | 1.9.0-beta03                  |

### ⚠️ Important Changes

* **JEWEL-892** All Jewel internal and experimental APIs are now also annotated with the corresponding `ApiStatus` annotation ([#3136](https://github.com/JetBrains/intellij-community/pull/3136))
  * This means they'll be correctly identified as such by all JetBrains tooling, including the Plugin DevKit and Marketplace.
* **JEWEL-896** Extracted Coil dependency as a separate library out of the Markdown Images extension, so other plugins can use it too ([`6d9016a`](https://github.com/JetBrains/intellij-community/commit/6d9016a))
* **JEWEL-897** Experimental API `renderImagesContent` renamed to `renderImageContent` (singular) ([#3145](https://github.com/JetBrains/intellij-community/pull/3145))
* **JEWEL-915** Removed the experimental `JewelToolWindowNoThemeComposePanel` and `composeForToolWindowWithoutTheme` APIs — they were identical to the non-`ToolWindow` variants ([#3143](https://github.com/JetBrains/intellij-community/pull/3143))
* **JEWEL-920** The default `Indication` has been set to a no-op implementation in both standalone and bridge, instead of the previous default implementation we inherited from Compose ([#3161](https://github.com/JetBrains/intellij-community/pull/3161))
  * We handle visual states separately from the `Indication` API, and as such this would only cause visual issues when using certain modifiers (e.g., `selectable`)
* **JEWEL-920** The experimental slot-based `ComboBox` overload has changed in a **breaking** way by reordering its parameters ([#3161](https://github.com/JetBrains/intellij-community/pull/3161))
* **JEWEL-949** All the experimental `*.render` Markdown renderer APIs have been renamed and have lost the `onTextClick` parameter (non-breaking change) ([#3162](https://github.com/JetBrains/intellij-community/pull/3162))
  * They now all have a default implementation that delegates to the new counterparts, ignoring `onTextClick`
* **JEWEL-949** The experimental `Markdown` and `LazyMarkdown` composables have lost the `onTextClick` parameter (non-breaking change) ([#3162](https://github.com/JetBrains/intellij-community/pull/3162))
* **JEWEL-949** The experimental API `ImageRendererExtension.renderImagesContent` was renamed to `renderImageContent` (singular "image") in a **breaking** manner ([#3162](https://github.com/JetBrains/intellij-community/pull/3162))
* **JEWEL-949** The experimental `GitHubTableBlockRenderer` has been made private ([#3162](https://github.com/JetBrains/intellij-community/pull/3162))
* **JEWEL-963** The 'int-ui-decorated-window' is now obsolete and will be removed in the future ([#3175](https://github.com/JetBrains/intellij-community/pull/3175))
  * Please update your dependencies to use the 'decorated-window' library directly
* **JEWEL-963** The 'decorated-window' module does not include the copies from Classes/Interfaces/Methods of JBR-Api ([#3175](https://github.com/JetBrains/intellij-community/pull/3175))
  * If you need access to any JBR-Api method, please use the official library instead - https://github.com/JetBrains/JetBrainsRuntimeApi
* **JEWEL-967** Updated CMP version to 1.9.0-beta03 ([#3188](https://github.com/JetBrains/intellij-community/pull/3188))
* **JEWEL-972** Updated dividers so they are not accessible by screen readers ([#3182](https://github.com/JetBrains/intellij-community/pull/3182))
* **JEWEL-980** `LocalMessageResourceResolverProvider` was not marked as experimental, but should have. This has been rectified ([#3191](https://github.com/JetBrains/intellij-community/pull/3191))
* **JEWEL-985** `JewelTheme.instanceUuid` and `LocalThemeInstanceUuid` are now stable ([#3193](https://github.com/JetBrains/intellij-community/pull/3193))
* **IJPL-200569** Internal classes generated by Compose Compiler Plugin are no longer considered part of the public API ([`161c8f7`](https://github.com/JetBrains/intellij-community/commit/161c8f7))
* **IJPL-174837** Moved Jewel Showcase sample to the DevKit plugin so it's available in the IDE by default (253+) ([`62c5e21`](https://github.com/JetBrains/intellij-community/commit/161c8f7))

### New features

* **JEWEL-286** Added support for `IconButton`s with a transparent background ([#3129](https://github.com/JetBrains/intellij-community/pull/3129))
  * Use a regular `IconButton` and set its style to the new `JewelTheme.transparentIconButtonStyle`
* **JEWEL-686** Added new `Default*Banner` and `Inline*Banner` components with support for automatically hiding the overflowing actions into a dropdown menu ([#3124](https://github.com/JetBrains/intellij-community/pull/3124))
* **JEWEL-875** Added new slot-based API variants to the default banner that accept a Composable as content, getting feature parity with inline banners ([#3132](https://github.com/JetBrains/intellij-community/pull/3132))
* **JEWEL-873** Added experimental support for Popups using a native window to standalone, too ([#3153](https://github.com/JetBrains/intellij-community/pull/3153))
  * Like for the bridge counterpart shipped in 0.29.0, this **experimental** feature is enabled via `JewelFlags`
  * This feature allows your popups to draw outside your Compose Panel/Window
  * The implementation is based on `JDialog` and requires one of the following conditions to work:
    * Use the JetBrains Runtime;
    * Enabled the `compose.interop.blending` system property;
    * Set the LaF flag `Panel.background` to a transparent value;
  * If none of the requirements are met, it falls back to the Compose implementation to avoid UI glitches
  * Please report any bugs and issues you find in both the standalone and bridge implementations!
* **JEWEL-877** Added a `Brush.cssLinearGradient()` API that allows you to create CSS-like linear gradients ([#3121](https://github.com/JetBrains/intellij-community/pull/3121))
  * More info in [this article](https://blog.sebastiano.dev/say-hi-like-youre-ai-gradient-text-in-compose-for-desktop/)
* **JEWEL-897** You can now include local images in your Markdown content. Simply add your image files (like PNGs, JPGs, or SVGs) to your `src/main/resources` folder and reference them directly. For example, to display `my-logo.png` located in `src/main/resources/images/`, you would write: `![My Logo](images/my-logo.png)` ([#3145](https://github.com/JetBrains/intellij-community/pull/3145))
* **JEWEL-911** Added `warning` and `disabledSelected` colors to `TextColors` ([#3144](https://github.com/JetBrains/intellij-community/pull/3144))
* **JEWEL-913** Added a factory function for `InlineMarkdownRenderer` to help you create an inline Markdown renderer ([#3156](https://github.com/JetBrains/intellij-community/pull/3156))
* **JEWEL-920** Added a simpler Boolean-based variant to `Modifier.outline()` ([#3161](https://github.com/JetBrains/intellij-community/pull/3161))
* **JEWEL-943** Added new `InfoText` component to easily show info-styled text ([#3172](https://github.com/JetBrains/intellij-community/pull/3172))
* **JEWEL-948** Added a new overload for scrollable containers that takes a more general `ScrollableState` parameter, that can be used with all lazy containers, as well as non-lazy containers that want to own their scroll modifier ([#3166](https://github.com/JetBrains/intellij-community/pull/3166))
* **JEWEL-940** The current Jewel API version is available at runtime through the `JewelBuild.apiVersionString` property ([#3179](https://github.com/JetBrains/intellij-community/pull/3179))
* **JEWEL-942** Created a new MarkdownText component to allow the use of markdown to easily format text ([#3185](https://github.com/JetBrains/intellij-community/pull/3185))
  * This method is similar to the "Text" component, but adds the parsing feature
  * Note that providing a markdown text that renders another component (such as a heading) may cause crashes
* **JEWEL-947** Added a new `Image` composable that uses Jewel's `IconKey`-based icon loading pipeline to safely load non-icon images in both standalone and bridge modes ([#3180](https://github.com/JetBrains/intellij-community/pull/3180))
* **JEWEL-950** Added a new overload for `ScrollState`-based scrollable containers that allows users to disable scrolling entirely ([#3168](https://github.com/JetBrains/intellij-community/pull/3168))

### Bug fixes

* **JEWEL-842** Fixed the Markdown editor font ligatures settings to match user's IDE settings with all fonts ([#3163](https://github.com/JetBrains/intellij-community/pull/3163))
  * We were only enabling/disabling the `liga` feature, now we also toggle `calt`.
  * Swing/the JBR uses the same two OpenType features, even though there are others too.
* **JEWEL-854** Scrollbars are now hidden from the accessibility context, preventing focus by screen readers ([#3154](https://github.com/JetBrains/intellij-community/pull/3154))
* **JEWEL-879** Fixed `Link` not updating its state correctly (hovering, clicking, focusing, etc.) when it is disabled and re-enabled ([#3128](https://github.com/JetBrains/intellij-community/pull/3128))
* **JEWEL-901** Fixed an issue in the experimental native `Popup` implementation where you needed two clicks to open the popups a second time ([#3131](https://github.com/JetBrains/intellij-community/pull/3131))
  * The popup was properly getting destroyed on dismissal, but the underlying node wasn't, causing this issue.
* **JEWEL-911** Fixed `TextColors.info` not being properly set in Darcula ([#3144](https://github.com/JetBrains/intellij-community/pull/3144))
  * This PR made reading global colours more resilient and accurate in general
* **JEWEL-916** Fixed a crash in the IntelliJ UI Inspector caused by unexpected null values in `AccessibleContext` when inspecting Jewel UI ([#3142](https://github.com/JetBrains/intellij-community/pull/3142))
* **JEWEL-917** Fixed a crash with malformed IDE themes that do not declare the `Button.arc` LaF key ([#3147](https://github.com/JetBrains/intellij-community/pull/3147))
* **JEWEL-918** Fixed the checkbox and radio button appearance in the IDE when using the Darcula theme ([#3148](https://github.com/JetBrains/intellij-community/pull/3148))
* **JEWEL-920** Fixed a bug in `BasicLazyTree` where the item background state was not properly remembered ([#3161](https://github.com/JetBrains/intellij-community/pull/3161))
* **JEWEL-920** Fixed a bug in `ListComboBox` where changing the `itemKeys` parameter value would not be picked up by the component until it exited and re-entered the composition ([#3161](https://github.com/JetBrains/intellij-community/pull/3161))
* **JEWEL-920** Fixed a bug in `CircularProgressIndicator` where changing the `frameRetriever` parameter value would not be picked up by the component until it exited and re-entered the composition ([#3161](https://github.com/JetBrains/intellij-community/pull/3161))
* **JEWEL-920** Fixed a bug where the `PopupMenu` was over-remembering some internal state ([#3161](https://github.com/JetBrains/intellij-community/pull/3161))
* **JEWEL-936** Fixed scrollable containers with `AlwaysVisible` reserving space for the scrollbar even when the content is smaller than the viewport and the scrollbar is not visible ([#3158](https://github.com/JetBrains/intellij-community/pull/3158))
* **JEWEL-936** Fixed a small bug in height calculation for horizontal scrollable containers in edge conditions ([#3158](https://github.com/JetBrains/intellij-community/pull/3158))
* **JEWEL-936** Fixed scrollbar appearance and behaviour in standalone mode on Windows and Linux ([#3158](https://github.com/JetBrains/intellij-community/pull/3158))
* **JEWEL-946** Fixed `SimpleListItem` colours in standalone and bridge ([#3160](https://github.com/JetBrains/intellij-community/pull/3160))
* **JEWEL-946** Fixed incorrect application of the "active" state in `ListComboBox` items ([#3160](https://github.com/JetBrains/intellij-community/pull/3160))
* **JEWEL-949** Fixed a bug that made it impossible to select text in a Markdown paragraph that contains one or more links ([#3162](https://github.com/JetBrains/intellij-community/pull/3162))
* **JEWEL-949** Fixed a bug where disabled fenced code blocks in Markdown would look "lighter" than indented code blocks ([#3162](https://github.com/JetBrains/intellij-community/pull/3162))
* **JEWEL-967** Fixed the Images Markdown extension artifact to correctly declare it depends on Coil3 ([#3188](https://github.com/JetBrains/intellij-community/pull/3188))
* **JEWEL-968** Fixed the Markdown console font ligatures settings to match user's IDE settings with all fonts ([#3178](https://github.com/JetBrains/intellij-community/pull/3178))
* **JEWEL-976** Fixed a `ArrayIndexOutOfBoundsException` in `ListComboBox` when passing an out-of-bounds selected index ([#3184](https://github.com/JetBrains/intellij-community/pull/3184))
* **JEWEL-985** Fixed a number of APIs that were missing the experimental annotations, or were improperly annotated as such: ([#3193](https://github.com/JetBrains/intellij-community/pull/3193))
  * `LocalCodeHighlighter` and `NoOpCodeHighlighter`
  * `LocalPopupRenderer`
* **JEWEL-986** Added missing CMP Resources transitive dependency to the `ui` module's POM ([#3195](https://github.com/JetBrains/intellij-community/pull/3195))
  * Removed unnecessary dependency on the autolink, strikethrough, and images extensions from the bridge styling module
  * Added missing dependency on the tables extension to the standalone styling module
* **JEWEL-989** Fixed `plugin.xml` dependencies for the Markdown styling modules — both standalone and bridge ([#3197](https://github.com/JetBrains/intellij-community/pull/3197))

### Deprecated API

* **JEWEL-686** Deprecated `Banner` APIs that had a composable slot for the actions. Migrate to the versions with `linkActions` and `iconActions` parameters ([#3124](https://github.com/JetBrains/intellij-community/pull/3124))
* **JEWEL-920** Deprecated several APIs in `Menu.kt` that were left public by mistake so they can be made private as they should, in the future ([#3161](https://github.com/JetBrains/intellij-community/pull/3161))
* **JEWEL-948** Deprecated `LazyListState`- and `LazyGridState`-based APIs for scrollable containers as they can be trivially migrated to the new `ScrollableState`-based APIs ([#3166](https://github.com/JetBrains/intellij-community/pull/3166))
* **JEWEL-949** Deprecated all `MarkdownBlockRenderer.render` APIs in favour of the new APIs with better naming, and no `onTextClick` parameter ([#3162](https://github.com/JetBrains/intellij-community/pull/3162))
* **JEWEL-949** Deprecated `Markdown` and `LazyMarkdown` overloads with the `onTextClick` parameter ([#3162](https://github.com/JetBrains/intellij-community/pull/3162))
* **JEWEL-985** Deprecated `LocalMenuManager` as `MenuManager` is also deprecated ([#3193](https://github.com/JetBrains/intellij-community/pull/3193))

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
