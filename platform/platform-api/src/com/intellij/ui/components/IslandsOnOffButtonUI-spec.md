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
| Component preferred size | track + focus ring padding: width = 26 + 2×4 = 34px, height = max(32, 16 + 2×4) = 32px |

The ON notch is a **solid filled 10×10px circle** at `left: 13px`; the OFF notch is a **hollow 8×8px ring** at `left: 4px`. Both keep uniform padding inside the track — 3px around the ON notch, 4px around the OFF notch. Track, border and notch all come from the `toggle*.svg` assets (see §3), so the geometry lives in the icons rather than in paint code.

The component preferred size is larger than the track to accommodate the focus ring without clipping. The track is centered within the component bounds. Focus ring padding = gap (1px) + stroke (2px) + AA safe area (1px) = 4px per side.

The toggle has no separate compact mode size — it uses the same dimensions regardless of the UI density setting.

All pixel values are scaled via `JBUIScale.scale()`.

---

## 3. Color Specification

Everything except the focus ring is drawn from SVG assets in
`platform-impl/resources/com/intellij/ide/ui/laf/icons/`: `toggleOn.svg`, `toggleOff.svg`,
`toggleOnDisabled.svg`, `toggleOffDisabled.svg`.

There is one asset per state and no per-theme copies. Each painted element carries an `id` naming a
`Toggle.*` palette key, and themes recolor it through the `icons.ColorPalette` block
(`UiThemePaletteCheckBoxScope` / `NewThemeCheckboxPatcher`) — the same mechanism the New UI checkbox
and radio icons use. Colors baked into the assets are only a fallback for themes that declare no
`Toggle.*` entries. Palette recoloring is available to themes based on the experimental (New UI)
themes, which covers all Islands themes.

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

Notch colors are resolved per-state via `Toggle.Foreground.Selected` / `Toggle.Foreground.Default`.

### 3.3 Disabled State

| Element | Light | Dark |
|---------|-------|------|
| Track fill (ON) | `control-bg-disabled` | `transparent` |
| Track fill (OFF) | `control-bg-disabled` | `transparent` |
| Track border (ON) | `control-border-disabled` | `control-border-disabled` |
| Track border (OFF) | `control-border-disabled` | `control-border-disabled` |
| Notch (ON) | `icon-disabled` (`#C3C5CB`) | `icon-disabled` (`#5F6269`) |
| Notch (OFF) | `icon-disabled` (`#C3C5CB`) | `icon-disabled` (`#5F6269`) |

The disabled state is a separate pair of assets (`toggleOnDisabled.svg`, `toggleOffDisabled.svg`) with the same geometry as the enabled ones. A separate file per state is required because palette recoloring is resolved per theme, not per component state.

### 3.4 Focus Ring

| Property | Value |
|----------|-------|
| Color | `control-brand-border` (via `ToggleButton.focusBorderColor`) |
| Gap (track edge → ring inner edge) | 1px |
| Stroke width | 2px |
| Shape | Pill-shaped (concentric round rects, `Path2D.WIND_EVEN_ODD` fill) |

The focus ring uses the same rendering technique as `DarculaCheckBoxUI` validation outlines — two concentric `RoundRectangle2D` shapes filled with even-odd winding.

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

The focus ring is the only color read from UI defaults; the track, border and notch colors are icon
palette keys declared in each theme's `icons.ColorPalette` block.

### UI defaults key

| Key | Role | Since |
|-----|------|-------|
| `ToggleButton.focusBorderColor` | Focus ring stroke | 2026.2 |

### Icon palette keys

| Key | Role | Asset |
|-----|------|-------|
| `Toggle.Background.Default` | OFF track fill | `toggleOff.svg` |
| `Toggle.Border.Default` | OFF track border | `toggleOff.svg` |
| `Toggle.Foreground.Default` | OFF notch | `toggleOff.svg` |
| `Toggle.Background.Selected` | ON track fill | `toggleOn.svg` |
| `Toggle.Border.Selected` | ON track border | `toggleOn.svg` |
| `Toggle.Foreground.Selected` | ON notch | `toggleOn.svg` |
| `Toggle.Background.Disabled` | OFF track fill (disabled) | `toggleOffDisabled.svg` |
| `Toggle.Border.Disabled` | OFF track border (disabled) | `toggleOffDisabled.svg` |
| `Toggle.Foreground.Disabled` | OFF notch (disabled) | `toggleOffDisabled.svg` |
| `Toggle.Background.SelectedDisabled` | ON track fill (disabled) | `toggleOnDisabled.svg` |
| `Toggle.Border.SelectedDisabled` | ON track border (disabled) | `toggleOnDisabled.svg` |
| `Toggle.Foreground.SelectedDisabled` | ON notch (disabled) | `toggleOnDisabled.svg` |

Supported keys are listed in `UiThemePaletteCheckBoxScope`; `UiThemePaletteScopeManager` routes both
the `Toggle.` key prefix and the `laf/icons/toggle*.svg` paths to that scope.

### Token aliases in Islands themes

Islands Dark and Islands Light map the palette keys to semantic tokens; Islands Darcula and Islands
High Contrast declare literal colors, matching how those themes handle checkbox palette keys.

| Semantic Token | Light Value | Dark Value |
|----------------|-------------|------------|
| `toggle-on-bg` | `accent-brand-bg` | `accent-brand-bg` |
| `toggle-off-bg` | `transparent-black-40` | `transparent-white-50` |
| `toggle-on-border` | `accent-brand-bg` | `accent-brand-bg` |
| `toggle-off-border` | `transparent-black-40` | `transparent-white-50` |
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

1. **Track, border and notch** — painted from a single `toggle*.svg` icon per state, centered in the component bounds. Icons are resolved by `findIconUsingNewImplementation`, so they are cached and HiDPI-aware.
2. **State selection** — `toggleOn`/`toggleOff` plus a `Disabled` suffix; no `Selected`/`Focused` suffix lookup, so the assets live in the flat `laf/icons/` directory rather than the `intellij/` and `darcula/` subdirectories used by `LafIconLookup`.
3. **Focus ring** — two concentric pill-shaped `RoundRectangle2D` shapes with `Path2D.WIND_EVEN_ODD` fill (gap 1px + stroke 2px). Only shown when enabled, focused, and no validation outline. It stays procedural because it is a single-color shape drawn outside the 26×16 icon bounds.
4. **Enlarged preferred size** — 34×32px (26×16 track + 4px padding per side) to accommodate focus ring without clipping.
5. **No text** in paint path — the Islands UI ignores `onText`/`offText` entirely.
6. **Sandbox panel** — `OnOffButtonPanel` shows all state combinations (enabled/disabled × on/off) with state labels.
