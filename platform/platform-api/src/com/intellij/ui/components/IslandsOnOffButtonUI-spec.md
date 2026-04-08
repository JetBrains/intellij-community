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

The ON notch is a thin vertical bar. The OFF notch is a hollow circle (ring).

The toggle has no separate compact mode size — it uses the same 26×16px dimensions regardless of the UI density setting.

All pixel values are scaled via `JBUIScale.scale()`.

---

## 3. Color Specification

### 3.1 Track Background Colors (enabled)

| State | Light | Dark |
|-------|-------|------|
| ON | `accent-brand-bg` (`#3871E1`) | `accent-brand-bg` (`#3871E1`) |
| OFF | `transparent-black-50` (`rgba(0,0,0,0.27)`) | `transparent-white-40` (`rgba(255,255,255,0.16)`) |

Note: OFF uses semi-transparent fills (not opaque), so the track blends with the parent background.

### 3.2 Notch/Indicator Colors (enabled)

The notch is white (`ToggleButton.buttonColor` → `#FFFFFF`) in all enabled states (both light and dark).

### 3.3 Disabled State

| Element | Light | Dark |
|---------|-------|------|
| Track fill | Transparent (`ToggleButton.disabledTrackFill`) | Transparent |
| Track border | `control-border-disabled` (`#DDDFE4`) | `control-border-disabled` (`#33353B`) |
| Notch | `icon-disabled` / `gray-120` (`#C3C5CB`) | `icon-disabled` / `gray-70` (`#5F6269`) |

The disabled border is rendered as a 1px filled ring (area subtraction technique), not a stroked path, for pixel-perfect rendering at fractional scales.

---

## 4. Interaction States

| State | Visual Change |
|-------|---------------|
| Default | Resting appearance |
| Disabled | Transparent track fill with 1px border ring; muted notch; no interaction response |

---

## 5. Behavioral Spec

- **Click/tap** toggles between ON and OFF states.
- The component extends `JToggleButton`; `isSelected() == true` means ON.
- No text labels — the visual indicator (notch shape + track color) communicates state.
- `createUI` sets `alignmentY = 0.5f` and `isRolloverEnabled = true`.
- Fixed preferred/min/max size: 26×16px (scaled).

---

## 6. Theme Key Mapping

Islands resolves all colors via `JBColor.namedColor("ToggleButton.*")` backed by `ManyIslands*.theme.json`.

### Core keys

| Key | Role | Since |
|-----|------|-------|
| `ToggleButton.onBackground` | ON track | — |
| `ToggleButton.onDisabledBackground` | ON track (disabled, legacy compat) | 2026.2 |
| `ToggleButton.offBackground` | OFF track | — |
| `ToggleButton.offDisabledBackground` | OFF track (disabled, legacy compat) | 2026.2 |
| `ToggleButton.buttonColor` | Notch (enabled) | — |
| `ToggleButton.borderColor` | Track border (legacy) | — |
| `ToggleButton.disabledTrackFill` | Disabled track fill (typically transparent) | 2026.2 |
| `ToggleButton.disabledBorderColor` | Disabled track border ring | 2026.2 |
| `ToggleButton.disabledButtonColor` | Disabled notch | 2026.2 |

### Token aliases in Islands themes

| Semantic Token | Light Value | Dark Value |
|----------------|-------------|------------|
| `toggle-on-bg` | `accent-brand-bg` | `accent-brand-bg` |
| `toggle-on-disabled-bg` | `#DFE1E5` | `#4E5157` |
| `toggle-off-bg` | `transparent-black-50` | `transparent-white-40` |
| `toggle-off-disabled-bg` | `#EBECF0` | `#393B40` |
| `toggle-button-bg` | `white` | `white` |
| `toggle-border` | `gray-120` | `gray-70` |
| `toggle-disabled-track-fill` | `transparent` | `transparent` |
| `toggle-disabled-border` | `control-border-disabled` | `control-border-disabled` |
| `toggle-disabled-button-color` | `icon-disabled` | `icon-disabled` |

---

## 7. UI Delegate Registration

In both `ManyIslandsLight.theme.json` and `ManyIslandsDark.theme.json`:

```json
"OnOffButtonUI": "com.intellij.ide.ui.laf.darcula.ui.IslandsOnOffButtonUI"
```

---

## 8. Implementation Notes

1. **Pill track** — `RoundRectangle2D` with arc = height (100% rounded).
2. **Disabled border** — rendered via `Area` subtraction (outer track minus inset track) then filled, not stroked. This avoids sub-pixel stroke artifacts.
3. **Fixed dimensions** — `getPreferredSize`/`getMinimumSize`/`getMaximumSize` all return 26×16 (via `JBUIScale`).
4. **No text** in paint path — the Islands UI ignores `onText`/`offText` entirely.
5. **Sandbox panel** — `OnOffButtonPanel` shows all 4 state combinations (enabled/disabled × on/off) with state labels.

### Files modified

- `community/platform/platform-impl/src/com/intellij/ide/ui/laf/darcula/ui/IslandsOnOffButtonUI.kt` — new UI delegate
- `community/platform/platform-resources/src/themes/islands/ManyIslandsLight.theme.json` — token aliases + UI registration
- `community/platform/platform-resources/src/themes/islands/ManyIslandsDark.theme.json` — token aliases + UI registration
- `community/platform/platform-resources/src/themes/metadata/IntelliJPlatform.themeMetadata.json` — 7 new keys (`since: 2026.2`)
- `community/platform/platform-impl/internal/src/com/intellij/internal/ui/sandbox/components/OnOffButtonPanel.kt` — updated sandbox demo
