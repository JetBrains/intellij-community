# IJPL Theming Engine and Editor Color Scheme

Use in the IJPL plugin/Swing-LaF bridge context. IntelliJ has **two independent theming systems**, and conflating
them — or mishandling their change events and color laziness — is a recurring source of bugs in Compose/Jewel-on-bridge
UI. This is grounded in the platform sources; it is a hazard reference, not a full API tour.

## Two systems, not one

1. **UI Look-and-Feel (Swing LaF/theme)** — drives component chrome: backgrounds, borders, controls, selection. Values
   come from LaF keys (the `*.theme.json` files), read in Jewel via the bridge `retrieve*` helpers and `JBColor`/
   `UIManager`. Changes are announced by `LafManagerListener`/`LafManager` (and `UISettingsListener` for related UI
   settings).
2. **Editor color scheme** — drives editor/syntax content: `EditorColorsScheme`, addressed by `ColorKey` and
   `TextAttributesKey`, managed by `EditorColorsManager` (`getGlobalScheme()`, `getSchemeForCurrentUITheme()`). Changes
   are announced on `EditorColorsManager.TOPIC` via `EditorColorsListener`.

These change **independently**: the user can switch the editor scheme without switching the IDE theme, and vice versa.
The Jewel bridge itself reflects this — `SwingBridgeReader` `combine`s a LaF-change flow *and* an
`EditorColorsManager.TOPIC` flow, recomputing theme data when either fires. Any code that caches derived theme/editor
values must therefore listen to the right source(s); listening to only one and assuming the other follows is a bug.

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

- Caching a resolved color/scheme value (in a field, `remember`, or `StateFlow`) without subscribing to the matching
  change topic, so it goes stale on theme/scheme switch. UI values → `LafManagerListener`/`UISettingsListener`; editor
  values → `EditorColorsManager.TOPIC`. A surface that needs both must observe both.
- In Compose, a `remember { … }` (or `derivedStateOf`) wrapping a themed read — a bridge `retrieve*`/`JBColor` read, or
  even a standalone read off `JewelTheme` — whose key list does **not** include a value that changes when the theme
  does. `remember` only re-runs its block when a key changes, so with no such key it captures the value from the first
  composition and keeps it; a later theme switch repaints the tree but the block is not re-executed, so the stale value
  persists. This is a foundation-level Jewel concern, **not** bridge-specific — it applies to standalone Jewel too,
  where e.g., a value gets cached across a light/dark flip.

Prefer keying on the **actual themed values the block consumes** (e.g., `JewelTheme.globalColors`, the specific style
object, `JewelTheme.isDark`, or the resolved editor scheme), so it recomputes exactly when its real inputs change and
no more. `remember(JewelTheme.instanceUuid) { … }` is the **catch-all fallback**: `instanceUuid` is minted fresh
(`remember(theme) { UUID.randomUUID() }` in `BaseJewelTheme`, on both the standalone and bridge paths) whenever the
`ThemeDefinition` changes. On the bridge that definition is rebuilt when **anything** the bridge tracks changes — it
`combine`s the LaF-change flow, UI settings, and the editor-scheme topic (`SwingBridgeReader`) — so `instanceUuid`
never misses an update, but it also re-runs the block on changes the block may not care about (e.g., an editor-scheme
switch invalidating a pure-LaF read). Use it when enumerating the block's real inputs is impractical; narrow the key
when recomputation cost matters. The bridge itself uses the `instanceUuid` pattern for its text styles
(`BridgeTypography`).

Do **not** key on `JewelTheme.name`: the name can stay the same across a real change — a theme hot-update that swaps
the LaF under an unchanged name, or two distinct themes sharing a display name — so it silently misses updates. Flag a
themed read inside `remember`/`derivedStateOf` with no theme-derived key, and flag one keyed only on `name`.

### `JBColor` laziness and capture

- `JBColor` is **lazy/dynamic**: it resolves its underlying `Color` on each access from the *current* theme
  (`JBColor.lazy { }`, `JBColor.namedColor(...)`, `JBColor.isBright()`), and `JBColor.namedColorOrNull` returns null for
  missing keys. Capturing some `JBColor` into a plain `androidx.compose.ui.graphics.Color` (e.g., via
  `.toComposeColor()`) **freezes** it at the current theme; after a theme switch that captured value is stale unless it
  was re-read on the change event. Flag a one-time `.toComposeColor()` snapshot of a dynamic color stored across theme
  changes.
- Reading a `JBColor` for a key that may not exist without handling null/marker. The default named-color path can
  wildcard-match (`*`) keys; for plugin-owned keys that should be "absent when undefined", use a strict read (see the
  strict-key note in `bridge-laf-read-api.md`).

### Scheme delegation

- Editing or caching attributes off `getGlobalScheme()` directly when a per-editor delegate scheme is in play. Schemes
  can be delegated/overlaid (font size, per-editor overrides); derive from the right scheme instance rather than
  assuming the global one.

## Standalone caveat

None of the editor-scheme or LaF machinery exists in a pure standalone Jewel app. Standalone uses the Int UI palette
directly and has no `EditorColorsManager`; do not introduce these dependencies into code that must also run standalone
(including plugin UI exercised in standalone UI tests). Keep editor-scheme sourcing behind the bridge path with a
hardcoded standalone fallback. Use contributors to abstract direct editor scheme access away from the composition.

## Cross-references

- Read helper catalog and fallback forms: `bridge-laf-read-api.md`.
- Standalone-vs-bridge model and the theming-container pattern: `standalone-vs-bridge.md`.
- Code highlighting specifically (the `CodeHighlighter` and `ProvideMarkdownStyling` wiring) is a `jewel-markdown`
  concern; cross-check there rather than re-deriving it here.
