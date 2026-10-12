# Theming and Color Fidelity

Use when reviewing colors, dimensions, and shapes in Jewel/Compose IDE UI.

## Quick triggers

- **Literal UI color:** `Color(0x...)`, `Color.Red`, `Color.DarkGray`, or a themed value commented out next to a literal
  is a strong theming finding.
- **Role mismatch:** border tokens used as backgrounds, foreground/text tokens used as container fills, disabled colors
  used for enabled content, or selection/focus colors used as static decoration.
- **Raw palette index:** `JewelTheme.colorPalette.blue(index)`/`gray(index)` or comments like `Blue4 is primary` are not
  portable. When you see this pattern, always check and report all three sub-issues below if they apply:
    1. bare accessors are deprecated/throwing on partial palettes, so use `blueOrNull`/`grayOrNull` plus fallbacks;
    2. index semantics differ by light/dark mode (`Blue4` is primary in classic light, `Blue6` in classic dark; `Gray1`
       is dark-mode unreadable as text);
    3. Islands/Darcula transitional themes can report Islands while exposing an empty Jewel palette due to key-format
       mismatch, so even nullable accessors may return null and need fallbacks.

Upstream authority for color roles and usage:
the [JetBrains UI Guidelines](https://plugins.jetbrains.com/docs/intellij/ui-guidelines-welcome.html), Colors section.
It is Swing-oriented, but the role semantics and theme-driven approach apply directly to Jewel. Cite it when a
color-role finding is questioned.

## Raw palette indexes need explicit fallback review

### Raw palette index checklist

Raw palette index calls are a compact smell that usually hide several independent portability bugs. If you see
`JewelTheme.colorPalette.blue(n)`, `gray(n)`, or a comment asserting that a palette index has a stable semantic meaning,
run this checklist explicitly in the review:

- **Throwing accessor:** bare `blue(n)`/`gray(n)` is deprecated and can throw when the palette is partial. The minimum
  fix is `blueOrNull(n)`/`grayOrNull(n)` plus a real fallback.
- **Mode-dependent meaning:** the same number is not a stable semantic role. Primary classic blue is `Blue4` in light
  and `Blue6` in dark; `Gray1` is a dark value and is not a safe text color in dark themes.
- **Islands empty-palette edge:** do not assume `isIslands` means the Jewel palette is populated. Transitional
  Islands/Darcula themes can expose no dash-scale palette keys to Jewel, so nullable accessors can still return null.
- **Prefer roles:** if a semantic global/component role exists for the use, prefer that over raw ramps.

A review that only says "use semantic colors" is incomplete for this pattern; name the throwing accessor and null
fallback problem too.

## Resolve every color to a theme source

In priority order, a color should come from:

1. A Jewel global color (`JewelTheme.globalColors...`) appropriate to the role.
2. A component style's color from the active Jewel styling.
3. An Int UI palette entry.
4. A Swing LaF key bridged into Compose (for parity with non-Compose IDE UI) but ONLY for IJPL Plugins. Standalone does
   not have such a luxury (see `standalone-vs-bridge.md`).

A literal `Color(0xFF...)` or named `Color.X` in UI code is a lead. It does not respond to theme switches,
custom/third-party themes, or high-contrast mode, and it will look wrong in at least one theme nobody tested.

### The "themed value is replaced by a literal" red flag

The strongest signal is a literal sitting where a themed value used to be. Example shape:

```kotlin
// lineColor = this.lineColor   // <- themed value, commented out
lineColor = Color.DarkGray      // <- hardcoded replacement
```

When you see a literal next to (or replacing) a commented-out themed expression, treat it as a definite finding: someone
overrode the theme deliberately, perhaps for debugging purposes, and lost the theme response. The fix is almost always
to restore the themed value or pick the correct themed role if the original was wrong.

## Semantic color roles must match usage

A token carries a role in its name. The usage must match the role.

| Token role        | Intended use             | Misuse to flag                                       |
|-------------------|--------------------------|------------------------------------------------------|
| `borders.*`       | 1px separators, outlines | Used as a `.background(...)` fill for a large region |
| foreground/text   | text and icon tint       | Painting a container/background                      |
| disabled/inactive | disabled controls        | Styling enabled, interactive content                 |
| selection/focus   | selected/focused state   | Static decoration unrelated to state                 |

A common real-world offender is in the shape of a border color used as a background. Border tokens are tuned to read as
thin lines and are intentionally close to the background; as a large fill they produce weak, wrong contrast, and they
diverge from the real panel/background token under custom themes (where border and background are tuned independently).
Use a content/panel background role instead. When unsure which background role is correct, that is a question for a
designer, not a guess.

## Dimensions and shapes

Magic-number dimensions (`8.dp`, `6.dp`, corner radii) are weaker findings than colors, but still worth raising when:

- the same conceptual value is duplicated across files and drifts (e.g., `RoundedCornerShape(8.dp)` in three places and
  `4.dp` in a fourth with no rationale), or
- the value gates a layout that must respond to IDE zoom/ density, and that response is unverified.

Prefer a small shared spacing/shape scale so ratios are deliberate and reviewable. Always ask explicitly: how does this
respond to IDE zoom and a custom theme?

## Principles inferable from the public theme definitions

These are observable by diffing the canonical theme files (`platform/platform-resources/src/themes/`: old `intellijlaf`/
`darcula`, Int UI `expUI/*`, Islands `islands/*`, `HighContrast`). They are not all spelled out in the prose guides:

- **The palette is not semantic.** Base palette entries are indexed by hue and lightness
  (gray/blue/green/yellow/red/orange/teal/purple ramps), not by meaning. Components must reference semantic roles, never
  a raw palette entry, because the palette carries no role intent on its own. Only Islands themes define semantic tokens
  based on the palette: see below.
- **Palette indices are not portable across theme generations** (numbering and step count differ), but the **scale
  direction is the same** in all of them: low index = darkest, high index = lightest. Verified: old `grey01`=#6e6e6e →
  `grey18`=#fafafa (18 steps); Int UI `Gray1`=#000000 → `Gray14`=#FFFFFF (14 steps); Islands `gray-10` darkest →
  `gray-160` lightest (steps of 10). So "index N" is a *different color* across generations even though the ramp runs
  the same way. Never hardcode or assume a palette index maps consistently across generations. Note: a Jewel
  `ThemeColorPalette` KDoc reportedly mislabels the gray order; the actual values run dark→light.
- **A given role binds to different palette indices in Light vs Dark *within the same generation*** — because base Int
  UI rebuilds the ramp per mode instead of remapping a shared one. The classic themes carry no role layer: each
  component maps a UI role straight onto a numbered stop, and the stop moves between modes. Verified in `expUI_light`/
  `expUI_dark`: the primary accent `#3574F0` is `Blue4` in light but `Blue6` in dark (the Int UI Kit even labels them
  "Blue 4 (Primary)" vs. "Blue 6 (Primary)"); panel `background` is `Gray13` (light) vs `Gray2` (dark); `borderColor`
  `Gray12` vs `Gray1`; `selectionForeground` swaps ends of the ramp (`Gray1`↔`Gray12`). Only mid-ramp roles that read
  similarly in both directions keep their index (`infoForeground`/secondary text ≈ `Gray7`). This is the concrete reason
  for the "always give a light **and** a dark value" rule: you cannot reuse one mode's index for the other. The
  role→index mapping for classic Int UI lives only as prose in the public **Int UI Kit** Figma (the "Usages" column:
  e.g., Gray 1 = primary text in light/main editor background in dark) — it is never materialized as a token in code,
  which is exactly the gap Islands closes.
- **Islands adds a semantic-role layer that base Int UI and old themes lack.** The *Islands* themes define roles like
  layered backgrounds (`layer-0/1/2-bg`), `control-bg`, `tool-window-bg`, accent roles
  (`accent-brand/error/warning/success-bg`), and text roles (`text-default/muted/secondary/disabled/link`). Base Int UI
  (`expUI_light`/`expUI_dark`) does **not** have this global role layer — its components map directly to palette
  entries (e.g., `Gray13`). Old themes (`intellijlaf`/`darcula`) also lack this Islands dash-role vocabulary, though
  `intellijlaf` does define some semantic-ish color aliases of its own (`foreground`, `selectionForeground`, `panel`,
  etc.) — they just aren't the Islands `layer-*`/`text-*` system. So this role vocabulary is Islands-specific; do not
  assume it exists under plain Int UI. Where it exists, prefer the highest-level role that fits over a raw palette
  index. In Islands the raw ramp is **shared across Light and Dark** (verified: the `gray-10…teal-160` color-block
  values are identical in `ManyIslandsLight`/`ManyIslandsDark` — the raw scale, not the whole files); light/dark is
  achieved entirely by re-pointing the *named* tokens (`text-default`, `layer-0-bg`, `selection-bg-active`) at different
  stops — the inverse of how classic Int UI rebuilds the ramp per mode.
- **A theme can report as Islands yet expose no Islands palette — because the palette keys are present but in
  the *wrong format* for the Islands read path.** `Islands Darcula` (`ManyIslandsDarcula.theme.json`, parent
  `ExperimentalDark`) sets `Islands: 1`, so `IslandsState.isEnabled()` is true. But neither it nor its ancestry
  (`ManyIslandsDarcula` → `ExperimentalDark`/`expUI_dark` → base `Darcula`) defines any `gray-10`-style dash-scale keys.
  Runtime-verified via the IDE's *Edit LaF Defaults* (IJ 261.3, filter "palette"): the live `UIDefaults` *is* fully
  populated with palette colors — `ColorPalette.Blue1…Blue13` (merged from the `expUI_dark` parent, e.g.,
  `Blue6 #3574F0`) and `ColorPalette.Gray0…Gray7` plus fractional `Gray0.75 #191A1C`, `Gray1.25`, etc. — but **all under
  classic capitalized keys, with zero dash-scale keys**. The bridge's `ThemeColorPalette.readFromLaF()` switches to the
  `"ColorPalette.gray-"` prefix when `isIslands` is true, so it matches none of those classic keys and returns **empty**
  hue lists while `isIslands = true`. The data is there; it's a key-format mismatch, not absent data. Per the Islands
  theme author (JetBrains, mid-2026), this is a known, *intentional-for-now* gap — Islands Darcula simply hasn't been
  given a separate dash-scale Darcula palette yet and is expected (not guaranteed) to be realigned to the `gray-NN` keys
  in a later build. So treat the empty-palette behavior as **transitional and version-specific** (verified unchanged in
  IJ 261.3 and 262 EAP; since 262 feature freeze has passed without the realignment, it will persist at least through
  the 262 line and cannot change before 263 at the earliest): re-check it against the build you're reviewing — 263 EAPs
  onward — rather than asserting it permanently. The durable lesson is the general one — "is Islands" does not guarantee
  a populated `ThemeColorPalette`, which is exactly why the API exposes safe accessors and an `Empty` sentinel. Read
  palette colors via the `*OrNull` accessors (`JewelTheme.colorPalette.grayOrNull(10)`, `blueOrNull(60)`, or
  `lookup("gray-10")`), which return `null` on a missing/partial palette; the bare `gray(index)`/`blue(index)` forms are
  `@Deprecated` and throw `NullPointerException` when the LaF has no full palette. Note the accessor index is
  theme-aware: Islands indices are 10-based (`grayOrNull(10)` → first entry), classic are 1-based (`grayOrNull(1)`).
  This reinforces the "never assume a palette index exists" rule.
- **Tool-window and "layer" backgrounds are their own roles.** Surfaces stack (window vs. tool-window vs. dialog vs.
  popup vs. editor), each with its own background role. Picking the wrong layer's background is the common
  Islands-vs.-Int UI breakage.
- **Corner radii follow a small scale tied to container size**, with compact variants — larger containers (islands,
  banners) use larger radii, controls/buttons a smaller one, tiny controls smaller still. Treat radius as coming from
  that scale, not an arbitrary literal (this is the same concentric idea in `idiomatic-components.md`).
- **A `.compact` density mode exists** for many metrics (control min sizes, row heights, radii, insets) — verified e.g.,
  `tabHeight.compact`/`rowHeight.compact` in old themes and `minimumSize.compact`/`padding.compact` in Islands.
  Spacing/sizing should not assume a single density; values come in regular and compact pairs.

These remain *principles*, not lookups: cite the public theme files as evidence, but do not hardcode specific values
pulled from them into reviewed code.

For *how* a `.theme.json` is actually parsed and turned into the `UIDefaults` these principles describe — the `colors`/
`ui`/`icons` sections, parent inheritance order, the `ColorPalette.` prefix, the `*` wildcard suffix-patterning, per-OS
values, nested-key flattening, and the suffix-driven value parser — see `theme-key-resolution.md`.

## Themeable-by-derivation is correct — do not over-correct

A literal used as an *offset or scale on a themed base* is good practice, not a defect:

```kotlin
// derived from the theme base — scales with IDE font settings/zoom
val heading = JewelTheme.defaultTextStyle.copy(fontSize = JewelTheme.defaultTextStyle.fontSize + 4.sp)
```

Such values respond to IDE font-size settings and zoom because they derive from the theme base. Hold the *derivation* up
as the model, not any specific arithmetic. The rule is "themeable by derivation," not "no numeric literals anywhere."
Flag literals that stand alone as the source of truth, not literals that transform a themed value.

Note on this mechanism: Jewel typography and the platform `JBFont` derive heading/secondary sizes **additively**
(`JBFont.biggerOn(n)`/`lessOn(n)`; Jewel `Typography` styles are "derived from" the label style), not by a fixed
multiplier. Do not assert a specific multiplicative ratio as "the" Int UI type scale — the point is derivation from the
theme base by whatever offset the platform uses.

## Reporting

- Name the specific token/role/LaF key to use, not just "use a theme color."
- Distinguish "hardcoded, no theme response" (strong) from "magic number, should be a shared token" (medium).
- Note which themes/modes you could not verify (custom theme, high contrast, zoom) and recommend the manual check.
