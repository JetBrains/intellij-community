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
| Notch (ON) | 2px wide × 7px tall rounded rect, centered vertically, positioned at `left: 18px` |
| Notch (OFF) | 8×8px ring (2px stroke) — outer circle with 4×4px hole subtracted, centered vertically, positioned at `left: 4px` |
| Component preferred size | track + focus ring padding: width = 26 + 2×4 = 34px, height = max(32, 16 + 2×4) = 32px |

The ON notch is a thin vertical bar. The OFF notch is a hollow circle (ring). Both are painted manually via `Graphics2D` shapes (`RoundRectangle2D` and `Ellipse2D` area subtraction).

The component preferred size is larger than the track to accommodate the focus ring without clipping. The track is centered within the component bounds. Focus ring padding = gap (1px) + stroke (2px) + AA safe area (1px) = 4px per side.

The toggle has no separate compact mode size — it uses the same dimensions regardless of the UI density setting.

All pixel values are scaled via `JBUIScale.scale()`.

---

## 3. Color Specification

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

Notch colors are resolved per-state via `ToggleButton.onNotchColor` / `ToggleButton.offNotchColor`.

### 3.3 Disabled State

| Element | Light | Dark |
|---------|-------|------|
| Track fill (ON) | `control-bg-disabled` | `transparent` |
| Track fill (OFF) | `control-bg-disabled` | `transparent` |
| Track border (ON) | `control-border-disabled` | `control-border-disabled` |
| Track border (OFF) | `control-border-disabled` | `control-border-disabled` |
| Notch (ON) | `icon-disabled` (`#C3C5CB`) | `icon-disabled` (`#5F6269`) |
| Notch (OFF) | `icon-disabled` (`#C3C5CB`) | `icon-disabled` (`#5F6269`) |

The disabled border is rendered as a 1px filled ring (area subtraction technique), not a stroked path, for pixel-perfect rendering at fractional scales.

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
- `createUI` sets `alignmentY = 0.5f` and `isRolloverEnabled = true`.
- Fixed preferred/min/max size (see §2).

---

## 6. Theme Key Mapping

Islands resolves all colors via `JBColor.namedColor("ToggleButton.*")` backed by `ManyIslands*.theme.json`.

### Core keys

| Key | Role | Since |
|-----|------|-------|
| `ToggleButton.onBackground` | ON track fill | — |
| `ToggleButton.offBackground` | OFF track fill | — |
| `ToggleButton.onDisabledBackground` | ON track fill (disabled) | 2026.2 |
| `ToggleButton.offDisabledBackground` | OFF track fill (disabled) | 2026.2 |
| `ToggleButton.onDisabledBorderColor` | ON track border (disabled) | 2026.2 |
| `ToggleButton.offDisabledBorderColor` | OFF track border (disabled) | 2026.2 |
| `ToggleButton.focusBorderColor` | Focus ring stroke | 2026.2 |
| `ToggleButton.onNotchColor` | ON notch fill (enabled) | 2026.2 |
| `ToggleButton.offNotchColor` | OFF notch fill (enabled) | 2026.2 |
| `ToggleButton.onDisabledNotchColor` | ON notch fill (disabled) | 2026.2 |
| `ToggleButton.offDisabledNotchColor` | OFF notch fill (disabled) | 2026.2 |

### Token aliases in Islands themes

| Semantic Token | Light Value | Dark Value |
|----------------|-------------|------------|
| `toggle-on-bg` | `accent-brand-bg` | `accent-brand-bg` |
| `toggle-off-bg` | `transparent-black-50` | `transparent-white-50` |
| `toggle-on-disabled-bg` | `control-bg-disabled` | `transparent` |
| `toggle-off-disabled-bg` | `control-bg-disabled` | `transparent` |
| `toggle-on-disabled-border` | `control-border-disabled` | `control-border-disabled` |
| `toggle-off-disabled-border` | `control-border-disabled` | `control-border-disabled` |
| `toggle-focus-border` | `control-brand-border` | `control-brand-border` |
| `toggle-on-notch` | `white` | `white` |
| `toggle-off-notch` | `white` | `white` |
| `toggle-on-disabled-notch` | `icon-disabled` | `icon-disabled` |
| `toggle-off-disabled-notch` | `icon-disabled` | `icon-disabled` |

---

## 7. UI Delegate Registration

In both `ManyIslandsLight.theme.json` and `ManyIslandsDark.theme.json`:

```json
"OnOffButtonUI": "com.intellij.ide.ui.laf.darcula.ui.IslandsOnOffButtonUI"
```

---

## 8. Implementation Notes

1. **Pill track** — `RoundRectangle2D` with arc = height (100% rounded).
2. **Notch (ON)** — 2×7px `RoundRectangle2D` bar at `left: 18px`, centered vertically.
3. **Notch (OFF)** — 8×8px `Ellipse2D` ring with 4×4px hole via `Area` subtraction at `left: 4px`.
4. **Disabled border** — rendered via `Area` subtraction (outer track minus inset track) then filled, not stroked. This avoids sub-pixel stroke artifacts.
5. **Focus ring** — two concentric pill-shaped `RoundRectangle2D` shapes with `Path2D.WIND_EVEN_ODD` fill (gap 1px + stroke 2px). Only shown when enabled, focused, and no validation outline.
6. **Enlarged preferred size** — 34×32px (26×16 track + 4px padding per side) to accommodate focus ring without clipping.
7. **No text** in paint path — the Islands UI ignores `onText`/`offText` entirely.
8. **Sandbox panel** — `OnOffButtonPanel` shows all state combinations (enabled/disabled × on/off) with state labels.

### Files modified

- `community/platform/platform-impl/src/com/intellij/ide/ui/laf/darcula/ui/IslandsOnOffButtonUI.kt` — UI delegate
- `community/platform/platform-resources/src/themes/islands/ManyIslandsLight.theme.json` — token aliases + UI registration
- `community/platform/platform-resources/src/themes/islands/ManyIslandsDark.theme.json` — token aliases + UI registration
- `community/platform/platform-resources/src/themes/metadata/IntelliJPlatform.themeMetadata.json` — 11 new keys (`since: 2026.2`)
- `community/platform/platform-impl/internal/src/com/intellij/internal/ui/sandbox/components/OnOffButtonPanel.kt` — updated sandbox demo
