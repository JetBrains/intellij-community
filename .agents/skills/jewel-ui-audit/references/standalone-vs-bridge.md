# Standalone vs IJPL Bridge: Jewel API Usage

Use when reviewing how a Jewel surface resolves theming and uses Jewel APIs, and to decide whether a fix must use
standalone or Swing/LaF-bridge mechanisms.

## Scope and boundary

This reference covers **Jewel-specific and bridge-specific API usage**. It flags choosing the wrong theming source for
the runtime context, reaching around Jewel to raw Swing from Compose, and using the wrong styling or theme provider.

It does **not** cover general Compose correctness (`LaunchedEffect` keys, recomposition, lazy-list keys, state
hoisting, modifier order, composition-vs.-presenter boundaries). Those belong to `compose-ui-audit`. If you spot a
generic Compose defect, note it in one line and route it to `compose-ui-audit` rather than expanding it here.

## Determine the context first

- **Standalone Jewel**: a Compose Desktop application using Jewel with no IntelliJ IDE underneath. Theming uses the
  int-ui-standalone styling and palette (e.g., an `IntUiTheme`-style provider). There is no Swing Look-and-Feel to
  bridge. Standalone Jewel ships only New UI/Int UI styling — it does not include the old pre-Int-UI themes.
- **IJPL plugin/Swing-LaF bridge** embeds Jewel UI in an IntelliJ-based IDE (such as a tool window, editor tab, or
  dialog). Theming bridges the IDE's Swing LaF so the Compose UI matches the surrounding IDE and tracks theme switches.

Bridge-context signals include a dependency on the IntelliJ platform or `ide-laf-bridge`, `SwingBridgeTheme`,
`JewelComposePanel` or Swing interop wrappers, `FileEditor` or tool-window hosting, `retrieveColor*` helpers, and IDE
`AllIconsKeys`. Standalone signals include a `main()` with `application {}`, standalone `int-ui` styling providers,
and no platform dependency; if the context is ambiguous, state which one you assumed.

## Theme generations (the design target)

In the IDE/bridge context there are three theme generations, and they are not equal targets:

- **Old UI (pre-Int-UI)**: e.g., the classic `IntelliJ` (light) and `Darcula` themes. Effectively deprecated. Do NOT
  treat as the default design target; support is best-effort at most. They have a different palette shape and are
  missing many newer keys. Jewel standalone does not include them at all.
- **New UI = Int UI**: the current default target (`expUI_light`/`expUI_dark` and variants). This is what to design
  against.
- **Islands**: variations on Int UI that render IDE tool windows as rounded "islands" and differ in a few components
  (toggles, tabs). Islands is the default in recent IJPL and builds on Int UI. Use island-aware keys for tool-window
  backgrounds so they render cleanly in both plain Int UI and Islands.

