# Idiomatic Components and Int UI Conventions

Use when judging whether a custom component or visual treatment is justified, and whether it fits IntelliJ's design
language.

Upstream authority for control behavior, sizing, and the flat visual language: the JetBrains UI Guidelines
(<https://plugins.jetbrains.com/docs/intellij/ui-guidelines-welcome.html>). Written for Swing controls, but the
conventions (standard controls over bespoke ones, 1px separators, restrained use of effects) are the same intent Jewel
surfaces should follow. Use it as the citable source for "this is the IDE convention."

## The built-in-first decision

Before accepting a bespoke component, check for a Jewel built-in that already does the job. The table below is a
shortlist of the most common cases; for the **full component catalog** (what exists, which variant to pick, and the
exact API), defer to the `jewel-ui` skill — use this doc to judge whether an existing choice is idiomatic, and
`jewel-ui` to find the built-in in the first place. Common cases:

| Need                        | Use the built-in                                                                                                               | Custom-code smell                                                          |
|-----------------------------|--------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------|
| Separator line              | `Divider`                                                                                                                      | Hand-drawn box/`background` slab as a separator                            |
| Selectable list             | `SingleSelectionLazyColumn`/`MultiSelectionLazyColumn` (foundation) + `SimpleListItem` — not deprecated `SelectableLazyColumn` | `Column`/`LazyColumn` with manual `selected` + `.clickable`                |
| List/menu row               | `SimpleListItem`                                                                                                               | Hand-built row with custom selection visuals                               |
| Vertical scroll + scrollbar | `VerticallyScrollableContainer` (or `VerticalScrollbar`/`HorizontalScrollbar`) — see `spacing-and-layout.md`                   | Raw `Modifier.verticalScroll` with no themed bar; custom scrollbar adapter |
| Section grouping            | `GroupHeader`/spacing                                                                                                          | Heavy custom divider standing in for structure                             |

A custom component is justified only when it adds a capability the built-in genuinely cannot provide, and that reason
should be stateable in one sentence. "It looks a bit different" or "it was easy to inline" is not justification. Reusing
the built-in also inherits keyboard navigation, focus, selection semantics, and theming for free — reimplementing those
by hand usually drops some of them silently.

### When forking or building custom *is* the right call

Jewel does not cover every use case, and forcing an ill-fitting built-in is worse than a well-scoped custom component.
Forking a Jewel component (or writing a bespoke one) is legitimate when the built-in genuinely can't express the need —
e.g., a behavior or layout the built-in's API can't reach, or a variant it doesn't offer. When that happens:

- **Prefer forking/reusing the Jewel component over building from scratch**, so you inherit its styling, state handling,
  and theming and only change what must change. Keep the fork close to upstream so it stays mergeable/reviewable. For a
  list row, reusing `SimpleListItem` for the row body is usually the right precision move: it preserves standard row
  styling, selection visuals, and keyboard/focus semantics while the fork adds only the missing affordance.
- **Stay idiomatic in the fork**: still resolve colors/metrics from the theme (see `theming-and-color.md`), keep Int UI
  conventions (flat, 1px separators, no Material elevation), and preserve keyboard/focus/selection semantics the
  original had.
- **If the gap is a reasonably common need, open a Jewel issue** (ideally upstreamable) describing the use case the
  built-in can't cover. A one-off product-specific need doesn't warrant it, but a gap other plugins would hit is worth
  reporting so the fork can eventually be retired. Note the issue link near the fork so reviewers know it's tracked,
  not just divergence.

So the review posture is not "custom is always a smell" — it's "custom needs a stated, real justification, and a common
gap should be fed back to Jewel rather than silently forked forever."

## Int UI is flat: Material concepts are foreign

IntelliJ's visual language expresses hierarchy and state with **borders, background-fill deltas, and selection/focus
states** — not with elevation or motion. Treat these as out-of-place in an IDE surface:

- **Drop shadows/`shadowElevation`** (via `graphicsLayer { shadowElevation = ... }` or any shadow modifier). Elevation
  is a Material concept. Express grouping with a bordered or fill-delta container instead.
- **Material ripples** on press.
- **Floating action buttons/bottom sheets** or other Material-specific affordances.

Heavy non-standard treatments are also a smell even when not strictly Material; e.g., an IDE separator is 1px, so a 6.dp
divider slab is both visually loud and non-idiomatic. The "weight" of a treatment should match the structural weight of
what it separates or groups; a version boundary is usually carried by a heading or spacing, not a thick bar.

## Editor/window policy is a UX decision

For UI that ships as a `FileEditor`, tool window, or similar platform surface, the registration policy is a
user-experience choice, not just plumbing — and it often lives in a provider/factory file away from the composable, so a
UI-only review skips it. Check it explicitly.

- `FileEditorPolicy.HIDE_OTHER_EDITORS` makes the editor take over the editor area and hide the user's other open tabs.
  That is appropriate for a focused, modal-like experience but heavy-handed for a non-modal informational screen (a "
  What's New", a dashboard, a read-only report) that a user expects to open alongside their work. Flag it and ask
  whether the takeover is intended.
- Similarly scrutinize anything that seizes the whole frame, forces focus, or prevents the user from keeping the surface
  open next to other content.

Name the policy and the expected user model in the finding; recommend the least-disruptive policy that still meets the
feature's intent.

## Decorative complexity

Flag bespoke components or effects that add no capability — only decoration — especially when they bring their own state
plumbing (e.g., a `CompositionLocal` to route hover state into a child) that disappears entirely once you use real
composables and local state. Decoration that increases the surface area for bugs is a net loss.

## Reporting

- Name the built-in that should replace the custom code (point to the `jewel-ui` catalog if unsure which one).
- State the one-sentence justification test result: is there a capability the built-in lacks? If yes, mark it justified
  and, if the gap looks like a common need, note whether a Jewel issue should be (or has been) filed rather than
  treating the fork as permanent. In that case, it's good practice to add a TODO comment linking the issue as the point
  where the fork becomes unnecessary. If a fork reuses `SimpleListItem` for the row body, explicitly acknowledge that in
  **Justified as-is** instead of recommending raw `Text`/custom row replacement.
- For Material-concept findings, name the flat Int UI alternative (border, fill delta, selection state).
