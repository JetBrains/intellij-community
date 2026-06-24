# Standalone vs IJPL Bridge: Jewel API Usage

Use when reviewing how a Jewel surface obtains theming and uses Jewel APIs, and to decide whether a fix should use
standalone or Swing/LaF-bridge mechanisms.

## Scope and boundary

This reference covers **Jewel-specific and bridge-specific API usage**. It does **not** cover general Compose
correctness — effects/`LaunchedEffect` keys, recomposition, lazy-list keys, state hoisting, modifier order,
composition-vs.-presenter boundaries. Those belong to the `compose-ui-audit` skill. Cross-check there, but do not
re-litigate generic Compose blunders here; if you spot one, note it briefly and route it to `compose-ui-audit` rather
than expanding it.

What is in scope here: choosing the wrong theming source for the runtime context, reaching around Jewel to raw Swing
from Compose, using the wrong styling/theme provider, and similar Jewel/bridge-shaped mistakes.

## Determine the context first

- **Standalone Jewel**: a Compose Desktop application using Jewel with no IntelliJ IDE underneath. Theming uses the
  int-ui-standalone styling and palette (e.g., an `IntUiTheme`-style provider). There is no Swing Look-and-Feel to
  bridge. Standalone Jewel ships only New UI/Int UI styling — it does not include the old pre-Int-UI themes.
- **IJPL plugin/Swing-LaF bridge**: Jewel UI embedded in an IntelliJ-based IDE (tool window, editor tab, dialog).
  Theming bridges the IDE's Swing LaF so the Compose UI matches the surrounding IDE and tracks theme switches.

Signals of the bridge context: dependency on the IntelliJ platform/`ide-laf-bridge`, use of `SwingBridgeTheme`,
`JewelComposePanel`/Swing interop wrappers, `FileEditor`/tool-window hosting, `retrieveColor*` helpers, IDE
`AllIconsKeys`. Signals of standalone: a `main()` with `application {}`, standalone int-ui styling providers, no
platform dependency. If genuinely ambiguous, state which you assumed.

## Theme generations (the design target)

In the IDE/bridge context there are three theme generations, and they are not equal targets:

- **Old UI (pre-Int-UI)**: e.g., the classic `IntelliJ` (light) and `Darcula` themes. Effectively deprecated. Do NOT
  treat as the default design target; support is best-effort at most. They have a different palette shape and are
  missing many newer keys. Jewel standalone does not include them at all.
- **New UI = Int UI**: the current default target (`expUI_light`/`expUI_dark` and variants). This is what to design
  against.
- **Islands**: variations on Int UI that also follow the Int UI design system, but render IDE tool windows as rounded
  "islands" and differ in a few components (e.g., toggles, tabs). Default in recent IJPL, and built on top of Int UI.
  The main thing to get right is that tool-window backgrounds use the correct keys so they look right in both plain Int
  UI and Islands.

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

In the bridge context, prefer the existing bridged Jewel global/component color when one exists for the role; drop to a
raw `retrieveColor*("LaFKey")` only when no bridged token covers it, and name the LaF key. The bridge helpers return
`Color.Unspecified`/null when a key is missing, so check the result rather than assuming the key resolved.

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

These apply to the bridge context, but the reason they matter is broader: **plugin UI is frequently run standalone** —
in UI unit tests, previews, or shared Compose modules — where the IJPL LaF does not exist at all. So robust LaF reads
are not just about untested themes; they are about the same composable running with no LaF underneath.

1. **Always provide a fallback for everything read from the LaF — not just colors.** Colors, dimensions
   (`retrieveIntAsDp*`), insets (`retrieveInsetsAsPaddingValues`), corner sizes (`retrieveArcAsCornerSize*`), and any
   other LaF read can be absent. Old (pre-Int-UI) 1p themes have no Int-UI palette, many 3p themes are missing keys, and
   in standalone there is no LaF at all. The `*OrNull`/`*OrUnspecified` reads return null/`Unspecified` when missing, so
   code must `takeOrElse { default }` or pass an explicit default. Prefer a **layered fallback**: primary semantic key →
   a palette/secondary key → a hardcoded default. Flag any LaF read with no fallback.
2. **Always provide both a light and a dark value.** Palette indices carry different meanings in light vs. dark, so a
   single value cannot serve both. Resolve `isDark` (e.g., `JewelTheme.isDark`) and give the light and dark variants
   explicitly; do not reuse one mode's value for the other.
