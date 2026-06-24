# Accessibility Semantics Triage

Use this reference for the small accessibility-semantics checks that belong in a Jewel UI audit because they affect
visible/interaction design decisions. It does **not** replace the `ui-accessibility` skill; use that for full
accessible-name/role/state, focus order, screen-reader, contrast, and keyboard audits.

## Recon trigger

Open this file when you see:

- Check `contentDescription` on `Icon` or `Image` calls, especially `""`, `null`, or values that repeat adjacent text.
- icon-only controls;
- Check stateful icon-only toggles such as expand/collapse, enable/disable, and show/hide controls.
- Check custom focusable or clickable components for missing focus indication or keyboard interaction gaps.
- a review finding that touches both visual affordance and screen-reader semantics.

## Focus, keyboard, and semantics handoff

Raise interaction-level accessibility gaps when they hurt usability: missing visible focus, controls unreachable by
keyboard, icon-only controls without a tooltip or name, and stateful controls with misleading labels. Do not attempt a
full accessibility audit here.

For focus/keyboard findings, include the handoff sentence when relevant:

> This overlaps `ui-accessibility`; full accessible-name/role/state/focus-order verification belongs there.

Use this when reporting:

- Include it when reporting a custom focusable component that lacks a visible focus ring.
- Include it when reporting a clickable custom row, chip, or card that lacks keyboard activation.
- Include it when reporting an icon-only action or toggle whose accessible name or state needs deeper verification.
- any finding where the visual affordance and screen-reader/keyboard semantics are coupled.

## `contentDescription` rule

State this rule when reporting a content-description issue:

- Never use `contentDescription = ""`.
- Use `contentDescription = null` only when the icon/image is purely decorative or would duplicate a sibling's
  accessible readout.
- Otherwise, use a concise non-empty description of the action or content.
- For stateful controls, describe the action/target and make the text reflect the current state.
- Full accessible-name/role/state verification belongs to `ui-accessibility`.

`""` provides neither a useful accessible name nor a hidden decorative state. Use `null` to mark decorative or duplicate
imagery so screen readers do not announce it independently.

## Mixed snippets: preserve correct `null`

In mixed code, review both the bad and good cases. Do not mechanically replace every `null` with text.

Correct `null` examples:

- a decorative leading icon next to `Text(pluginName)`;
- a status icon next to visible `Text("Enabled")`/`Text("Disabled")`;
- an avatar or illustration whose identity/content is already announced by nearby text and the image adds no separate
  information.

Findings to report:

- `contentDescription = ""` on an icon-only button: replace with a concise action description such as
  `"Configure plugin"`.
- `contentDescription = pluginName` on a leading icon followed by `Text(pluginName)`: duplicate announcement; use
  `null`.
- `contentDescription = "Menu"` or `"Chevron"` on a toggle: names the widget, not the action/target. Use e.g.,
  `"Expand details"`/`"Collapse details"`.

## Tooltips and accessible names are related but separate

Icon-only controls normally need both:

- a visible IDE affordance via `Tooltip(tooltip = { Text("Action") }) { ... }`;
- a meaningful non-empty accessible name/description for the icon/button semantics.

Tooltips only appear on hover for sighted mouse users, so controls still require accessible semantics, and
`contentDescription` alone provides no visible hover affordance. Raise the UX affordance in this skill and route deeper
semantics to `ui-accessibility`.

## Reporting

For every content-description finding:

1. Name the exact value/pattern (`""`, duplicate sibling text, static state text, or missing action name).
2. Say whether the element is decorative/duplicate or meaningful/actionable.
3. Recommend `null` or a concise non-empty description accordingly.
4. State the rule above, briefly.
5. Add: full accessible-name/role/state verification belongs to `ui-accessibility`.
