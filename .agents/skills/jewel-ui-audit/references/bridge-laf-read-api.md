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
- **Exact custom-key probe:** when code needs to know whether a specific key is directly present (for example a Studio
  Bot key before a `ColorPalette.*` fallback), the fix is `UIManager.getColor(key)`, not `retrieveColor*` and not
  `JBColor.get(key, marker)`. `retrieveColor*`/`JBColor.namedColorOrNull` can wildcard-match; `JBColor.get` registers
  the supplied default instead of reading LaF defaults.
- **Namespaced custom key:** `MyPlugin.*`-style keys with read-with-fallback are generally valid; do not claim they are
  broken solely because wildcard fallback exists. Check instead that custom keys are documented in a
  `META-INF/*.themeMetadata.json`-style catalog.
- **Bridge/LaF read in composables:** direct `org.jetbrains.jewel.bridge.retrieve*`, `JBColor`, `UIManager`, or other
  IJPL/LaF reads inside composables are direct IDE access. They couple content to the bridge and break standalone demos,
  previews, and UI tests; move reads to bridge wiring and inject values. If the values have semantic meaning (colors,
  dimensions, insets, corner sizes), prefer a small theme-container/theme-data object with light/dark and missing-key
  fallbacks.

## Read helpers and their fallback forms

Every read can miss (key absent in old/3p themes; no LaF at all when the same UI runs standalone in tests). Each family
has a no-fallback form and a safe form; review should require the safe form unless a hardcoded default follows.

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
- `retrieveArcAsCornerSize(key)`/`retrieveArcAsCornerSizeWithFallbacks(vararg keys)` /
  `retrieveArcAsCornerSizeWithFallbacksOrNull(vararg keys)` — multi-key/raw forms; the `...OrNull` needs a default after
  it.

Fonts/text styles (these resolve from the IDE and generally have platform defaults but still only exist in the
bridge):

- `retrieveDefaultTextStyle()`/`retrieveDefaultTextStyle(lineHeightMultiplier)`, `retrieveEditorTextStyle()`,
  `retrieveConsoleTextStyle()`, `retrievePlatformTextStyle()`, `retrieveEditorColorScheme()`. (`retrieveJBFont(key)`
  also exists but is `internal` to the bridge — not a public plugin-facing helper. You should not need it.)
- Note `retrieveEditorTextStyle()`/`retrieveEditorColorScheme()` read the **editor color scheme**, a system separate
  from the UI LaF with its own change topic. For editor/syntax-content surfaces and the laziness/listener hazards, read
  `ijpl-theming-and-editor-scheme.md`.

Theme identity/generation:

- `JewelTheme.isDark`, `JewelTheme.name`, `JewelTheme.instanceUuid` (use `name`/`instanceUuid` as `remember` keys so
  reads recompute on theme change).
- `JewelTheme.newUiChecker` (`NewUiChecker.isNewUi()` — lowercase `i`) in composition; `NewUI.isEnabled` outside.
  `currentUiThemeOrNull()` for the bridge theme.
- Islands: no Jewel-native check — platform `IslandsState.isEnabled`/`isCustomEnabled`, only meaningful when New UI is
  on.

## What to flag

- A raw `*OrNull`/`*OrUnspecified` read with no `?:`/`takeOrElse` default following it.
- A color read that resolves only one of light/dark — prefer the `retrieveColor(key, isDark, default, defaultDark)`
  overload or an explicit `isDark` branch.
