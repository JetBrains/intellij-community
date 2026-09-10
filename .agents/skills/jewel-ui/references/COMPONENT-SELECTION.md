# Component Selection

Pick the component that matches the user's intent, not just the first one that works. When multiple Jewel components could solve a problem, these rules align with the JetBrains IntelliJ Platform UI Guidelines — the authority for IntelliJ-styled UX. Jewel's API follows those guidelines.

## How to Use This File

1. Identify the interaction shape: mutually exclusive pick, multi-select, pick-or-type, binary toggle, action row, etc.
2. Apply the rule below.
3. Cross-check the external JetBrains guideline linked at the end of the section when behavior is non-obvious.

## Mutually Exclusive Selection (pick exactly one)

| Shape | Use |
|---|---|
| 2–4 options, short labels | `RadioButtonRow` group under a `GroupHeader` |
| 2–4 options, very short labels, emphasizing toggle semantics | `SegmentedControl` / `SegmentedControlButton` |
| 5+ options, long labels, limited space, or less-frequent setting | `ListComboBox` / `ComboBox` |
| User may enter a custom value in addition to picking | `EditableComboBox` |

Do **not** build a row of 3 `DefaultButton`s (or `OutlinedButton`s) as an exclusive selector — that's an action row, not a selection control.

Source: [Radio Button](https://plugins.jetbrains.com/docs/intellij/radio-button.html), [Combo Box](https://plugins.jetbrains.com/docs/intellij/combo-box.html).

## Multi-Select and Toggles

| Shape | Use |
|---|---|
| Independent boolean items | Group of `CheckboxRow` in a `Column` |
| Items share a "select all" parent | `ThreeStateCheckbox` as parent + `CheckboxRow` children |
| Binary yes/no with a clear default state | Single `Checkbox` / `CheckboxRow` |
| Binary yes/no where the "off" state is unclear from the label | Two `RadioButton`s with explicit labels for both states |

Source: [Checkbox](https://plugins.jetbrains.com/docs/intellij/checkbox.html).

## Pick-or-Type

| Shape | Use |
|---|---|
| Predefined values, no custom input allowed | `ListComboBox` / `Dropdown` |
| Predefined values + allow custom input | `EditableComboBox` |
| Very large list; user already knows the value | `TextField` with completion (not a combo box) |
| No initial values available | `TextField` — never show an empty combo box |

Source: [Combo Box](https://plugins.jetbrains.com/docs/intellij/combo-box.html).

## Action Buttons

| Shape | Use |
|---|---|
| Primary action in a form, dialog, or confirmation | `DefaultButton` |
| Secondary / cancel / alternative action | `OutlinedButton` |
| Row of primary actions (rare) | One `DefaultButton` + `OutlinedButton`s; never two `DefaultButton`s |
| Icon-only action | `IconButton` / `IconActionButton` |

The primary action must be visually distinct. Destructive actions (delete, discard, remove) are still the primary button of the dialog — paired with an outlined "Cancel" — but should use clear, imperative labels (`"Delete"`, not `"Yes"`) so users don't confirm by reflex.

## Tooltips for Unlabeled Controls

Every icon-only or unlabeled interactive control **must** be wrapped in a Jewel `Tooltip` (or `TooltipArea`). This is not a judgement call about whether the icon is "obvious" — the rule is uniform.

- Tooltip content includes **both** the action name **and** the keyboard shortcut (when one exists). Example: `"Refresh (Ctrl+R)"`.
- The `Icon` inside still gets a meaningful `contentDescription` for assistive tech — the tooltip is for sighted users, the `contentDescription` is for screen readers; both should carry the action name.
- Do not ship an `IconButton` / `IconActionButton` / `SelectableIconActionButton` / `ToggleableIconActionButton` without a `Tooltip`, even if the icon seems obvious in context.

```kotlin
Tooltip(tooltip = { Text("Refresh (Ctrl+R)") }) {
    IconButton(onClick = ::refresh) {
        Icon(key = MyIcons.Refresh, contentDescription = "Refresh")
    }
}
```

Source: [Tooltip](https://plugins.jetbrains.com/docs/intellij/tooltip.html).

## Text Input Sizing

| Shape | Use |
|---|---|
| Short, single-line input (few words) | `TextField` |
| Unconstrained, multi-line text; newlines valid (commit messages, descriptions, code) | `TextArea` |
| Read-only display text | Plain `Text` — not a disabled `TextField` |

`TextArea` sizing conventions per the IntelliJ Text Area guideline:

- Minimum height **~3 lines** (~55 px) so the multi-line affordance is visible at rest.
- Width **~270 px minimum, ~600 px maximum** (~80-column target for code-adjacent content).
- Size to an integral line count; avoid auto-resize.
- No units glyph to the right of the area; if units are needed, put them in the label.

Source: [Text Area](https://plugins.jetbrains.com/docs/intellij/text-area.html).

## Numeric Input

| Shape | Use |
|---|---|
| Continuous bounded value with direct-manipulation intent (volume, zoom, opacity) | `Slider` |
| Precise numeric entry with validation | `TextField` (numeric) |
| Both direct manipulation and precise entry | `Slider` + `TextField` bound to the same state |
| Discrete preset (Low / Medium / High) | `SegmentedControl` or `RadioButtonRow` group — not `Slider` |

`Slider` notes:

- Always show the current value nearby (a trailing `Text(value.toInt().toString())` in a `Row` is the canonical shape). The slider alone does not communicate the exact number.
- Set `valueRange` explicitly (e.g. `0f..100f`).
- Use `steps = n-1` for integer snapping across `n` whole-number stops; omit for smooth continuous.
- Slider works in `Float`; convert to `Int` only at display / persistence boundaries.

## Feedback and Progress

### Banner severity

Use a Jewel `InlineBanner` (attached to the affected component) or `DefaultBanner` (app-level) when attention is needed but the state is not immediate. Pick severity by impact:

| Severity | When |
|---|---|
| Information | Optional context that doesn't change the user's path (e.g. "3 files indexed") |
| Warning | Workflow-impacting state the user should address (e.g. "Workspace out of sync — sync before editing") |
| Error | Required to unblock (e.g. "Can't save: credentials expired") |

Rules:

- Place the banner at the **top of the affected component**, not over it.
- **≤2 sentences** of body text; **≤2 actions** in the banner's action area.
- Prefer `Link` over `Button` for banner actions.
- Do not use a banner when the state can't be tied to a specific UI component — reach for a platform notification / balloon instead.

### Progress indicators

| Shape | Use |
|---|---|
| Duration is known (file count, byte total, step index) | `HorizontalProgressBar` (determinate) |
| Duration is unknown | `IndeterminateHorizontalProgressBar` or `CircularProgressIndicator` (compact) |

Rules:

- **Run in the background** rather than a modal dialog — the user should keep working during long operations.
- Pair the bar with a short status label (`"Compiling project…"`). The bar alone doesn't convey what's happening.
- **Cancel vs Stop** — use `"Cancel"` for safely interruptible work; use `"Stop"` only when the interruption is irreversible.
- **Do not offer a Pause action.** The IntelliJ guideline explicitly prefers background execution over pause.
- **Dismiss the indicator on completion** — don't leave it visible after the work finishes.

Sources: [Banner](https://plugins.jetbrains.com/docs/intellij/banner.html), [Progress Bar](https://plugins.jetbrains.com/docs/intellij/progress-bar.html).

## Tabs

Use Jewel `Tabs` / `TabStrip` for peer content views (document tabs, section tabs within a single surface). For **navigation between many app sections**, reach for a navigation rail pattern instead (Pattern 1 in LAYOUT-PATTERNS.md).

Rules:

- **Auto-hide at 8+ tabs** into a dropdown / "more" affordance — a horizontal strip of 8+ tabs loses legibility and keyboard affordance.
- **Never disable a tab.** If a tab's content is unavailable, render the explanation inside the tab content, not by disabling the tab.
- **Label length: max 3 words.** Short, sentence-style capitalization, no trailing punctuation.
- Position tabs **above** their content, aligned to the container borders.
- For 12+ destinations that feel tab-like but are really navigation, use the nav-rail-plus-content pattern (Pattern 1).

Source: [Tabs](https://plugins.jetbrains.com/docs/intellij/tabs.html).

## Validation Errors

Present per-field validation errors **inline**, directly below the offending field. Not in a dialog, not in a tooltip, not in a banner, not in a toast / notification.

| Concern | Use |
|---|---|
| Field outline (focused or not) | `Modifier`/styling that maps to `JewelTheme.globalColors.outlines.error` and `.focusedError` |
| Error message text | `InfoText` (or a plain `Text` using `JewelTheme.globalColors.text.error` + `JewelTheme.typography.small`) |
| Message wording | Short, imperative, specific — `"Enter a valid email address"`, not `"Invalid"` or `"Error"` |

Rules:

- Keep outline + text + border **coherent** — all three should communicate the same error state via the semantic `outlines.error` / `text.error` tokens. Do not hardcode hex values in the component.
- Validate on blur or on submit, not on every keystroke.
- Clear the error as soon as the user starts correcting the input.
- Reserve `InlineBanner` for **surface-level** status (e.g. "Save failed: network error"), not single-field validation. A form that fails at submit time can combine: per-field inline errors plus a top-of-form banner.

Sources: [Input Field](https://plugins.jetbrains.com/docs/intellij/input-field.html), Jewel [THEMING-COLORS.md](THEMING-COLORS.md).

## Search Affordances

IntelliJ has a Search Field guideline, but Jewel does not ship a dedicated `SearchField` composable. Two shapes cover the space:

| Interaction | Use |
|---|---|
| Persistent search bar that filters a list / view (global or pane-level) | `TextField` with a leading `Icon(key = AllIconsKeys.Actions.Search, …)`; standard `TextField` state API |
| In-list filter-as-you-type, keyboard-first navigation | `SpeedSearchArea` wrapping the list + one of `SpeedSearchScope.SpeedSearchableLazyColumn` / `SpeedSearchableTree` / `SpeedSearchableComboBox` |

Rules:

- Do not label the field `"Search"` — the magnifying-glass icon is self-explanatory. Use the placeholder for scope context instead (`"Filter users"`, `"Search in project"`).
- Never show an empty combo box in place of a search field. If there are no options yet, fall back to a plain `TextField`.
- For large lists where the user already knows the value, consider a `TextField` with completion rather than a combo box.

Sources: [Search Field](https://plugins.jetbrains.com/docs/intellij/search-field.html), [Combo Box](https://plugins.jetbrains.com/docs/intellij/combo-box.html).

## Group Header Threshold

`GroupHeader` is valuable for **larger** groups of controls. For **≤3 controls** use vertical insets / spacing instead — a header adds visual weight without payoff at small group sizes.

| Situation | Use |
|---|---|
| 4+ related controls forming a logical section | `GroupHeader` + the controls in a `Column` |
| 2–3 related controls | Plain `Column` with `Arrangement.spacedBy(...)`; no header |
| A single control in a "settings row" | Label inline with the control; no header |

Do not substitute per-row `Card` or per-row `Divider` for a header — those add more visual weight than a `GroupHeader` would have.

Source: [Group Header](https://plugins.jetbrains.com/docs/intellij/group-header.html).

## Writing Component Labels

Label-writing conventions (capitalization, punctuation, negation, button-case exception, placeholder-vs-label, link text, external-link icons) live in a dedicated file so selection rules and label rules can evolve independently. See [LABEL-RULES.md](LABEL-RULES.md).

## Canonical Source Links

Authority: JetBrains IntelliJ Platform UI Guidelines. The Jewel API implements these components; the guidelines define the UX contract.

- [Components index](https://plugins.jetbrains.com/docs/intellij/components.html)
- [Principles](https://plugins.jetbrains.com/docs/intellij/principles.html) — layout, typography, validation errors, platform theme colors
- [Radio Button](https://plugins.jetbrains.com/docs/intellij/radio-button.html)
- [Checkbox](https://plugins.jetbrains.com/docs/intellij/checkbox.html)
- [Combo Box](https://plugins.jetbrains.com/docs/intellij/combo-box.html)
- [Button](https://plugins.jetbrains.com/docs/intellij/button.html)
- [Group Header](https://plugins.jetbrains.com/docs/intellij/group-header.html)
- [Tooltip](https://plugins.jetbrains.com/docs/intellij/tooltip.html)
- [Banner](https://plugins.jetbrains.com/docs/intellij/banner.html)
- [Progress Bar](https://plugins.jetbrains.com/docs/intellij/progress-bar.html)
- [Tabs](https://plugins.jetbrains.com/docs/intellij/tabs.html)
- [Text Area](https://plugins.jetbrains.com/docs/intellij/text-area.html)
- [Input Field](https://plugins.jetbrains.com/docs/intellij/input-field.html)
- [Search Field](https://plugins.jetbrains.com/docs/intellij/search-field.html)

For label-writing source links, see [LABEL-RULES.md](LABEL-RULES.md).
