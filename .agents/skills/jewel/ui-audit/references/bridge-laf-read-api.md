# Bridge LaF Read API Reference

Use in the IJPL plugin/Swing-LaF bridge context when reviewing how Compose/Jewel code pulls values out of the IDE
Look-and-Feel. This is a grounded catalog of the helpers that exist (from `org.jetbrains.jewel.bridge`, `ide-laf-bridge`
module) plus the fallback discipline to enforce. It is a lookup reference; the principles live in
`standalone-vs-bridge.md`.

Do not apply any of this in a standalone Jewel app — there is no Swing LaF to read. Standalone uses `IntUiTheme` /
`ThemeColorPalette`/`GlobalColors` and can hardcode Int UI defaults.

## Quick triggers

- **Raw bridge read:** `retrieveColorOrNull`/`retrieveColorOrUnspecified`/`*OrNull`/`*OrUnspecified` with no immediate
  fallback is a finding.
- **Single fallback for both modes:** prefer `retrieveColor(key, isDark, default, defaultDark)` or an explicit
  light/dark branch.
- **Standard/common Swing key fallback trap:** keys like `Label.background` may resolve from platform defaults or
  wildcard rules when the code expected absence. Check whether the fallback can actually run.
- **Exact custom-key probe:** when checking whether a specific key is directly present (such as a Studio Bot key before
  a `ColorPalette.*` fallback), use `UIManager.getColor(key)`, not `retrieveColor*` or `JBColor.get(key, marker)`.
  `retrieveColor*`/`JBColor.namedColorOrNull` can wildcard-match; `JBColor.get` registers the supplied default instead
  of reading LaF defaults.
- **Namespaced custom key:** `MyPlugin.*`-style keys with read-with-fallback are generally valid; do not claim they are
  broken solely because wildcard fallback exists. Check instead that custom keys are documented in a
  `META-INF/*.themeMetadata.json`-style catalog.
- **Bridge/LaF read in composables:** direct `org.jetbrains.jewel.bridge.retrieve*`, `JBColor`, `UIManager`, or IJPL/LaF
  reads in composables couple content to the bridge and break standalone demos, previews, and UI tests. Move reads to
  bridge wiring and inject semantic values via a theme-container object with light/dark and missing-key fallbacks.

## Read helpers and their fallback forms

Every LaF read can miss when a key is absent in old or third-party themes, or when the UI runs standalone in tests
without a LaF. Each helper family provides a raw form and a fallback form; require the fallback form unless an explicit
default follows.

Colors:

- `retrieveColorOrNull(key)`/`retrieveColorOrUnspecified(key)` — raw read; returns null/`Color.Unspecified` when
  missing. Only acceptable when followed by a `?: default`/`takeOrElse { default }`.
- `retrieveColor(key, default)` and `retrieveColor(key, isDark, default, defaultDark)` — built-in default; the `isDark`
  overload is the idiomatic way to satisfy the light+dark rule in one call.
- `retrieveColorsOrUnspecified(vararg keys)` — multiple keys at once; still unspecified-on-miss.

Dimensions (int LaF value → dp):

- `retrieveIntAsDp(key, default)` — preferred; carries a default.
- `retrieveIntAsDpOrUnspecified(key)`/`retrieveIntAsNonNegativeDpOrUnspecified(key)` — raw; require a
  `takeOrElse { ...dp }`.

Insets/padding:

- `retrieveInsetsAsPaddingValues(key, default)` — preferred.
- `retrieveInsetsAsPaddingValuesOrNull(key)` — raw; require a `?: PaddingValues(...)`.

Corner radius (arc → CornerSize):

- `retrieveArcAsCornerSizeOrDefault(key, default)`/`retrieveArcAsNonNegativeCornerSizeOrDefault(key, default)` —
  preferred.
- `retrieveArcAsCornerSize(key)`, `retrieveArcAsCornerSizeWithFallbacks(vararg keys)`, and
  `retrieveArcAsCornerSizeWithFallbacksOrNull(vararg keys)` provide single-key and multi-key lookups; follow the
  `...OrNull` overload with an explicit default.

Fonts/text styles (these resolve from the IDE and generally have platform defaults but still only exist in the
bridge):

- Use `retrieveDefaultTextStyle()`, `retrieveDefaultTextStyle(lineHeightMultiplier)`, `retrieveEditorTextStyle()`,
  `retrieveConsoleTextStyle()`, `retrievePlatformTextStyle()`, and `retrieveEditorColorScheme()` to read IDE fonts and
  schemes (`retrieveJBFont(key)` is `internal` to the bridge and unavailable to plugins).
- Note `retrieveEditorTextStyle()`/`retrieveEditorColorScheme()` read the **editor color scheme**, a system separate
  from the UI LaF with its own change topic. For editor/syntax-content surfaces and the laziness/listener hazards, read
  `ijpl-theming-and-editor-scheme.md`.

Theme identity/generation:

- Read `JewelTheme.isDark`, `JewelTheme.name`, and `JewelTheme.instanceUuid` to inspect theme identity, and pass `name`
  or `instanceUuid` as `remember` keys so cached reads recompute on theme changes.
- Call `JewelTheme.newUiChecker` (`NewUiChecker.isNewUi()`, with a lowercase `i`) inside composition, `NewUI.isEnabled`
  outside composition, and `currentUiThemeOrNull()` to access the bridge theme.