- A `remember`ed read not keyed on `JewelTheme.name`/`instanceUuid` (won't update on theme switch).
- Any direct LaF/platform read inside a composable — `retrieveColor*`, `retrieveIntAsDp*`, `retrieveInsets*`, raw
  `UIManager.getColor`, `JBColor`, etc. Treat this like other direct IJPL access: it is not portable shared content.
  Move reads to a bridge wiring layer and inject the resulting values. If colors/dimensions/insets/corners carry
  semantic meaning, prefer a dedicated theme-container/theme-data object that owns the fallback chain and exposes
  semantic properties to content.
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

Report this when you see inline LaF reads in composables, especially multiple reads or values reused across components:
"move the LaF reads to a bridge theme container/wiring layer and inject semantic values into content." Do not require a
container for a one-off value with no reuse or semantic identity, but still keep bridge reads out of portable content.

## Strict key lookup and custom-key notes

The default bridge color read can do wildcard (`*`) partial matching on keys: `retrieveColorOrNull` delegates to
`JBColor.namedColorOrNull`, whose `calculateColorOrNull` falls back to `findPatternMatch` (`UIManager.get("*")`, suffix
match) when there's no direct key (see `theme-key-resolution.md` for how `*.` patterns get into `UIDefaults`). This is
mainly a trap for **standard/common Swing key names**: a key such as `Label.background` can resolve from platform
defaults or wildcard suffix rules when the code expected "not defined, use my fallback." Do not treat every fallback-ed
bridge read as broken; first ask whether the key is a common Swing/LaF key or a namespaced plugin-owned key.

Plugin-owned keys should be namespaced and specific (for example, `MyPlugin.Badge.background`, not `Label.background` or
`Badge.background`). For such custom keys, a normal `retrieveColor(key, default)`/`retrieveColor(key, isDark, default,
defaultDark)` read-with-fallback is generally valid: in plain Swing LaFs the custom key is absent, and the fallback is
what you want. Do **not** claim a namespaced custom key is broken solely because wildcard fallback exists; only flag it
when the suffix is so generic that a `*.<suffix>` rule is realistically expected to hijack the lookup, or when the code
needs to distinguish "directly defined" from "matched by wildcard" for correctness.

When plugin code introduces custom LaF keys, look for a companion **theme metadata** file under `META-INF` (commonly a
`*.themeMetadata.json` resource). The important structure is a small JSON catalog naming the theme family and listing
custom `ui` keys with at least `key`, `description`, and usually a source/owner field. This makes plugin keys
discoverable to theme authors and reviewers, documents intended value types/semantics, and helps avoid anonymous magic
strings scattered through code. Do not require an exact file name but flag undocumented custom keys when no equivalent
metadata catalog exists.

**The strict-read technique** is still useful when direct-key presence matters (public platform API, so usable in main
plugin code): read the key with `UIManager.getColor(key)` instead of the Jewel/`namedColor` path. `UIManager.getColor`
checks the exact LaF defaults key and does **not** run Jewel/JBColor's `*` wildcard fallback, so an undefined key stays
`null` rather than resolving through a suffix rule. In this narrow exact-key-probe scenario, do **not** recommend
`retrieveColorOrNull`/`retrieveColorOrUnspecified` as the fix: those are the normal bridge read APIs, but they are not
strict direct-key probes because they delegate to `JBColor.namedColorOrNull`.

Do **not** use `JBColor.get(key, JBColor.marker("SOME_SENTINEL"))` for this. In IJPL, `JBColor.get` is not a LaF-default
lookup API: it checks JBColor's private `defaultThemeColors` map, registers the provided default color when absent, and
returns that default. A marker default therefore makes an actually-present LaF key look missing, including existing
`ColorPalette.*` fallback keys. Use `UIManager.getColor` for exact key probing, then apply the real layered fallback
(`palette key via exact `UIManager.getColor` lookup → hardcoded light/dark default`). Use this for strict direct-key
probing, not as a blanket replacement for every custom-key fallback. If the code is inside reusable content, still move
this lookup to bridge wiring/a theme container and inject the resolved value; the strict lookup API remains
`UIManager.getColor` in that bridge layer.

When reporting this finding, explicitly contrast the three APIs so the fix is not ambiguous: `UIManager.getColor` means
strict exact-key lookup; Jewel `retrieveColor*`/`JBColor.namedColorOrNull` mean LaF lookup plus `*` wildcard suffix
matching; `JBColor.get(key, default)` means default registration/return and is not a LaF lookup at all.

Grounding: `JBColor.namedColorOrNull` delegates through `calculateColorOrNull`, which calls `UIManager.getColor` and then
`findPatternMatch`; Jewel `retrieveColorOrNull` delegates to that path. `JBColor.get`, by contrast, only uses
`defaultThemeColors` and the supplied default.
