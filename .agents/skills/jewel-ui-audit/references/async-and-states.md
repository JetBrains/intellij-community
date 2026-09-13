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
  field-level inline messages, `Outline.Error`/the themed error role, and feedback as the user types or on focus loss.
  If field-level errors already exist only after `touched`/`edited` flags become true, check the initial invalid path:
  a disabled button plus tooltip still leaves the user with no visible actionable feedback until they guess what to
  edit.

## Every async-backed surface needs Loading and Error, not just Ready

A composable gated behind async work must define what renders in each state. The common defect is an early return into
blankness:

```kotlin
if (parsed.isEmpty()) return   // nothing renders while work is in flight
```

Generally, a blank first paint reads as broken. Require an explicit Loading state (spinner, skeleton, or shimmer) and an
explicit Error state. A clean way to make this unavoidable is a sealed UI-state type (`Loading`/`Ready`/`Error`) so
the `when` forces every branch to be handled — but the requirement is the states, not a specific encoding.

The happy path passing review is not evidence the states exist; it is the reason they get forgotten. Check the gating
condition specifically.

## Async images: three rendered states, never a silent gap

An async image must handle loading and failure visibly and must not shift layout if at all possible:

1. **Loading**: reserve space (based on the image size when available, or a plausible fixed aspect-ratio box) and show a
   loading indicator/shimmer. Rendering `null` or returning from the composable before emitting any image-sized node
   causes surrounding text to start higher/earlier and then reflow when the image arrives — cumulative layout shift.
   Padding around the eventual `Image` is not reserved space if that branch is not emitted while loading. Reserving
   space upfront prevents the jump even before the real aspect ratio is known.
2. **Loaded**: the image, sized to the reserved area.
3. **Error**: a visible affordance — alt text, a broken-image placeholder, optionally retry. Logging the failure and
   leaving the painter null forever produces an invisible permanent gap, which is the worst outcome because nothing
   tells the user (or a screenshot reviewer) anything is missing.

## Offline and sourcing consistency

If a screen's text is bundled/offline-capable but its images are fetched over the network at render time, the screen
partially fails exactly when connectivity is worst (right after an update, behind a corporate proxy or captive portal) —
and per the rule above, that failure is often silent. Flag inconsistent sourcing. Route remote media through the
platform's standard image-loading path (which manages threading, caching, and error states) rather than ad-hoc
fetch-and-decode, and prefer a bounded cache over an unbounded one. Consider disk caching if necessary.

## Affordance labeling for state toggles (overlaps accessibility)

This overlaps the `ui-accessibility` skill; raise it here when reviewing the visual/UX affordance, and defer deeper a11y
review there.

- An icon-only toggle (e.g., an expand/collapse chevron) needs a `contentDescription` that (a) names the action/target,
  not the widget, and (b) reflects current state. A static `"Menu"` fails both: it does not say what expands, and it
  does not change between expanded and collapsed.
- Consider providing tooltips for icon buttons (an expected IDE affordance; see the tooltip guidance in
  `interaction-and-affordances.md`).
- A default-collapsed disclosure that hides a primary affordance (e.g., a navigation panel collapsed by default with
  only an unlabeled arrow) has a discoverability problem: a first-time user gets no signal the content exists. Consider
  a label, a default-expanded state, or another discovery cue.

## Progress indication: pick the right kind

Any operation with a perceptible duration needs progress feedback, and the *kind* matters:

- **Determinate** progress (a real percentage/step count) whenever the total is known — downloads, multistep tasks,
  iteration over a known collection. Showing an indeterminate spinner when you could show real progress hides useful
  information and makes the operation feel stuck.
- **Indeterminate** (spinner/shimmer) only when the duration is genuinely unknown.
- For very short or background work, no blocking indicator at all may be right — don't gate the whole surface behind a
  spinner for sub-perceptible work (this ties back to the Loading-state rule above: reserve space/skeleton rather than
  a blank or a modal spinner).
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

Flag a form that only blocks submission with no inline reason, a validation message that doesn't localize the failing
field, and any error styling that hardcodes red instead of the themed error role. Also flag the subtler version where
inline errors exist behind `edited`/`touched` gates, but the initial invalid form starts with a disabled primary button
and no visible field guidance; a disabled-button tooltip is not an adequate discovery path.

## Absence checklist (findings the code cannot point you to)

The defects above are visible in code you can read. A different and easily-missed class is the **expected thing that is
simply absent** — there is no line to grep, so a code-reading pass structurally cannot surface it. Run this checklist
explicitly against the feature's purpose and report anything missing as a finding:

- **Loading and error states exist** for every async-backed view (covered above) — confirmed present, not just
  happy-path.
- **Offline/failure story is defined** for any network dependency, rather than silent failure.
- **"New since last seen"/unread state**: for changelog, What's-New, notification, inbox, or feed surfaces, is there
  any tracking of what the user has already seen, and any visual distinction for new items? Presenting every item with
  equal weight on every visit defeats the core purpose of these surfaces. The absence of any last-seen/seen-state
  mechanism is itself the finding.
- **Empty state** is designed (not a blank panel) when the data set can legitimately be empty.
- **Discoverability** of primary affordances that are collapsed/hidden by default (see the toggle-labeling note above).
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
