# IslandsOnOffButtonUI — Design & Implementation Spec

**Source:** [Int UI Kit - Islands — Toggle Grey bg](https://www.figma.com/design/zKwabe7qCf1c0LFu93997q/Int-UI-Kit--Islands?node-id=18954-37397&m=dev)

---

## 1. Component Overview

The toggle is a **pill-shaped track** with a small **notch indicator** (no text labels). It visually represents a binary on/off state. This is a modernized design compared to the legacy `OnOffButton` which uses text labels and a sliding rectangular knob.

**Implementation:** `IslandsOnOffButtonUI.kt` (`platform-impl/.../darcula/ui/`)
**API component:** `OnOffButton.java` (this directory)

---

## 2. Dimensions

| Property | Value |
|----------|-------|
| Track width | 26px |
| Track height | 16px |
| Track corner radius | 100% of height (fully rounded pill) |
| Notch (ON) | 10×10px solid filled circle, centered vertically, positioned at `left: 13px` (3px inset from the right of the track) |
| Notch (OFF) | 8×8px ring (2px stroke), 4px inset on every side of the track |
| Asset canvas | 32×22px — the 26×16 track plus 3px of padding per side for the focus ring |
| Component preferred size | width = asset width (32px), height = max(32, asset height) = 32px |

The ON notch is a **solid filled 10×10px circle** at `left: 13px`; the OFF notch is a **hollow 8×8px ring** at `left: 4px`. Both keep uniform padding inside the track — 3px around the ON notch, 4px around the OFF notch. Track, border and notch all come from the `toggle*.svg` assets (see §3), so the geometry lives in the icons rather than in paint code.

The component preferred size is larger than the track to accommodate the focus ring without clipping. The track is centered within the component bounds. Focus ring padding = gap (1px) + stroke (2px) = 3px per side, and it lives inside the asset canvas rather than in paint code, so all six assets share one canvas and gaining focus never moves the track or resizes the component.

The toggle has no separate compact mode size — it uses the same dimensions regardless of the UI density setting.

All pixel values are scaled via `JBUIScale.scale()`.

---

## 3. Color Specification

Everything, including the focus ring, is drawn from SVG assets in
`platform-impl/resources/com/intellij/ide/ui/laf/icons/`: `toggleOn.svg`, `toggleOff.svg`,
`toggleOnFocused.svg`, `toggleOffFocused.svg`, `toggleOnDisabled.svg`, `toggleOffDisabled.svg`.

There is one asset per state and no per-theme copies. Each painted element names the theme color it
takes its fill or stroke from in a `color-fill-key` / `color-stroke-key` attribute — the convention
the Got It tooltip icons use (`com.intellij.ui.GotItComponentBuilderKt#colorizeIfPossible`).
`TogglePalettePatcher` in `IslandsOnOffButtonUI` resolves each name against UI defaults, where
`UITheme` registers every theme color under a `ColorPalette.` prefix. Colors baked into the assets
are only a fallback for themes that declare none of these colors.

The values are the semantic `toggle-*` tokens rather than raw palette tokens, because a raw token
such as `transparent-white-30` holds the same value in light and dark themes and so cannot express a
per-theme color; `toggle-off-bg` resolves to `transparent-black-40` in light and `transparent-white-50`
in dark.

### 3.1 Track Background Colors (enabled)

| State | Light | Dark |
|-------|-------|------|
| ON | `accent-brand-bg` (`#3871E1`) | `accent-brand-bg` (`#3871E1`) |
| OFF | `transparent-black-50` (`rgba(0,0,0,0.27)`) | `transparent-white-50` (`rgba(255,255,255,0.16)`) |

Note: OFF uses semi-transparent fills (not opaque), so the track blends with the parent background.

### 3.2 Notch/Indicator Colors (enabled)

| State | Light | Dark |
|-------|-------|------|
| ON notch | `white` | `white` |
| OFF notch | `white` | `white` |

Notch colors are resolved per-state via `toggle-on-notch` / `toggle-off-notch`.

### 3.3 Disabled State

| Element | Light | Dark |
|---------|-------|------|
| Track fill (ON) | `control-bg-disabled` | `transparent` |
| Track fill (OFF) | `control-bg-disabled` | `transparent` |
| Track border (ON) | `control-border-disabled` | `control-border-disabled` |
| Track border (OFF) | `control-border-disabled` | `control-border-disabled` |
| Notch (ON) | `icon-disabled` (`#C3C5CB`) | `icon-disabled` (`#5F6269`) |
| Notch (OFF) | `icon-disabled` (`#C3C5CB`) | `icon-disabled` (`#5F6269`) |

The disabled state is a separate pair of assets (`toggleOnDisabled.svg`, `toggleOffDisabled.svg`) with the same geometry as the enabled ones. A separate file per state is required because a color is resolved per theme, not per component state.

#### Overlapping translucent layers

Each track is painted as a full-extent fill (3–29 × 3–19) with the 1px border stroked on top of its
outer edge, rather than a fill inset to meet the border. An inset fill leaves the two edges abutting,
which antialiases into a visible seam.

Because the two layers now overlap, a translucent border would composite twice over the fill and read
as a darker ring. The OFF track's border is therefore `transparent` in Islands Light, Dark and
Darcula, where it would be the same color as the fill anyway and contributes nothing. High Contrast
keeps a visible `#1AEBFF` border: there the fill is opaque `#000000`, so there is no double
compositing, and the border is what makes the track visible at all against the background.

The fill is drawn before the border in every asset, so the border is never covered.

### 3.4 Focus Ring

| Property | Value |
|----------|-------|
| Color | `toggle-focus-border` (→ `control-brand-border`) |
| Gap (track edge → ring inner edge) | 1px |
| Stroke width | 2px |
| Shape | Pill-shaped `rect` with `rx` = half the height |

The focus ring is part of `toggleOnFocused.svg` / `toggleOffFocused.svg`, drawn in the padding the
shared canvas reserves around the track. It is suppressed when the component carries a validation
outline (`DarculaUIUtil.getOutline`), matching `DarculaCheckBoxUI`. Disabled toggles have no focused
asset, since a disabled toggle is not focusable.

---

## 4. Interaction States

| State | Visual Change |
|-------|---------------|
| Default | Resting appearance |
| Focused | Focus ring (2px stroke with 1px gap) around the track |
| Disabled | Transparent/muted track fill with 1px border ring; muted notch; no interaction response |

---

## 5. Behavioral Spec

- **Click/tap** toggles between ON and OFF states.
- The component extends `JToggleButton`; `isSelected() == true` means ON.
- No text labels — the visual indicator (notch shape + track color) communicates state.
- `installUI` sets `alignmentY = 0.5f`.
- Fixed preferred/min/max size (see §2).

---

## 6. Theme Key Mapping

All colors are theme colors named by the assets. Every entry in a theme's `colors` block is
registered into UI defaults as `ColorPalette.<name>`, which is how `TogglePalettePatcher` resolves
them; no `icons.ColorPalette` entries and no platform-side key table are involved.

### Colors named by the assets

| Theme color | Role | Asset |
|-------------|------|-------|
| `toggle-off-bg` | OFF track fill | `toggleOff.svg`, `toggleOffFocused.svg` |
| `toggle-off-border` | OFF track border | `toggleOff.svg`, `toggleOffFocused.svg` |
| `toggle-off-notch` | OFF notch | `toggleOff.svg`, `toggleOffFocused.svg` |
| `toggle-on-bg` | ON track fill | `toggleOn.svg`, `toggleOnFocused.svg` |
| `toggle-on-border` | ON track border | `toggleOn.svg`, `toggleOnFocused.svg` |
| `toggle-on-notch` | ON notch | `toggleOn.svg`, `toggleOnFocused.svg` |
| `toggle-focus-border` | Focus ring stroke | `toggleOnFocused.svg`, `toggleOffFocused.svg` |
| `toggle-off-disabled-bg` | OFF track fill (disabled) | `toggleOffDisabled.svg` |
| `toggle-off-disabled-border` | OFF track border (disabled) | `toggleOffDisabled.svg` |
| `toggle-off-disabled-notch` | OFF notch (disabled) | `toggleOffDisabled.svg` |
| `toggle-on-disabled-bg` | ON track fill (disabled) | `toggleOnDisabled.svg` |
| `toggle-on-disabled-border` | ON track border (disabled) | `toggleOnDisabled.svg` |
| `toggle-on-disabled-notch` | ON notch (disabled) | `toggleOnDisabled.svg` |

`UINewThemeIconsTest` checks that every painted element names a color and that all four Islands
themes declare every color the assets name.

The `ToggleButton.focusBorderColor` UI defaults key is no longer read by the delegate; the focus ring
colour comes from `toggle-focus-border` like every other element.

### Token aliases in Islands themes

All four Islands themes declare the `toggle-*` colors in their `colors` block. Islands Dark and
Islands Light alias them to shared design tokens; Islands Darcula and Islands High Contrast give
literal colors, matching how those themes handle checkbox palette keys.

| Semantic Token | Light Value | Dark Value |
|----------------|-------------|------------|
| `toggle-on-bg` | `accent-brand-bg` | `accent-brand-bg` |
| `toggle-off-bg` | `transparent-black-40` | `transparent-white-50` |
| `toggle-off-border` | `transparent` | `transparent` |
| `toggle-on-border` | `accent-brand-bg` | `accent-brand-bg` |
| `toggle-on-disabled-bg` | `control-bg-disabled` | `transparent` |
| `toggle-off-disabled-bg` | `control-bg-disabled` | `transparent` |
| `toggle-on-disabled-border` | `control-border-disabled` | `control-border-disabled` |
| `toggle-off-disabled-border` | `control-border-disabled` | `control-border-disabled` |
| `toggle-focus-border` | `control-brand-border` | `control-brand-border` |
| `toggle-on-notch` | `white` | `white` |
| `toggle-off-notch` | `icon-over-accent` | `gray-130` |
| `toggle-on-disabled-notch` | `icon-disabled` | `icon-disabled` |
| `toggle-off-disabled-notch` | `icon-disabled` | `icon-disabled` |

---

## 7. UI Delegate Registration

Registered programmatically by `IslandsUICustomization.applyMissingKeys()` whenever Islands mode is enabled (`IslandsState.isEnabled()`). The delegate class name is injected into `UIManager` defaults as `"OnOffButtonUI"` if not already set by the theme, so any Islands-enabled theme — including custom themes — gets the Islands toggle automatically without explicit JSON configuration.

---

## 8. Implementation Notes

1. **Everything** — painted from a single `toggle*.svg` icon per state, centered in the component bounds. Icons are resolved by `findIconUsingNewImplementation`, so they are cached and HiDPI-aware, then wrapped with `createWithPatcher` so rasterization is shared per resolved palette.
2. **State selection** — `toggleOn`/`toggleOff` plus a `Disabled` or `Focused` suffix, the scheme `LafIconLookup` uses for checkboxes. The assets live in the flat `laf/icons/` directory rather than the `intellij/` and `darcula/` subdirectories, because one asset serves every theme.
3. **Focus ring** — part of the focused assets. Only shown when enabled, focused, and no validation outline.
4. **Preferred size** — taken from the asset canvas rather than hardcoded, so changing the assets cannot desynchronize layout from what is painted.
5. **No text** in paint path — the Islands UI ignores `onText`/`offText` entirely.
6. **Sandbox panel** — `OnOffButtonPanel` shows all state combinations (enabled/disabled × on/off) with state labels.
