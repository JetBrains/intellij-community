# Async State Completeness and Media

Use when a screen or component depends on asynchronous work (parsing, IO, network, image loading).

## Quick triggers

- **Blank while loading:** if a branch returns early or renders nothing while async work is pending, flag the missing
  Loading state and reserve space instead of shifting layout.
- **Async image gap:** if an image branch returns before rendering `Image`/placeholder, require a fixed-size or
  aspect-ratio placeholder plus loading affordance. Do not assume space is reserved unless a visible `Box`/`Spacer`/
  `size`/`aspectRatio` remains in the loading branch.
- **Silent image failure:** if errors are only logged or the image stays absent forever, require visible alt text,
  broken-image placeholder, or retry/error affordance.
- **Ad-hoc fetch/decode:** if code fetches media manually (for example, direct HTTP request and decode), prefer the
  platform's standard image-loading path for threading, caching, and error states.
- **Mixed offline/remote data:** if names/bios/copy are bundled but images require network at render time, flag the
  offline/remote inconsistency.
- **Validation by disabled button only:** if `enabled = isValid` or a disabled "submit" is the only feedback, require
  field-level inline messages and `Outline.Error` as the user types or on focus loss.
  - If field-level errors appear only after `touched`/`edited` flags become true, check the initial invalid path: a
    disabled button plus tooltip leaves the user guessing what to edit.

## Every async-backed surface needs Loading and Error, not just Ready

A composable gated behind async work must define what renders in each state. The common defect is an early return into
blankness:

```kotlin
if (parsed.isEmpty()) return   // nothing renders while work is in flight
```

A blank first paint reads as broken. Require an explicit Loading state (spinner, skeleton, or shimmer) and an explicit
Error state. A sealed UI-state type (`Loading`/`Ready`/`Error`) forces `when` branches to handle every state, though any
encoding that renders all three states is valid.

Review the async gating condition directly to verify that the composable renders explicit Loading and Error states
alongside the Ready state.

## Async images: three rendered states, never a silent gap

An async image must handle loading and failure visibly without shifting layout:

1. **Loading**: reserve space (from known image dimensions or a fixed aspect-ratio box) and show a loading indicator or
   shimmer. Returning `null` or exiting before emitting an image-sized node causes surrounding text to reflow when the
   image arrives (cumulative layout shift). Padding on the eventual `Image` branch does not reserve space while loading.
2. **Loaded**: the image, sized to the reserved area.
3. **Error**: a visible affordance — alt text, a broken-image placeholder, optionally retry. Logging the failure and
   leaving the painter null forever produces an invisible permanent gap, which is the worst outcome because nothing
   tells the user (or a screenshot reviewer) anything is missing.

## Offline and sourcing consistency

When a screen bundles its text offline but fetches images over the network at render time, images fail behind corporate
proxies, captive portals, or offline updates. Combined with missing error states, those failures are silent.

Flag inconsistent sourcing. Route remote media through the platform's standard image-loading path (for threading,
caching, and error states) instead of ad-hoc fetch-and-decode. Prefer a bounded cache over an unbounded one, and use
disk caching when needed.

## Affordance labeling for state toggles (overlaps accessibility)

This overlaps the `ui-accessibility` skill; raise it here when reviewing the visual/UX affordance, and defer deeper a11y
review there.

- An icon-only toggle (e.g., an expand/collapse chevron) needs a `contentDescription` that (a) names the action/target,
  not the widget, and (b) reflects current state. A static `"Menu"` fails both: it does not say what expands, and it
  does not change between expanded and collapsed.
- Consider providing tooltips for icon buttons (an expected IDE affordance; see the tooltip guidance in
  `interaction-and-affordances.md`).
- A default-collapsed disclosure that hides a primary affordance (such as a navigation panel collapsed with only an
  unlabeled arrow) hurts discoverability because first-time users see no signal the content exists. Add a label,
  default-expanded state, or discovery cue.

## Progress indication: pick the right kind

Any operation with a perceptible duration needs progress feedback, and the *kind* matters:

- **Determinate** progress (a real percentage/step count) whenever the total is known — downloads, multistep tasks,
  iteration over a known collection. Showing an indeterminate spinner when you could show real progress hides useful
  information and makes the operation feel stuck.
- **Indeterminate** (spinner/shimmer) only when the duration is genuinely unknown.
- For sub-perceptible or background work, avoid blocking indicators. Do not gate the whole surface behind a modal
  spinner for fast work; reserve space or show a skeleton instead.
- This guidance applies primarily to the UI layer; for IDE-level background activity, use the IJPL APIs. You can then
  reflect the ongoing loading operation in the Jewel UI too, if it is fetching data needed to show content.

Flag an indeterminate indicator used where the total is known, and a long operation with no progress feedback at all.

## Form validation and inline error feedback

For any input surface (settings panels, dialogs, forms), validation is a required state, not an afterthought:

- **Invalid input is surfaced inline**, next to the offending field, with a specific message — not only via a disabled
  "submit" button or a failure after submitting. IntelliJ's convention is field-level validation (e.g., an
  `Outline.Error` + message) as the user types, on focus loss, or after a submission attempt.
- **The message says what's wrong and how to fix it**, not just "invalid."
- **Error state is themed** (use the error role/outline, not a hardcoded red — see `theming-and-color.md`), and clears
  when corrected.
- Distinguish **error** (blocking, must fix) from **warning** (allowed but risky) where the domain calls for it.

When auditing forms, check that invalid inputs show field-level inline reasons rather than only blocking submission,
localize the failing field, and use the themed error role instead of hardcoded red. Also verify that `edited` or
`touched` gates do not hide inline errors on an initially invalid form behind a disabled primary button and tooltip.

## Absence checklist (findings the code cannot point you to)

The defects above are visible in code you can read. Expected capabilities that are absent from the code have no line to
grep, so a code-reading pass alone misses them. Run this checklist against the feature's purpose and report missing
items as findings:

- **Loading and error states**: confirm that every async-backed view renders explicit Loading and Error states in
  addition to the Ready state.
- **Offline/failure story is defined** for any network dependency, rather than silent failure.
- **"New since last seen"/unread state**: changelog, What's-New, notification, inbox, and feed surfaces must track seen
  items and visually distinguish unread ones. Treating every item equally on every visit defeats the surface's purpose;
  missing seen-state tracking is itself a finding.
- **Empty state** is designed (not a blank panel) when the data set can legitimately be empty.
- **Discoverability**: verify that primary affordances collapsed or hidden by default provide a visible label or
  discovery cue.
- **Progress feedback** exists (and is determinate when the total is known) for any perceptibly long operation.
- **Inline validation** exists for input surfaces, rather than only a disabled/failing "submit" button.
- **Tooltips and context menus** are present where an IDE user expects them (icon-only controls, truncated text,
  right-click on actionable items or selectable text) — see `interaction-and-affordances.md`.

Derive the list from the feature type: ask "what does a user expect a screen like this to do that this implementation
does not?" and check each expectation against the code. When you flag an absence, state the user-facing consequence,
since there is no code line to cite.

## Reporting

- For state findings, name the missing state(s) (Loading/Error) and the gating line that returns into blankness.
- For absence findings, name the expected capability, why the feature type needs it, and the user-facing consequence of
  its absence (there will be no line number).
- For image findings, specify all three states and the layout-shift cause; recommend reserved aspect-ratio space and the
  standard image-loading path.
- For labeling findings, give the corrected description shape (action and state) and note the overlap with
  `ui-accessibility`.
