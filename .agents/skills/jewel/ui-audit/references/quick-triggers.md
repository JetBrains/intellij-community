# Quick Triggers for Jewel UI Audit

Use this as the first pass after establishing whether the UI is standalone Jewel or an IJPL/Swing-LaF bridge surface.
These are evidence patterns: only report a finding after you confirm the pattern exists in the provided code.

## Evidence-first rule

Before reporting any issue:

1. Identify the exact component/API/pattern in the snippet.
2. Cite or name the line/pattern that proves it is present.
3. Check the relevant trigger card below.
4. Do not assume a fix exists elsewhere unless the prompt or code shows it.
5. Do not praise a fix unless the exact API/value is visible in the snippet.

## Trigger cards

### `SelectionContainer(LazyColumn|card feed|list rows)`

Check whether selection wraps discrete rows/cards instead of text runs. Flag when one `SelectionContainer` covers a
whole `LazyColumn`, card feed, or row list. Broad wrapping shows an I-beam cursor over padding/icons and lets selection
bleed across row/card boundaries. Recommend scoping selection to individual `Text`/prose runs.

Scan descendant content for nested action controls (`Button`, `IconButton`, links, menus) even when an ancestor
composable declares the `SelectionContainer`. Every nested action control must disable selection and restore the arrow
pointer, usually with `DisableSelection`.

