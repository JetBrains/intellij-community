---
name: icons
description: Create or review IntelliJ New UI SVG icons and theme variants.
---

# IntelliJ Platform New UI Icons

## Golden rules

1. **Always ship two SVGs** — one for the light theme (e.g. `addFile.svg`) and one for the dark theme with the `_dark` suffix (`addFile_dark.svg`). Geometry must be identical between them; only the palette swaps.
   - **Tool-window icons ship as a quartet.** When a tool window has both a 16×16 base (`name.svg` / `name_dark.svg`) and a 20×20 New-UI stripe variant (`name@20x20.svg` / `name@20x20_dark.svg`), they must share the same metaphor. Always update all four files together. The same applies to plugin-local icons like `toolWindowChat.svg` + `toolWindowChat@20x20.svg`. The 16×16 base also appears in Search Everywhere, Find Action, context menus, the Services tool window, recent locations, and the View ▸ Tool Windows menu.
2. **Only use colors from the canonical palette.** See [palette.md](./palette.md). Picking a one-off color breaks theming and contrast.
3. **One canvas size per icon role.** See [Icon roles](#icon-roles). Do not invent new sizes or pad with empty space — IntelliJ scales the canvas as a single unit.
4. **No raster, no gradients, no filters, no embedded fonts.** Path geometry only (`<path>`, `<rect>`, `<circle>`, `<line>`, `<polyline>`, `<polygon>`). Text must be converted to outlines.
5. **Use `fill="none"` on the root `<svg>`** and set `fill` / `stroke` explicitly per shape — never rely on CSS or `currentColor`.
6. **Strokes use `stroke-width="1"`, `stroke-linecap="round"`, `stroke-linejoin="round"`** (or `stroke-miterlimit="10"` for hard joins). Heavier strokes are reserved for hero glyphs inside a circle badge (e.g. status checkmarks) and use `stroke-width="1.5"` or `"2"`.
7. **Pixel-grid align**: keep stroke axes on half-pixel centers (`x.5`) and fills on whole pixels so the icon stays crisp at 1× rendering.
8. **Preserve the Apache 2.0 copyright comment** as line 1. Match the year of contribution.
9. **File names use camelCase** matching the action/node ID and stay ASCII-only.

## Icon roles

| Role                                    | Canvas    | Filename pattern                       | Reference dir                                   |
|-----------------------------------------|-----------|----------------------------------------|-------------------------------------------------|
| Action icons (menus, popups, toolbars)  | **16×16** | `name.svg` + `name_dark.svg`           | `expui/actions/`, `expui/general/`              |
| Tree node icons (PSI, structure view)   | **16×16** | `name.svg` + `name_dark.svg`           | `expui/nodes/`                                  |
| Tool-window stripe icons (legacy/16)    | **16×16** | `name.svg` + `name_dark.svg`           | `expui/toolwindows/`                            |
| Tool-window stripe icons (New UI)       | **20×20** | `name@20x20.svg` + `name@20x20_dark.svg` | `expui/toolwindows/`                          |
| Main toolbar (New UI)                   | **20×20** | `name@20x20.svg` + `name@20x20_dark.svg` | `expui/general/`, `expui/run/`, `expui/toolbar/` |
| Editor gutter icons                     | **14×14** (small marks **12×12**, **9×9**) | `name.svg` + `name_dark.svg`           | `expui/gutter/`                                 |
| Status bar / inline status              | **16×16** | `name.svg` + `name_dark.svg`           | `expui/status/`, `expui/inline/`                |
| Breakpoint marks                        | **14×14**                                   | `name.svg` + `name_dark.svg`           | `expui/breakpoints/`                            |
| Run-config tags & disclosure chevrons   | **16×16** (`chevron*.svg` may be 9×9 to 16×16) | `name.svg` + `name_dark.svg`           | `expui/run/`, `expui/general/`                  |
| Welcome/onboarding & logos (system)     | **16, 20, 28, 48** (per surface)            | `name.svg` + `name_dark.svg`           | `expui/ide/`, `expui/welcome/`                  |

When in doubt, find a sibling icon in the same folder and copy its `width` / `height` / `viewBox`.

## Where the SVGs live

Pick the placement by whether a Classic-UI icon already exists:

- **Only New UI needed** → put the SVG (and its `_dark` sibling) under an `expui/<role>/` resource root. Callers reference it directly through the plugin's generated `<Plugin>Icons` class.
- **Replacing an existing Classic-UI icon** → keep the Classic file at its old path, put the New UI SVG under the matching `expui/<role>/` path, and add a mapping entry to the nearest `*IconMappings.json`. At runtime, when `ExperimentalUI.isNewUI()` is true, the mapper substitutes the New UI file for the Classic one so no caller has to change its icon reference.

Mapping-file shape (excerpt from `community/platform/icons/src/PlatformIconMappings.json`):

```json
{
  "expui": {
    "actions": {
      "addFile.svg": "actions/addFile.svg",
      "checked.svg": ["actions/checked.svg", "actions/setDefault.svg"]
    }
  }
}
```

Key = path relative to the `expui/` root (the New UI file). Value = the Classic path(s) that should resolve to it. A single New UI icon may back multiple Classic paths.

## SVG skeleton

```svg
<!-- Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license. -->
<svg width="16" height="16" viewBox="0 0 16 16" fill="none" xmlns="http://www.w3.org/2000/svg">
  <!-- shapes, ordered back-to-front -->
</svg>
```

## Composition rules

- **One semantic meaning per icon.** A status badge, an accent dot, or a "+" overlay is fine; two unrelated glyphs in one icon is not.
- **Optical centering, not geometric.** Plus/arrow/refresh glyphs sit slightly above center; round badges (class, method, status) are centered on `(cx=8, cy=8)` for 16×16 and `(cx=10, cy=10)` for 20×20.
- **Outer keep-out**: leave at least **1 px** of empty padding on each side of a 16×16 icon (so meaningful geometry lives within `1..15`). For 20×20 use **2 px** of padding. Stripe icons must stay visually balanced inside their 20×20 cell.
- **Stroke + fill pairing for node-style icons** (e.g. `nodes/class.svg`): a light-tinted fill at radius 6.5 with a stroke in the accent color, plus a glyph filled with the same accent.
  - Light: `<circle cx="8" cy="8" r="6.5" fill="<accent-bg-light>" stroke="<accent-light>"/>` then glyph `fill="<accent-light>"`.
  - Dark: same circle with `fill="<accent-bg-dark>" stroke="<accent-dark>"` and glyph filled with `<accent-dark>`.
- **Stroke-only icons** (chevrons, refresh, edit pencil): a single-color path using the neutral stroke (`#6C707E` light / `#CED0D6` dark) at `stroke-width="1"`. Use `#818594` (light) / `#6F737A` (dark) for "secondary" stroke glyphs like dropdown chevrons.
- **Status badges** (error/warning/success/info) follow this template:
  - Light: filled circle/triangle in the *accent* color, glyph painted in `white`.
  - Dark: filled circle/triangle in the *dark accent* color. The glyph stays `white` on error, success and info badges. On a warm (orange or yellow) fill, the glyph uses the matching *muted dark fill* (e.g. `#5E4D33` inside `#F2C55C` warning).
- **Two-tone action icons** (e.g. `actions/addFile.svg`): the base glyph uses the neutral gray, and the small "modifier" (`+`, ✕, ↻, gear) uses the primary blue accent. Light gray + blue accent → dark gray + blue accent in the dark variant.
- **Stroke variants** (e.g. `*_stroke.svg`): an outline of the same glyph, drawn in `white` only, with no `_dark` partner.

## Generation workflow

1. **Pick the role and canvas size** from the table above. Find at least two visually similar siblings under `community/platform/icons/src/expui/<role>/` and read their SVG sources. Mirror their stroke/fill mix.
2. **Lay out geometry on the pixel grid** (whole-pixel fills, half-pixel stroke centers). Optical-center the glyph.
3. **Apply the canonical light palette** from [palette.md](./palette.md). Never invent colors.
4. **Save the light SVG** with the Apache 2.0 header, `width`/`height`/`viewBox` matching the role, and `fill="none"` on `<svg>`.
5. **Duplicate to the `_dark` filename** and swap each color for its dark-theme partner from the palette mapping. Keep paths byte-identical otherwise.
6. **Place and wire the file** as [Where the SVGs live](#where-the-svgs-live) says.
7. **Verify visually** in both themes in the IDE image preview, or run the IDE with and without the Classic UI plugin (`com.intellij.classic.ui`) to compare. Check selection states for stripe icons.

## Common mistakes

- Off-palette colors from a Figma import without remapping.
- Adding a New UI SVG without a `*IconMappings.json` entry when replacing a Classic icon — the Classic icon will keep showing in New UI mode.

## References

- [examples.md](./examples.md) — annotated SVG snippets for the common icon roles, copied from the live icon set.
- Canonical example tree: `community/platform/icons/src/expui/` — when reviewing, diff your new icon against a sibling there.