- Islands has no Jewel-native check; query the platform `IslandsState.isEnabled` or `isCustomEnabled` properties when
  New UI is enabled.

## What to flag

- A raw `*OrNull`/`*OrUnspecified` read with no `?:`/`takeOrElse` default following it.
- A color read that resolves only one of light/dark — prefer the `retrieveColor(key, isDark, default, defaultDark)`
  overload or an explicit `isDark` branch.
- A `remember`ed read not keyed on `JewelTheme.name`/`instanceUuid` (won't update on theme switch).
- Any direct LaF/platform read inside a composable (`retrieveColor*`, `retrieveIntAsDp*`, `retrieveInsets*`,
  `UIManager.getColor`, `JBColor`). Move reads to a bridge wiring layer and inject the resulting values. For semantic
  colors, dimensions, insets, or corners, use a theme-container object that owns the fallback chain and exposes semantic
  properties.
- Dimension/inset/arc reads that have a fallback for color but not for the metrics beside them — the fallback discipline
  is for all reads, not just colors.

## Theme-container boundary for semantic values

Fallbacks make reads safe; they do not by themselves make content portable. If a composable reads a LaF key directly,
that composable now depends on the bridge module and an IDE LaF. For shared content, previews, screenshot tests, and
standalone demos, the bridge layer should resolve the values and pass them in.

Use a small theme-container/theme-data object when a value has product/UI semantics, for example `mutedText`,
`badgeBackground`, `rowHoverBackground`, `detailsMinWidth`, or `chipCornerSize`. The bridge implementation reads LaF
keys with layered light/dark/missing-key fallbacks; the standalone implementation supplies Int UI defaults. Content then
uses semantic fields and never calls `retrieve*`, `JBColor`, or `UIManager` directly.

Report inline LaF reads in composables, especially multiple reads or reused values, and recommend moving the LaF reads
to a bridge theme container/wiring layer to inject semantic values into content. Do not require a container for a
one-off value without reuse or semantic identity, but keep bridge reads out of portable content.

## Strict key lookup and custom-key notes

The default bridge color read can wildcard-match (`*`) keys. `retrieveColorOrNull` delegates to
`JBColor.namedColorOrNull`, whose `calculateColorOrNull` falls back to `findPatternMatch` (`UIManager.get("*")`, suffix
match) when no direct key exists (see `theme-key-resolution.md` for how `*.` patterns enter `UIDefaults`).

This is mainly a trap for **standard/common Swing key names**. A key such as `Label.background` can resolve from
platform defaults or wildcard suffix rules when the code expected a miss to trigger its fallback. First check whether
the key is a common Swing/LaF key or a namespaced plugin-owned key.

Plugin-owned keys must be namespaced and specific (such as `MyPlugin.Badge.background`, not `Label.background` or
`Badge.background`). For custom keys, `retrieveColor(key, default)` or
`retrieveColor(key, isDark, default, defaultDark)` is valid because plain Swing LaFs omit the custom key and run the
fallback.

Do **not** claim a namespaced custom key is broken solely because wildcard fallback exists. Flag it only when a generic
suffix lets a `*.<suffix>` rule hijack the lookup, or when correctness requires distinguishing a direct definition from
a wildcard match.

When plugin code introduces custom LaF keys, check for a companion **theme metadata** file under `META-INF` (commonly
`*.themeMetadata.json`). It must name the theme family and list custom `ui` keys with `key`, `description`, and a
source/owner field. Flag undocumented custom keys when no metadata catalog exists.

**The strict-read technique** applies when direct-key presence matters: read the key with `UIManager.getColor(key)`
instead of the Jewel/`namedColor` path. `UIManager.getColor` checks the exact LaF defaults key without running
Jewel/JBColor's `*` wildcard fallback, so undefined keys return `null`.

In this exact-key-probe scenario, do **not** recommend `retrieveColorOrNull` or `retrieveColorOrUnspecified` as the fix.
Those are the normal bridge read APIs, not strict direct-key probes, because they delegate to
`JBColor.namedColorOrNull`.

Do **not** use `JBColor.get(key, JBColor.marker("SOME_SENTINEL"))` for this. In IJPL, `JBColor.get` checks JBColor's
private `defaultThemeColors` map, registers the provided default color when absent, and returns that default. A marker
default makes an existing LaF key (including `ColorPalette.*` fallback keys) look missing.

Use `UIManager.getColor` for exact key probing, then apply the layered fallback (`palette key via exact
UIManager.getColor lookup → hardcoded light/dark default`). In reusable content, move this `UIManager.getColor` lookup
to bridge wiring or a theme container and inject the resolved value.

When reporting this finding, contrast the three APIs explicitly:

- `UIManager.getColor`: strict exact-key lookup.
- Jewel `retrieveColor*`/`JBColor.namedColorOrNull`: LaF lookup plus `*` wildcard suffix matching.
- `JBColor.get(key, default)` registers and returns the supplied default in `defaultThemeColors` without performing a
  LaF lookup.

Grounding: `JBColor.namedColorOrNull` delegates through `calculateColorOrNull`, which calls `UIManager.getColor` and
then `findPatternMatch`; Jewel `retrieveColorOrNull` delegates to that path. `JBColor.get`, by contrast, only uses
`defaultThemeColors` and the supplied default.