If one control is already protected, contrast it with the unprotected sibling (for example, "the Install button already
uses `DisableSelection`, but the More `IconButton` does not"). Do not flag broad selection for a genuine continuum of
selectable prose, such as paragraphs in one long Markdown/document run, where cross-paragraph selection is expected.

### Direct IJPL `Project` leak in `@Composable` / Lazy `CompositionLocal` minting

Flag direct parameters of `com.intellij.openapi.project.Project` on `@Composable` functions. `Project` is a heavy
IJPL platform object without guaranteed stable `equals`/`hashCode` for Compose. Passing it directly breaks Compose
stability tracking (causing recomposition churn or loops) and leaks platform coupling. Also flag minting ad-hoc
`CompositionLocal`s (such as `LocalProject`) out of laziness to bypass parameter passing.

### Icon over-tinting & layer `ColorFilter.tint` on filled icons

Flag Compose layer-level `ColorFilter.tint(...)` applied to filled, multi-color, or gradient icons. Layer tinting runs
at draw time and clobbers fill alpha channels and interior shapes to solid colors.

For monochrome or stroke-based icons, recommend Jewel's `PainterHint.Stroke(color)` (applied at SVG patch time). Flag
mixing `PainterHint.Stroke(color)` with layer `ColorFilter.tint()` because layer tinting overrides stroke recoloring
during draw. Using `Stroke` on multi-color icons requires an explicit callout and user decision.

### Mixed text style baseline alignment

Flag horizontal `Row`s that place different text styles (such as default UI font + editor code font, or different font
sizes) side-by-side with `Alignment.CenterVertically`. Vertical centering misaligns font baselines across different
font metrics. Recommend `AnnotatedString` in a single `Text` or explicit baseline alignment
(`Modifier.alignByBaseline()`/`Row(verticalAlignment = Alignment.Bottom)`), and require visual baseline verification.

### Component reinvention priority over micro-styling

Check first whether a custom component reinvents a standard Jewel layout or control before reviewing custom colors,
hover states, or micro-styling. Common examples include hand-rolling a popup/menu instead of
`PopupMenu`/`SelectableMenuItem`, or using a plain `LazyColumn` + manual selection state instead of
`*SelectableLazyColumn`.

### Instant IDE list scrolling vs. animated smooth scrolling

Do not claim instant step jumps (`scrollToItem`) during keyboard navigation in IDE lists are "laggy". IDE lists use
instant step jumps to match desktop platform conventions and provide immediate feedback. Do not recommend smooth
animated scrolling (`animateScrollToItem`) for list keyboard navigation.

### Focus/keyboard finding with a11y overlap

If a custom focusable/clickable component lacks visible focus, keyboard reachability, or Enter/Space activation, report
the interaction problem here and use `accessibility-semantics.md` for the handoff sentence: full
accessible-name/role/state/focus-order verification belongs to `ui-accessibility`.

### Manual selected list without speed search

If a list or tree is hand-rolled with `Column`/`LazyColumn` + manual selected/clickable rows, evaluate two losses
separately: (1) selection keyboard/focus semantics, and (2) speed search/type-to-filter.

For non-trivial lists, speed search is the default expectation unless a visible search or type-to-filter field exists.
Name the modern selection containers (`SingleSelectionLazyColumn`/`MultiSelectionLazyColumn`, replacing the deprecated
`SelectableLazyColumn`) and call out speed search as a separate missing affordance.

### Icon/Image `contentDescription`

Flag `contentDescription = ""`: an empty string is neither a useful accessible name nor the correct way to hide a
decorative element. Use `accessibility-semantics.md` for the full rule, including preserving correct `null` cases and
routing full accessible-name/role/state verification to `ui-accessibility`.

### Validation only via disabled "submit"

If form validity appears only through `enabled = isValid`, a disabled "submit" button, or a vague tooltip, flag missing
inline validation. Require field-level messages next to the offending field, `Outline.Error`/the themed error text
role, and feedback as the user types, on focus loss, or after a submit attempt.

If inline errors appear only after `edited`/`touched` flags become true, still check the initial invalid path. A
disabled submit button plus tooltip gives no visible actionable feedback until the user guesses what to edit. Tooltips
must name the exact missing/invalid field or constraint; `Fill in all fields` is not enough.

### Async image/media loading

If image loading renders nothing while pending, flag layout shift and require reserved fixed/aspect-ratio space plus a
loading affordance. A nullable painter branch that returns before emitting `Image`, `Box`, `Spacer`, `size`, or
`aspectRatio` has not reserved space; padding on the loaded `Image` does not count.

If failure only logs or leaves the image absent, flag the missing broken-image/alt-text/error affordance. If
text/name/bio data is bundled while the avatar is fetched at render time, flag the offline/remote sourcing
inconsistency. Prefer the platform's standard image-loading path over ad-hoc fetch/decode code.

### Raw palette indices and throwing accessors

If code uses `JewelTheme.colorPalette.blue(index)`, `gray(index)`, or assumes `Blue4`/`Gray1` semantics, flag it and
run the full three-part checklist. Prefer semantic role tokens when available:

1. Bare accessors are deprecated and throw on partial palettes; use `blueOrNull`/`grayOrNull` plus null fallbacks.
2. Index meanings differ by mode: primary blue is `Blue4` in classic light but `Blue6` in classic dark, and `Gray1` is
   unreadable as dark-mode text.
3. Islands/Darcula transitional themes can report Islands while exposing an empty Jewel palette due to key-format
   mismatch, so nullable accessors can still return null.

### LaF reads: standard keys vs. custom keys

If a bridge surface reads standard Swing keys such as `Label.background`, check whether the fallback path is safe.
Standard keys can resolve from platform defaults or wildcard rules when the code expected a miss.

For a namespaced plugin-owned key such as `MyPlugin.Badge.background`, a normal read-with-fallback is valid; do not
flag it solely because wildcard fallback exists. When exact custom-key presence matters, prefer
`UIManager.getColor(key)` for strict LaF-default lookup. Do not use `JBColor.get(key, marker)` because it registers
and returns the supplied default instead of reading LaF defaults.

For custom keys, check for a companion `META-INF/*.themeMetadata.json` catalog listing `ui` keys with descriptions and
source ownership.

### Direct bridge/LaF reads inside composables

Treat direct LaF reads in composables like other direct IJPL access. Calls to `org.jetbrains.jewel.bridge.retrieve*`,
`JBColor`, and `UIManager` couple content to the IDE bridge and break standalone demos, previews, and UI tests.

Move the read to bridge wiring and inject the value. For semantic values (colors, dimensions, insets, corner sizes),
use a small theme-container or theme-data object that owns light/dark and missing-key fallbacks. Do not add redundant
`SwingBridgeTheme` if the hosting panel already wraps content in it.

### Resizable panels and split panes

If a tool-window pane, split layout, or resizable detail area lacks minimum sizes, flag it. Derive the minimum from
content collision boundaries (for example, title width + action width + gaps), not a round guess like `220.dp`.

Below the floor, the surface must clip or scroll intentionally rather than reflowing until controls overlap or
disappear. Do not invent additive-spacing findings when spacing is otherwise clean.

### Manual hover in IJPL bridge rows

If a bridge-hosted row, plain label, or container uses `hoverable`, `collectIsHoveredAsState`, or hand-painted hover
backgrounds, check the platform equivalent. In IJPL bridge mode, `swingCompatMode` suppresses hover/pressed states for
many controls, and plain rows/labels do not hover-highlight.

Include this exception clause in the finding: **tabs, scrollbars, and icon buttons do hover in the bridge even under
`swingCompatMode`, but a plain row is not one of those exceptions.** Recommend a built-in list/row/control when one
owns the behavior, or remove manual hover for static/plain rows.

### Experimental icon framework in shipping UI

If shipping plugin UI depends on `com.intellij.platform.icons`, `IconDesigner`, `IconModifier`, `IconManager`, or
Jewel's experimental icon-designer composables, flag unstable API usage when a normal icon works.

Do **not** call the framework unbundled: the modules ship in the IDE, and the risk is `@Internal`/`@Experimental` API
stability. Include this replacement sentence: use Jewel `IconKey` + `Icon`/`PainterHint` for Jewel UI, or classic
`AllIcons`/`IconLoader` for Swing/platform icons.

### Justified `SimpleListItem` fork

If a custom or forked list row reuses `SimpleListItem` for the row body and adds only a missing capability (such as a
disclosure chevron), do not flag `SimpleListItem` as a smell. Do **not** recommend replacing the row body with raw
`Text` or a custom text row to control spacing.

Put it in **Justified as-is**: reusing `SimpleListItem` preserves standard row styling, selection visuals, and
keyboard/focus semantics while the fork supplies the missing affordance. A TODO or issue link for retiring the fork is
positive evidence.

### Hardcoded icon filename variants

If code hardcodes `_dark`, `@2x`, `_stroke`, or size-suffixed icon paths instead of referencing the base `IconKey`, flag
that it bypasses the hint/loader pipeline. Recommend referencing the base SVG path and shipping variants beside it.

For `@2x.svg`, always include the Swing-vs-Jewel divergence: the classic Swing `IconLoader` looks for `@2x.svg`, but
Jewel's `HiDpi` hint does **not** apply `@2x` to SVGs. Jewel SVG size comes from intrinsic `width`/`height` or a `@WxH`
size variant.

### Stateful/icon resource variants

If a Jewel `IconKey` or stateful painter references only a specialized variant (for example selected/disabled/dark)
without a base asset, flag the missing base. Include this exact sentence in the finding:

"Specialized variants such as `Selected`/`Disabled`/`_dark` are optional fallbacks, but the bare base icon must exist
because Jewel falls through to it at the end of the hint branch."

Always report the diagnostic contrast with this exact point: a magenta image is a Jewel-path load-failure tell, while
the classic Swing `IconLoader` path often fails blank/silent.

### Long prose/full-width text

If Markdown, release notes, chat output, documentation, or multi-sentence prose uses unconstrained `fillMaxWidth()`,
flag the missing line-length cap.

Recommend a readable max width around 50–75 characters per line (hard cap 80 for Latin text unless a product-specific
spec says otherwise). Keep the capped prose column start-aligned in LTR layouts unless a product-specific spec
intentionally centers it.

### Raw scroll/no themed scrollbar

If overflowing content uses raw `Modifier.verticalScroll`/`horizontalScroll`, bare `LazyColumn`, or hand-rolled
scrollbar with no Jewel styled scrollbar/container, flag it. Use Jewel scrollable containers or selection/list
components with the appropriate scrollbar integration. Do not flag a genuinely bounded layout that cannot overflow.
