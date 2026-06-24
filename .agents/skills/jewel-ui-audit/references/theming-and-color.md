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
    2. Palette index semantics differ between light and dark modes (`Blue4` is primary in classic light, `Blue6` is
       primary in classic dark, and `Gray1` is unreadable as text in dark mode);
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

1. Use a Jewel global color (`JewelTheme.globalColors...`) that matches the semantic role.
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

Treat a literal next to or replacing a commented-out themed expression as a definite finding. Someone overrode the
theme (often for debugging) and lost the theme response. Restore the themed value or select the appropriate themed
role if the original was wrong.

## Semantic color roles must match usage

A token carries a role in its name. The usage must match the role.

| Token role        | Intended use             | Misuse to flag                                       |
|-------------------|--------------------------|------------------------------------------------------|
| `borders.*`       | 1px separators, outlines | Used as a `.background(...)` fill for a large region |
| foreground/text   | text and icon tint       | Painting a container/background                      |
| disabled/inactive | disabled controls        | Styling enabled, interactive content                 |
| selection/focus   | selected/focused state   | Static decoration unrelated to state                 |

A common defect is using a border color as a background. Border tokens are tuned to read as thin lines close to the
background. As a large fill, they produce weak contrast and diverge from the panel/background token under custom themes
(where border and background are tuned independently).

Use a content or panel background role instead. When unsure which background role is right, ask a designer rather than
guessing.

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
- **Palette indices are not portable across theme generations** (numbering and step count differ), though the **scale
  direction is the same** in all of them: low index = darkest, high index = lightest.
  - Across theme files, old themes run from `grey01` (`#6e6e6e`) to `grey18` (`#fafafa`) in 18 steps, and Int UI runs
    from `Gray1` (`#000000`) to `Gray14` (`#FFFFFF`) in 14 steps. Islands runs from `gray-10` (darkest) to `gray-160`
    (lightest) in steps of 10.
  - Never hardcode or assume a palette index maps to the same color across generations. A Jewel `ThemeColorPalette`
    KDoc mislabels the gray order; the actual values run dark→light.
- **A given role binds to different palette indices in Light vs. Dark *within the same generation*** because base Int
  UI rebuilds the ramp per mode instead of remapping a shared one. Classic themes carry no role layer, so each
  component maps a UI role straight onto a numbered stop that moves between modes.
  - Verified in `expUI_light`/`expUI_dark`: primary accent `#3574F0` is `Blue4` in light ("Blue 4 (Primary)") and
    `Blue6` in dark ("Blue 6 (Primary)"). Panel `background` is `Gray13` (light) vs. `Gray2` (dark), `borderColor` is
    `Gray12` vs. `Gray1`, and `selectionForeground` swaps `Gray1`↔`Gray12`.
  - Only mid-ramp roles that read similarly in both modes keep their index (`infoForeground`/secondary text ≈ `Gray7`).
    Always provide both a light **and** a dark value instead of reusing one mode's index.
  - In classic Int UI, the role→index mapping exists only in the public **Int UI Kit** Figma ("Usages" column) rather
    than as code tokens. For example, Gray 1 is primary text in light and main editor background in dark.
- **Islands adds a semantic-role layer that base Int UI and old themes lack.** Islands themes define layered
  backgrounds (`layer-0/1/2-bg`), `control-bg`, `tool-window-bg`, accent roles
  (`accent-brand/error/warning/success-bg`), and text roles (`text-default/muted/secondary/disabled/link`).
  - Base Int UI (`expUI_light`/`expUI_dark`) maps components directly to palette entries (such as `Gray13`). Old themes
    (`intellijlaf`/`darcula`) define a few color aliases (`foreground`, `selectionForeground`, `panel`), not the
    Islands `layer-*`/`text-*` vocabulary.
  - Do not assume Islands role tokens exist under plain Int UI; where they do exist, prefer the highest-level role over
    a raw palette index.
  - Islands shares its raw `gray-10…teal-160` color-block values across `ManyIslandsLight` and `ManyIslandsDark`. It
    switches between light and dark by re-pointing named tokens (`text-default`, `layer-0-bg`, `selection-bg-active`)
    at different stops rather than rebuilding the ramp per mode.