3. **Standalone can usually just hardcode.** A pure standalone Jewel app has a known, fixed Int UI palette and no LaF to
   read, so hardcoding the Int UI default values (or reading the standalone palette) is fine and simpler. The fallback
   machinery is mainly for the bridge/plugin path and for plugin UI that may run without a LaF.

### The read-with-fallback trick, and why it is only safe for custom/IJ keys

A reusable composable can *sometimes* stay portable by reading a LaF key with a hardcoded fallback: in the bridge the
key resolves to the IDE value, and standalone the read misses and drops to the fallback. This works — **but only for
custom/plugin-owned or IJ-specific LaF keys that do not exist in a plain Swing LaF.** It is **not** safe for standard
Swing LaF keys (e.g., `Panel.background`, `Button.foreground`, `Label.foreground`, `TextField.background`, and the
like): those keys *are* present in the default Swing LaF that a standalone app runs under, so the read succeeds and
returns the Swing default instead of missing and falling back to your intended Int UI value. The result is a silently
wrong non-Int-UI color standalone, with no error to signal it. So: read-with-fallback is viable for keys the Swing LaF
has never heard of; for standard Swing keys, do not rely on the fallback — resolve the value through
an injected abstraction (below) instead.

Underlying all of this: **Jewel exposes its LaF-read utilities (`retrieveColor*`, `retrieveIntAsDp*`, etc.) only in the
bridge module.** Any content composable that references those APIs is, by construction, not portable — it cannot compile
or run in a standalone/preview/test context that does not depend on `ide-laf-bridge`. Treat a bridge read API appearing
in a would-be-reusable content composable as the portability smell itself, independent of whether its fallback is
correct.

The robust, general fix is not the custom-key shortcut but the **injected theming abstraction** described in the
next section — resolve themed values through an interface, so content never calls `retrieve*` at all.

## Theming-container pattern

Rather than scattering raw `retrieve*` calls (and their fallbacks) through composables, centralize them in a
**theming-container object**: a single object exposing one `@Composable` getter per styled value, each encapsulating the
layered fallback (`primary key → palette fallback → light/dark default`) and the light/dark split, keyed so it
recomputes on theme change. This keeps call sites clean and gives one place to audit theme correctness.

Taken one step further, make that container an **injected interface** rather than a single hardcoded object: one getter
per themed value, a **bridge implementation** that reads the LaF, and a **standalone implementation** that returns
hardcoded light/dark Int UI defaults, provided to content through a `CompositionLocal` (or parameter) by the wiring
layer. Content composables consume the interface and never mention a `retrieve*` call, so the same code runs in the
plugin and standalone — this is what makes genuinely portable content possible, and the answer to the standard-Swing-key
hazard above.

Flag as findings: a `retrieve*` (or `JBColor`/`UIManager`) read inline in a composable with no fallback; a theming value
that handles color fallback but not the dp/inset/arc reads next to it; a container that resolves only one of light/dark.

For how to take this further — the content-vs.-wiring layer split, the `IntUiTheme`/`SwingBridgeTheme` wrapper shape,
and module/dependency hygiene — read `portability-structuring.md`.

For the exact catalog of bridge read helpers (`retrieveColor*`, `retrieveIntAsDp*`, `retrieveInsetsAsPaddingValues*`,
`retrieveArcAsCornerSize*`, text-style/font reads) and their safe-vs.-raw forms, read `bridge-laf-read-api.md`.

## Common bridge-specific findings

- A literal color with the themed/bridged value commented out next to it (the "themed value replaced by a literal" red
  flag) — in a plugin this also loses IDE theme-switch tracking.
- A LaF/palette read with no fallback (rule 1), or a single value used for both light and dark (rule 2).
- Hardcoded palette index assumptions, or assuming a palette exists at all (old 1p and many 3p themes have none). Note
  also that a palette can be *empty even when `IslandsState.isEnabled()` is true*: `Islands Darcula` reports as Islands
  but ships no `gray-10`-style dash-scale keys (nor does its `ExperimentalDark`/`Darcula` ancestry), so
  `ThemeColorPalette.readFromLaF()` returns empty hue lists — read colors via the safe `*OrNull` accessors
  (`JewelTheme.colorPalette.grayOrNull(10)`, `lookup("gray-10")`), never the `@Deprecated` `gray(index)`/`blue(index)`
  forms, which throw on a partial palette. The same role also rides a different index in Light vs. Dark within one
  generation (e.g., Int UI primary `#3574F0` = `Blue4` light/`Blue6` dark), which is why rule 2 (separate light/dark
  values) is non-negotiable. See `theming-and-color.md`.
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
