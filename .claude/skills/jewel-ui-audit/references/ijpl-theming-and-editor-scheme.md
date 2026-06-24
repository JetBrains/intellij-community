# IJPL Theming Engine and Editor Color Scheme

Use in the IJPL plugin/Swing-LaF bridge context. IntelliJ has **two independent theming systems**; conflating them or
mishandling their change events and color laziness causes recurring bugs in Compose/Jewel-on-bridge UI. This reference
maps those platform hazards.

## Two systems, not one

1. **UI Look-and-Feel (Swing LaF/theme)** — drives component chrome: backgrounds, borders, controls, selection. Values
   come from LaF keys (the `*.theme.json` files), read in Jewel via the bridge `retrieve*` helpers and `JBColor`/
   `UIManager`. Changes are announced by `LafManagerListener`/`LafManager` (and `UISettingsListener` for related UI
   settings).
2. The **editor color scheme** drives editor and syntax content through `EditorColorsScheme` (addressed by `ColorKey`
   and `TextAttributesKey`) and is managed by `EditorColorsManager` (`getGlobalScheme()`,
   `getSchemeForCurrentUITheme()`). Changes are announced on `EditorColorsManager.TOPIC` via `EditorColorsListener`.

These change **independently**: the user can switch the editor scheme without switching the IDE theme, and vice versa.
`SwingBridgeReader` combines a LaF-change flow and an `EditorColorsManager.TOPIC` flow, recomputing theme data when
either fires. Code that caches derived theme or editor values must listen to the matching source(s).

## What to flag

### Mixing the two systems

- Pulling editor/syntax colors (code background, syntax foregrounds) from **UI LaF keys** instead of the
  `EditorColorsScheme` (`ColorKey`/`TextAttributesKey`), or vice versa. Markdown code blocks, diff views, and any
  editor-like surface should source from the editor scheme (`retrieveEditorColorScheme()`/`retrieveEditorTextStyle()`
  in the bridge), not from `Panel.background`-style LaF UI keys.
- Assuming the editor scheme's dark/light matches the UI theme's. They can diverge (light IDE + dark editor is a real,
  odd configuration). Use `EditorColorsManager.isDarkEditor()`/the scheme's own background to decide editor-content
  contrast, not `JewelTheme.isDark`.

### Change events

- Flag caching a resolved color or scheme value (in a field, `remember`, or `StateFlow`) without subscribing to the
  matching change topic, which leaves the value stale on theme or scheme switches. UI values must listen to
  `LafManagerListener`/`UISettingsListener`, editor values must listen to `EditorColorsManager.TOPIC`, and a surface
  that uses both must observe both.
- In Compose, a `remember { … }` or `derivedStateOf` wrapping a themed read (a bridge `retrieve*`/`JBColor` read, or a
  standalone `JewelTheme` read) without a theme-sensitive key:
  - `remember` re-runs its block only when a key changes. Without a theme key, it freezes the value from the first
    composition across later theme switches.
  - This applies to both bridge and standalone Jewel (where values can freeze across a light/dark flip).

Prefer keying on the **actual themed values the block consumes** (`JewelTheme.globalColors`, the specific style object,
`JewelTheme.isDark`, or the resolved editor scheme) so it recomputes only when its inputs change.

Use `remember(JewelTheme.instanceUuid) { … }` as the **catch-all fallback**. `BaseJewelTheme` mints a fresh
`instanceUuid` (`remember(theme) { UUID.randomUUID() }`) whenever `ThemeDefinition` changes on standalone and bridge
paths.

On the bridge, `SwingBridgeReader` rebuilds `ThemeDefinition` when the LaF flow, UI settings, or editor-scheme topic
changes (`BridgeTypography` uses this pattern). Because `instanceUuid` also invalidates pure-LaF reads on editor-scheme
switches, narrow the key when recomputation cost matters.

Do **not** key on `JewelTheme.name`. The name can stay unchanged across a theme hot-update or between two themes sharing
a display name, silently missing updates. Flag any themed read inside `remember`/`derivedStateOf` with no theme-derived
key or keyed only on `name`.

### `JBColor` laziness and capture

- `JBColor` is **lazy/dynamic**, resolving its `Color` on each access from the current theme (`JBColor.lazy { }`,
  `JBColor.namedColor(...)`, `JBColor.isBright()`), while `JBColor.namedColorOrNull` returns null when missing.
  - Capturing a `JBColor` into a plain `androidx.compose.ui.graphics.Color` (via `.toComposeColor()`) freezes it at the
    current theme. Flag one-time `.toComposeColor()` snapshots stored across theme changes.
- Reading a `JBColor` for a key that may not exist without handling null/marker. The default named-color path can
  wildcard-match (`*`) keys; for plugin-owned keys that should be "absent when undefined", use a strict read (see the
  strict-key note in `bridge-laf-read-api.md`).

### Scheme delegation

- Editing or caching attributes off `getGlobalScheme()` directly when a per-editor delegate scheme is in play. Schemes
  can be delegated/overlaid (font size, per-editor overrides); derive from the right scheme instance rather than
  assuming the global one.

## Standalone caveat

Pure standalone Jewel apps use the Int UI palette directly and have no LaF or `EditorColorsManager`. Keep editor-scheme
sourcing behind the bridge path with a hardcoded standalone fallback so shared plugin UI runs in standalone UI tests.
Use contributors to abstract direct editor-scheme access out of composition.

## Cross-references

- Read helper catalog and fallback forms: `bridge-laf-read-api.md`.
- Standalone-vs-bridge model and the theming-container pattern: `standalone-vs-bridge.md`.
- Code highlighting specifically (the `CodeHighlighter` and `ProvideMarkdownStyling` wiring) is a `jewel-markdown`
  concern; cross-check there rather than re-deriving it here.