- **A theme can report as Islands yet expose no Islands palette due to a key-format mismatch.** `Islands Darcula`
  (`ManyIslandsDarcula.theme.json`, parent `ExperimentalDark`) sets `Islands: 1`, so `IslandsState.isEnabled()` is
  true. However, neither it nor its ancestry (`ManyIslandsDarcula` → `ExperimentalDark`/`expUI_dark` → `Darcula`)
  defines `gray-10`-style dash-scale keys.
  - Runtime-verified via *Edit LaF Defaults* (IJ 261.3, filter "palette"): live `UIDefaults` contains
    `ColorPalette.Blue1…Blue13` (such as `Blue6 #3574F0`) and `ColorPalette.Gray0…Gray7` (plus fractional `Gray0.75
    #191A1C`, `Gray1.25`) under **classic capitalized keys, with zero dash-scale keys**.
  - Because `ThemeColorPalette.readFromLaF()` looks up the `"ColorPalette.gray-"` prefix when `isIslands` is true, it
    matches none of the classic keys and returns **empty** hue lists while `isIslands = true`.
  - Per the Islands theme author (JetBrains, mid-2026), this is a known transitional gap until Islands Darcula receives
    a dash-scale `gray-NN` palette. It remains unchanged in IJ 261.3 and 262 EAP (post-feature-freeze) and cannot
    change before 263 at the earliest; re-check against 263+ builds when reviewing.
  - Do not assume `isIslands` guarantees a populated `ThemeColorPalette`. Read palette colors via `*OrNull` accessors
    (`JewelTheme.colorPalette.grayOrNull(10)`, `blueOrNull(60)`, or `lookup("gray-10")`), which return `null` on a
    missing or partial palette.
  - Never call the `@Deprecated` `gray(index)`/`blue(index)` accessors, which throw `NullPointerException` when the LaF
    lacks a full palette. Accessor indices are theme-aware: Islands indices are 10-based (`grayOrNull(10)` → first
    entry), while classic indices are 1-based (`grayOrNull(1)`).
- **Tool-window and "layer" backgrounds are their own roles.** Surfaces stack (window, tool-window, dialog, popup, and
  editor), each with its own background role. Picking the wrong layer's background is the common Islands-versus-Int UI
  breakage.
- **Corner radii follow a small scale tied to container size**, with compact variants — larger containers (islands,
  banners) use larger radii, controls/buttons a smaller one, tiny controls smaller still. Treat radius as coming from
  that scale, not an arbitrary literal (this is the same concentric idea in `idiomatic-components.md`).
- **A `.compact` density mode exists** for many metrics (control min sizes, row heights, radii, insets) — verified e.g.,
  `tabHeight.compact`/`rowHeight.compact` in old themes and `minimumSize.compact`/`padding.compact` in Islands.
  Spacing/sizing should not assume a single density; values come in regular and compact pairs.

These remain *principles*, not lookups: cite the public theme files as evidence, but do not hardcode specific values
pulled from them into reviewed code.

See `theme-key-resolution.md` for how `.theme.json` files are parsed into `UIDefaults`, including `colors`/`ui`/`icons`
sections, parent inheritance order, the `ColorPalette.` prefix, `*` wildcard suffix-patterning, per-OS values,
nested-key flattening, and the suffix-driven value parser.

## Themeable-by-derivation is correct — do not over-correct

Allow numeric literals that apply an *offset or scale to a themed base value*:

```kotlin
// derived from the theme base — scales with IDE font settings/zoom
val heading = JewelTheme.defaultTextStyle.copy(fontSize = JewelTheme.defaultTextStyle.fontSize + 4.sp)
```

Such values respond to IDE font-size settings and zoom because they derive from the theme base. Hold the *derivation* up
as the model, not any specific arithmetic. The rule is "themeable by derivation," not "no numeric literals anywhere."
Flag literals that stand alone as the source of truth, not literals that transform a themed value.

Jewel typography and platform `JBFont` derive heading and secondary sizes **additively** (`JBFont.biggerOn(n)`/
`lessOn(n)`; Jewel `Typography` styles derive from the label style) rather than by a fixed multiplier. Do not assert a
specific multiplicative ratio as the Int UI type scale; derive from the theme base using the platform's offset.

## Reporting

- Name the specific token/role/LaF key to use, not just "use a theme color."
- Report hardcoded colors that ignore theme changes as high-severity findings, and report repeated magic-number
  dimensions that belong in a shared token scale as medium-severity findings.
- Note which themes/modes you could not verify (custom theme, high contrast, zoom) and recommend the manual check.
