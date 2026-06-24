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
whole `LazyColumn`, card feed, or row list: this causes I-beam cursor over padding/icons and lets selection bleed
across row/card boundaries. Recommend scoping selection to individual `Text`/prose runs. Also scan descendant content
for nested action controls (`Button`, `IconButton`, links, menus) even when the `SelectionContainer` is in an ancestor
composable: every nested action control needs selection disabled and arrow-pointer behavior restored, usually with
`DisableSelection`.
If one control is already protected, say so and contrast the sibling that is not (for example, "the Install button
already uses `DisableSelection`, but the More `IconButton` does not").

Do not flag broad selection for a genuine continuum of selectable prose, such as paragraphs in one long
Markdown/document run, where cross-paragraph selection is expected.

### Focus/keyboard finding with a11y overlap

If a custom focusable/clickable component lacks visible focus, keyboard reachability, or Enter/Space activation, report
the interaction problem here and use `accessibility-semantics.md` for the handoff sentence: full
accessible-name/role/state/focus-order verification belongs to `ui-accessibility`.

### Manual selected list without speed search

If a list/tree is hand-rolled with `Column`/`LazyColumn` + manual selected/clickable rows, evaluate two losses
separately: (1) selection keyboard/focus semantics, and (2) speed search/type-to-filter. For non-trivial lists, speed
search should be the default expectation unless there is a good visible alternative such as a search/type-to-filter
field. Name the modern selection containers (`SingleSelectionLazyColumn`/`MultiSelectionLazyColumn`; formerly the
now-deprecated `SelectableLazyColumn`) and still call out speed search as a separate missing affordance.

### Icon/Image `contentDescription`

Flag `contentDescription = ""`: an empty string is neither a useful accessible name nor the correct way to hide a
decorative element. Use `accessibility-semantics.md` for the full rule, including preserving correct `null` cases and
routing full accessible-name/role/state verification to `ui-accessibility`.

### Validation only via disabled "submit"

If form validity is only reflected by `enabled = isValid`, a disabled "submit" button, or a vague tooltip, flag missing
inline validation. Require field-level messages next to the offending field, `Outline.Error`/the themed error text role,
and feedback as the user types, on focus loss, or after a submit attempt. If inline errors exist only after `edited`/
`touched` flags become true, still check the initial invalid path: a disabled submit button plus tooltip leaves no
visible actionable feedback until the user guesses what to edit. Tooltips should name the exact missing/invalid field or
constraint; `Fill in all fields` is not enough.

### Async image/media loading

If image loading renders nothing while pending, flag layout shift and require reserved fixed/aspect-ratio space plus a
loading affordance. A nullable painter branch that returns before emitting `Image`, `Box`, `Spacer`, `size`, or
`aspectRatio` has not reserved space; padding on the eventual loaded `Image` does not count. If failure only logs or
leaves the image absent, flag missing broken-image/alt-text/error affordance. If text/name/bio data is bundled but the
avatar is fetched at render time, flag the offline/remote sourcing inconsistency. Prefer the platform's standard
image-loading path over ad-hoc fetch/decode code.

### Raw palette indices and throwing accessors

If code uses `JewelTheme.colorPalette.blue(index)`, `gray(index)`, or assumes `Blue4`/`Gray1` semantics, flag it and
run the full three-part checklist: (1) bare accessors are deprecated/throwing on partial palettes, so use `blueOrNull`/
`grayOrNull` plus null fallbacks; (2) index meanings differ by mode — primary blue is `Blue4` in classic light but
`Blue6` in classic dark, and `Gray1` is dark-mode unreadable as text; (3) Islands/Darcula transitional themes can report
Islands while exposing an empty Jewel palette due to key-format mismatch, so even nullable accessors may return null.
Prefer semantic role tokens when available.

### LaF reads: standard keys vs. custom keys

If a bridge surface reads standard/common Swing keys such as `Label.background`, check whether the fallback path is
actually safe: standard keys may resolve from platform defaults or wildcard rules when the code expected a miss. If a
bridge surface reads a namespaced plugin-owned key such as `MyPlugin.Badge.background`, a normal read-with-fallback is
generally valid; do not claim it is broken solely because wildcard fallback exists. When exact custom-key presence
matters, prefer `UIManager.getColor(key)` for strict LaF-default lookup; do not use `JBColor.get(key, marker)` because it
registers/returns the supplied default instead of reading LaF defaults. For custom keys, check for a companion
`META-INF/*.themeMetadata.json`-style catalog listing `ui` keys with descriptions/source ownership.

### Direct bridge/LaF reads inside composables

Treat direct LaF reads in composables like other direct IJPL access: `org.jetbrains.jewel.bridge.retrieve*`, `JBColor`,
`UIManager`, and similar calls couple content to the IDE bridge and are not portable to standalone demos, previews, or
UI tests. Move the read to bridge wiring and inject the value. If the read has semantic meaning (colors, dimensions,
insets, corner sizes), prefer a small theme-container/theme-data object that owns light/dark and missing-key fallbacks.
Do not add redundant `SwingBridgeTheme` if the hosting panel already wraps content in it.

### Resizable panels and split panes

If a tool-window pane, split layout, or resizable detail area lacks minimum sizes, flag it. The minimum must be derived
from content collision boundaries (for example, title width + action width + gaps), not a round guess like `220.dp`.
Below the floor, the surface should clip or scroll intentionally; it should not keep reflowing until controls overlap or
disappear. Do not invent additive-spacing findings when spacing is otherwise clean.

### Manual hover in IJPL bridge rows

If a bridge-hosted row/plain label/container uses `hoverable`, `collectIsHoveredAsState`, or hand-painted hover
backgrounds, check the platform equivalent. In IJPL bridge mode, `swingCompatMode` suppresses hover/pressed for many
controls, and plain rows/labels generally should not hover-highlight; adding a custom hover makes the plugin stand out.
A complete finding for this trigger must include the exception clause: **tabs, scrollbars, and icon buttons do hover in
the bridge even under `swingCompatMode`, but a plain row is not one of those exceptions.** Recommend a built-in
list/row/control when one owns the right behavior, or remove manual hover for static/plain rows.

### Experimental icon framework in shipping UI

If shipping plugin UI depends on `com.intellij.platform.icons`, `IconDesigner`, `IconModifier`, `IconManager`, or
Jewel's experimental icon-designer composables, flag unstable API usage when a normal icon would do. Do **not** call the
framework unbundled: the modules are present in the IDE; the concern is `@Internal`/`@Experimental` API stability.
Include this replacement sentence: use Jewel `IconKey` + `Icon`/`PainterHint` for Jewel UI, or classic
`AllIcons`/`IconLoader` for Swing/platform icons.

### Justified `SimpleListItem` fork

If a custom/forked list row explicitly reuses `SimpleListItem` for the row body and adds only a missing capability (for
example a disclosure chevron), do not treat `SimpleListItem` as a smell and do **not** recommend replacing the row body
with raw `Text`/a custom text row just to control spacing. Put it in **Justified as-is**: reusing `SimpleListItem`
preserves standard row styling, selection visuals, and keyboard/focus semantics while the fork supplies the missing
affordance. A TODO/issue link for retiring the fork is positive evidence.

### Hardcoded icon filename variants

If code hardcodes `_dark`, `@2x`, `_stroke`, or size-suffixed icon paths instead of referencing the base `IconKey`, flag
that it bypasses the hint/loader pipeline. For `@2x.svg`, always include the Swing-vs-Jewel divergence: the classic
Swing `IconLoader` does look for `@2x.svg`, but Jewel's `HiDpi` hint does **not** apply `@2x` to SVGs; Jewel SVG size
comes from intrinsic `width`/`height` or a `@WxH` size variant. Recommend referencing the base SVG path and shipping
variants beside it.

### Stateful/icon resource variants

If a Jewel `IconKey` or stateful painter references only a specialized variant (for example selected/disabled/dark)
without a base asset, flag the missing base. Use this exact sentence in the finding: "Specialized variants such as
`Selected`/`Disabled`/`_dark` are optional fallbacks, but the bare base icon must exist because Jewel falls through to
it at the end of the hint branch." Always report the diagnostic contrast with this exact point: a magenta image is a
Jewel-path load-failure tell, while the classic Swing `IconLoader` path often fails blank/silent.

### Long prose/full-width text

If Markdown, release notes, chat output, documentation, or multi-sentence prose uses unconstrained `fillMaxWidth()`,
flag missing line-length cap. Recommend a readable max width around 50–75 characters per line (hard cap 80 for Latin
text unless a product-specific spec says otherwise), and keep the capped prose column start-aligned in LTR layouts
unless a product-specific spec intentionally centers it.

### Raw scroll/no themed scrollbar

If overflowing content uses raw `Modifier.verticalScroll`/`horizontalScroll`, bare `LazyColumn`, or hand-rolled
scrollbar with no Jewel styled scrollbar/container, flag it. Use Jewel scrollable containers or selection/list
components with the appropriate scrollbar integration. Do not flag a genuinely bounded layout that cannot overflow.