Detecting the generation in code (cite, don't guess):

- New UI: `NewUI.isEnabled` (outside composition) or `JewelTheme.newUiChecker.isNewUi()` (in composition; note the
  lowercase `i` — it is `isNewUi()` on the `NewUiChecker` interface). `ExperimentalUI.isNewUI()` is the underlying
  platform check (uppercase `UI`), but it is explicitly **not public API** — its own KDoc says to use
  `NewUI.isEnabled()` for plugin development, so prefer that.
- Islands: there is no Jewel-native check. The platform exposes `IslandsState.isEnabled` (and `isCustomEnabled` for
  third-party island themes), implemented around `JBUI.getInt("Islands", 0)` and the custom-islands advanced setting (
  see `com.intellij.openapi.application.impl.islands.IslandsUICustomization`). Islands is only possible under New UI: if
  New UI is off, it is definitely not Islands.
- Supporting Islands tool-window backgrounds:
  <https://plugins.jetbrains.com/docs/intellij/supporting-islands-theme.html>

The public theme definitions are the citable source of truth for which keys exist per generation (old UI `intellijlaf`/
`darcula`, Int UI `expUI/*`, Islands `islands/*`, plus `HighContrast`), under `platform/platform-resources/src/themes/`
in intellij-community. Their companion editor color schemes are the `.xml` files in the same directories.

## Color sourcing per context

| Context         | Correct color source                                                                                                                                                                                                       | Misuse to flag                                                                                                                             |
|-----------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------|
| Standalone      | Jewel global colors/int-ui palette entry, or a value derived from them                                                                                                                                                     | Literal `Color(...)`/`Color.X`; pulling IDE LaF keys that do not exist standalone                                                          |
| Bridge (plugin) | An IDE LaF key resolved through the bridge: `retrieveColorOrUnspecified("Key")`, `retrieveColorOrNull("Key")`, `retrieveColor(key, default)`, `retrieveColorsOrUnspecified(...)`; or a Jewel global that is itself bridged | Literal colors; standalone-only palette assumptions; reaching past the bridge to raw `UIManager.getColor(...)`/`JBColor` from Compose code |

In the bridge context, prefer an existing bridged Jewel global or component color when one covers the role. Drop to a
raw `retrieveColor*("LaFKey")` only when no bridged token exists, and name the LaF key. Because bridge helpers return
`Color.Unspecified` or `null` when a key is missing, check the result.

## Theme/styling provider per context

- Use the **standalone** styling/theme provider in standalone apps and the **bridge** styling/theme provider in plugins.
  Mixing them (e.g., a standalone Markdown styling in a plugin, or a bridge theme entry point in a standalone `main`) is
  an API-usage finding.
- Markdown specifically has parallel standalone and bridge styling factories; a plugin should use the bridge one so
  headings/code/links track the IDE theme. (See the `jewel-markdown` skill for the Markdown styling/extension details;
  this skill only flags the standalone/bridge mismatch.)
- For component/theme catalog questions (which provider, which component, icon APIs), defer to the `jewel-ui` skill; for
  embedding Compose into Swing (`ComposePanel`, tool-window tabs, compositing), defer to `jewel-swing-interop`. Use this
  skill to judge whether the existing choice is correct for the context.

## Hard rules when reading from the LaF

These rules apply to the bridge context and to **plugin UI that runs standalone** in UI unit tests, previews, or shared
Compose modules where no IJPL LaF exists. Safe LaF reads keep the same composable working both across custom themes and
with no LaF underneath.

1. **Always provide a fallback for everything read from the LaF, not only colors.** Colors, dimensions
   (`retrieveIntAsDp*`), insets (`retrieveInsetsAsPaddingValues`), corner sizes (`retrieveArcAsCornerSize*`), and other
   LaF reads can be absent in old pre-Int-UI themes, third-party themes, or standalone runs.
   - Because `*OrNull`/`*OrUnspecified` reads return `null`/`Unspecified` when missing, call `takeOrElse { default }`
     or pass an explicit default.
   - Prefer a **layered fallback**: primary semantic key → palette/secondary key → hardcoded default. Flag any LaF read
     with no fallback.
2. **Always provide both a light and a dark value.** Palette indices carry different meanings in light vs. dark, so a
   single value cannot serve both. Resolve `isDark` (e.g., `JewelTheme.isDark`) and give the light and dark variants
   explicitly; do not reuse one mode's value for the other.
3. **Standalone can hardcode defaults.** A pure standalone Jewel app has a fixed Int UI palette and no LaF to read, so
   hardcoding Int UI defaults or reading the standalone palette is sufficient. Reserve fallback chains for the
   bridge/plugin path and for plugin UI that runs without a LaF.

### The read-with-fallback trick, and why it is only safe for custom/IJ keys

Reading a LaF key with a hardcoded fallback works only for **custom/plugin-owned or IJ-specific LaF keys that do not
exist in a plain Swing LaF**. In the bridge the key resolves to the IDE value, and in standalone the read misses and
uses the fallback.

This trick is **not** safe for standard Swing LaF keys (`Panel.background`, `Button.foreground`, `Label.foreground`,
`TextField.background`). Because those keys exist in the default Swing LaF, a standalone read succeeds and returns the
Swing default instead of falling back to the Int UI value.

Use read-with-fallback only for keys that plain Swing never defines. For standard Swing keys, resolve the value through
an injected abstraction (below) instead of relying on a fallback miss.

Jewel exposes its LaF-read utilities (`retrieveColor*`, `retrieveIntAsDp*`, etc.) only in the bridge module. Any
content composable that calls those APIs cannot compile or run in a standalone, preview, or test module that omits
`ide-laf-bridge`. Flag any bridge read API inside a reusable content composable as a portability defect, even if its
fallback is complete.

For portable surfaces, use the **injected theming abstraction** below so content resolves themed values through an
interface and never calls `retrieve*` directly.

## Theming-container pattern

Rather than scattering raw `retrieve*` calls and fallbacks across composables, centralize them in a **theming-container
object**. Expose one `@Composable` getter per styled value, encapsulating the layered fallback (`primary key → palette
fallback → light/dark default`) and light/dark split, keyed to recompute on theme change.

To make content portable across bridge and standalone runs, turn that container into an **injected interface** with one
getter per themed value:

- Provide a **bridge implementation** that reads the LaF and a **standalone implementation** that returns hardcoded
  light/dark Int UI defaults.
- Supply the active implementation through a `CompositionLocal` or parameter at the wiring layer.
- Content composables read the interface and never call `retrieve*`, avoiding the standard-Swing-key hazard above.

Flag as findings:
- Flag any `retrieve*`, `JBColor`, or `UIManager` read called inline in a composable with no fallback.
- A theming value that handles color fallback but omits the adjacent dp, inset, or arc fallbacks.
- Flag any theming container that resolves only light or only dark values.

For the content-vs.-wiring layer split, the `IntUiTheme`/`SwingBridgeTheme` wrapper shape, and module dependency
hygiene, read `portability-structuring.md`.

For the catalog of bridge read helpers (`retrieveColor*`, `retrieveIntAsDp*`, `retrieveInsetsAsPaddingValues*`,
`retrieveArcAsCornerSize*`, text-style/font reads) and their safe-vs.-raw forms, read `bridge-laf-read-api.md`.

## Common bridge-specific findings

- A literal color with the themed/bridged value commented out next to it (the "themed value replaced by a literal" red
  flag) — in a plugin this also loses IDE theme-switch tracking.
- A LaF/palette read with no fallback (rule 1), or a single value used for both light and dark (rule 2).
- Flag code that hardcodes palette index assumptions or assumes a palette always exists (old 1p and many 3p themes
  define none).
  - A palette can also be *empty when `IslandsState.isEnabled()` is true*: `Islands Darcula` reports as Islands but
    ships no `gray-10` dash-scale keys in its `ExperimentalDark`/`Darcula` ancestry, so
    `ThemeColorPalette.readFromLaF()` returns empty hue lists.
  - Read colors via the safe `*OrNull` accessors (`JewelTheme.colorPalette.grayOrNull(10)`, `lookup("gray-10")`), never
    the `@Deprecated` `gray(index)`/`blue(index)` forms, which throw on a partial palette.
  - Because the same role maps to a different index in Light vs. Dark within one generation (for example, Int UI
    primary `#3574F0` is `Blue4` light and `Blue6` dark), always provide separate light and dark values (rule 2; see
    `theming-and-color.md`).
- Tool-window/background surfaces that look wrong under Islands because they use a base color instead of the
  tool-window/island-aware key.
- Hardcoded dimensions where the IDE provides scaled metrics (the bridge surface should respond to IDE zoom/DPI, and
  Islands/Int UI expose `.compact` density variants for many metrics).
- Icons loaded as raw resources instead of through the IDE icon system (`AllIconsKeys`/`IconKey`) in a plugin, so they
  do not theme or scale with the IDE.
- A surface that does not update on IDE LaF/theme changes because colors were captured once as literals rather than read
  from the bridge.

## Reporting

- State the assumed context (standalone or bridge) once, up front.
- For each color/theming finding, give the context-correct fix: a Jewel global/palette entry (standalone) or a specific
  bridged token/`retrieveColor*("LaFKey")` (bridge).
- For a generic Compose issue noticed in passing, name it in one line and route to `compose-ui-audit`; do not expand it
  here.
