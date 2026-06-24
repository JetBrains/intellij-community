# Idiomatic Components and Int UI Conventions

Use when judging whether a custom component or visual treatment is justified, and whether it fits IntelliJ's design
language.

Upstream authority for control behavior, sizing, and the flat visual language: the JetBrains UI Guidelines
(<https://plugins.jetbrains.com/docs/intellij/ui-guidelines-welcome.html>). Written for Swing controls, but the
conventions (standard controls over bespoke ones, 1px separators, restrained use of effects) are the same intent Jewel
surfaces should follow. Use it as the citable source for "this is the IDE convention."

## The built-in-first decision

Before accepting a bespoke component, check for a Jewel built-in that already does the job. Defer to the `jewel-ui`
skill for the full component catalog, variants, and exact APIs. Use this reference to judge whether an existing choice
is idiomatic:

| Need                        | Use the built-in                                                                                                               | Custom-code smell                                                          |
|-----------------------------|--------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------|
| Separator line              | `Divider`                                                                                                                      | Hand-drawn box/`background` slab as a separator                            |
| Selectable list             | `SingleSelectionLazyColumn`/`MultiSelectionLazyColumn` (foundation) + `SimpleListItem` — not deprecated `SelectableLazyColumn` | `Column`/`LazyColumn` with manual `selected` + `.clickable`                |
| Popup menu / Filter menu    | `PopupMenu` with `SelectableMenuItem` / `MenuItem`                                                                             | Hand-rolled `PopupContainer`/`Column` with manual hover/selection rows     |
| List/menu row               | `SimpleListItem` / `SelectableMenuItem`                                                                                         | Hand-built row with custom selection visuals                               |
| Vertical scroll + scrollbar | `VerticallyScrollableContainer` (or `VerticalScrollbar`/`HorizontalScrollbar`) — see `spacing-and-layout.md`                   | Raw `Modifier.verticalScroll` with no themed bar; custom scrollbar adapter |
| Section grouping            | `GroupHeader`/spacing                                                                                                          | Heavy custom divider standing in for structure                             |

A custom component is justified only when it adds a capability the built-in lacks, stated in one sentence. "It looks a
bit different" or "it was easy to inline" is not justification. Reusing the built-in inherits keyboard navigation,
focus, selection semantics, and theming that hand-rolled implementations often drop.

### When forking or building custom *is* the right call

Jewel does not cover every use case, and forcing an ill-fitting built-in is worse than a well-scoped custom component.
Forking or writing a custom component is legitimate when the built-in API cannot express the required behavior, layout,
or variant:

- **Prefer forking/reusing the Jewel component over building from scratch** to inherit its styling, state handling, and
  theming while changing only what is necessary.
  - Keep the fork close to upstream so it stays mergeable. For list rows, reuse `SimpleListItem` for the row body to
    preserve row styling, selection visuals, and keyboard/focus semantics.
- **Stay idiomatic in the fork**: still resolve colors/metrics from the theme (see `theming-and-color.md`), keep Int UI
  conventions (flat, 1px separators, no Material elevation), and preserve keyboard/focus/selection semantics the
  original had.
- **If the gap is a common need, open a Jewel issue** describing the use case the built-in cannot cover so the fork can
  later be retired. Link the issue near the fork so reviewers know it is tracked rather than accidental divergence.

Require a stated justification for any custom component, and file a Jewel issue for common gaps rather than maintaining
silent permanent forks.

## Int UI is flat: Material concepts are foreign

IntelliJ's visual language expresses hierarchy and state with **borders, background-fill deltas, and selection/focus
states** — not with elevation or motion. Treat these as out-of-place in an IDE surface:

- **Drop shadows/`shadowElevation`** (via `graphicsLayer { shadowElevation = ... }` or any shadow modifier). Elevation
  is a Material concept. Express grouping with a bordered or fill-delta container instead.
- **Material ripples** on press.
- **Floating action buttons**, **bottom sheets**, and other Material-specific affordances belong to Material rather than
  Int UI.

Heavy non-standard treatments are also a smell even when not strictly Material. Because an IDE separator is 1px, a 6.dp
divider slab is visually loud and non-idiomatic. Match the treatment's weight to the structural weight of what it
groups; use a heading or spacing for a version boundary, not a thick bar.

## Editor/window policy is a UX decision

For UI that ships as a `FileEditor`, tool window, or similar platform surface, the registration policy is a UX choice.
Because it lives in a provider or factory file away from the composable, check it explicitly:

- `FileEditorPolicy.HIDE_OTHER_EDITORS` takes over the editor area and hides the user's other open tabs. That fits a
  focused modal-like flow, but is disruptive for a non-modal informational screen (such as "What's New", a dashboard, or
  a read-only report) opened alongside work. Flag it and confirm whether the takeover is intended.
- Similarly scrutinize anything that seizes the whole frame, forces focus, or prevents the user from keeping the surface
  open next to other content.

Name the policy and the expected user model in the finding; recommend the least-disruptive policy that still meets the
feature's intent.

## Decorative complexity

Flag bespoke components or effects that add only decoration. Watch for decorative state plumbing (such as a
`CompositionLocal` routing hover state to a child) that standard composables and local state do not need. Decoration
that increases bug surface area is a net loss.

## Reporting

- Name the built-in that should replace the custom code (point to the `jewel-ui` catalog if unsure which one).
- State the one-sentence justification test result: does the built-in lack a required capability?
  - If justified and the gap is a common need, recommend filing a Jewel issue and adding a `TODO` comment linking it.
  - If a fork reuses `SimpleListItem` for the row body, acknowledge that in **Justified as-is** instead of recommending
    raw `Text`/custom rows.
- For Material-concept findings, name the flat Int UI alternative (border, fill delta, selection state).
