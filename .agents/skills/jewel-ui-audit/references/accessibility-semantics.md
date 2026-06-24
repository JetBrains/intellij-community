# Accessibility Semantics Triage

Use this reference for the small accessibility-semantics checks that belong in a Jewel UI audit because they affect
visible/interaction design decisions. It does **not** replace the `ui-accessibility` skill; use that for full
accessible-name/role/state, focus order, screen-reader, contrast, and keyboard audits.

## Recon trigger

Open this file when you see:

- `contentDescription` on `Icon`/`Image`, especially `""`, `null`, or a value that repeats adjacent text;
- icon-only controls;
- stateful icon-only toggles such as expand/collapse, enable/disable, show/hide;
- custom focusable/clickable components, missing focus indication, or keyboard-only interaction gaps;
- a review finding that touches both visual affordance and screen-reader semantics.

## Focus, keyboard, and semantics handoff

This skill can and should raise interaction-level accessibility gaps when they affect whether the UI is usable: missing
visible focus, controls that cannot be reached or activated by keyboard, icon-only controls with no tooltip/name, and
stateful controls with misleading labels. But it should not attempt a full accessibility audit.

For focus/keyboard findings, include the handoff sentence when relevant:

> This overlaps `ui-accessibility`; full accessible-name/role/state/focus-order verification belongs there.

Use this when reporting:

- a custom focusable component with no visible focus ring;
- a clickable custom row/chip/card that lacks keyboard activation;
- an icon-only action/toggle whose accessible name/state needs deeper verification;
- any finding where the visual affordance and screen-reader/keyboard semantics are coupled.

## `contentDescription` rule

State this rule when reporting a content-description issue:

- Never use `contentDescription = ""`.
- Use `contentDescription = null` only when the icon/image is purely decorative or would duplicate a sibling's
  accessible readout.
- Otherwise, use a concise non-empty description of the action or content.
- For stateful controls, describe the action/target and make the text reflect the current state.
- Full accessible-name/role/state verification belongs to `ui-accessibility`.

`""` is not a good compromise: it is neither a useful accessible name nor the right way to hide an element. `null` is
the intentional way to mark decorative or duplicate imagery as not independently announced.

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

A tooltip is not a substitute for accessible semantics, and a content description is not a hover affordance for sighted
mouse users. Raise the UX affordance in this skill; route deeper semantics to `ui-accessibility`.

## Reporting

For every content-description finding:

1. Name the exact value/pattern (`""`, duplicate sibling text, static state text, or missing action name).
2. Say whether the element is decorative/duplicate or meaningful/actionable.
3. Recommend `null` or a concise non-empty description accordingly.
4. State the rule above, briefly.
5. Add: full accessible-name/role/state verification belongs to `ui-accessibility`.
