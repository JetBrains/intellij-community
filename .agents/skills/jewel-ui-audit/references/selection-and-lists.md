# Selection and Lists

Use when reviewing selectable lists/trees, text-selection scope and pointer behavior, and any "active item" state
derived from a list's scroll position. For hover/focus/keyboard affordances, tooltips, and context menus, see
`interaction-and-affordances.md`.

## Quick triggers

- **Manual selected rows in `Column`/`LazyColumn`:** prefer `SingleSelectionLazyColumn`/`MultiSelectionLazyColumn` with
  `SimpleListItem`; hand-rolled selection usually drops keyboard navigation and focus handling. `SimpleListItem` is the
  standard row component, not a smell.
- **Searchable/non-trivial list with no speed search:** recommend `SpeedSearchableLazyColumn`/`SpeedSearchArea` by
  default. Omit only when there is a good alternative, such as a visible type-to-filter/search field.
- When a root or ancestor `SelectionContainer` wraps cards or list rows, flag the I-beam cursor over non-text regions
  and selection bleed across row/card boundaries. Scope selection to text runs and inspect descendants for action
  controls that need `DisableSelection`, even when an ancestor composable declares the selection container.
- **Continuous prose exception:** broad selection can be correct for one long document/Markdown/prose continuum where
  cross-paragraph copying is expected.
- **Scroll position drives active item:** check end-of-list and empty/heterogeneous cases; first-visible-item logic
  often cannot ever select trailing items.

## Selectable lists: use the built-in

A selectable list must use a built-in selection container whose rows are usually `SimpleListItem`s. Prefer
`SingleSelectionLazyColumn` or `MultiSelectionLazyColumn` (both in `org.jetbrains.jewel.foundation.lazy`); the older
`SelectableLazyColumn` still exists but is **deprecated** in favor of those two.

`SimpleListItem` is the right choice for most list rows, not a smell. When placed in a selection container, it
preserves standard row styling, density, selection visuals, and keyboard/focus semantics. The built-in container owns:

- selection state (single/multi, correct semantics),
- keyboard navigation (arrow keys, home/end),
- focus handling.

The antipattern is a plain `Column` or `LazyColumn` where each row is a `SimpleListItem` (or custom row) wired with
`selected = (item == something)` and a manual `.clickable { }`. There, `SimpleListItem` is the right *row* in the wrong
*container*: manual wiring reimplements selection by hand and drops built-in keyboard navigation and focus handling.

Speed search (type-to-filter) is a separate Jewel facility rather than part of the selection column itself: use
`SpeedSearchableLazyColumn` or wrap the list in a `SpeedSearchArea` (alongside `SpeedSearchableTree` and
`SpeedSearchableComboBox`, all currently `@ExperimentalJewelApi`). Speed search is an expected IDE pattern for
non-trivial lists and is the default recommendation.

When reviewing a manual selected list, evaluate speed search as a separate loss from keyboard/focus selection
semantics. Skip it only when the surface already provides a type-to-filter/search mechanism, the list is tiny and
static, or product requirements specify a different search affordance. Do not claim a plain selection column provides
speed search automatically.

## Text selection scope and pointer

Scoping text selection too broadly (wrapping a large composite region in a `SelectionContainer`, or setting
`selectable = true` over a whole layout rather than specific text runs) introduces two defects:

- **I-beam everywhere.** A selection-enabled region shows the text I-beam cursor across its whole area, including over
  non-text parts (images, icons, padding, structural chrome). For a read-only informational surface, the I-beam is the
  wrong pointer affordance because it signals an editor or text field where there is none.
- **Selection crosses visual boundaries.** Whole-region selection lets the user rubber-band across elements meant to
  read as separate (discrete cards, list rows, structural gaps), producing highlights that span those boundaries.

Decide selectability deliberately and scope it tightly to the text runs where copying is a real use case. Do **not**
wrap an entire `LazyColumn`, card feed, or list of discrete rows in a single `SelectionContainer`; that puts an I-beam
over padding/icons and lets selection bleed across row/card boundaries.

The exception is a genuine continuum of selectable prose (such as paragraphs in one long document or Markdown article)
where cross-paragraph selection is expected.

Leave read-only informational surfaces non-selectable so the arrow pointer is shown. If broad copy support is required,
verify the cursor over non-text regions and confirm that selection does not bleed across boundaries, especially in card
or grid feeds.

When using the default UI text style/font and its derivatives, the mouse pointer must remain the default arrow rather
than an I-beam, even if the text is selectable. Making UI text selectable is a convenience rather than the primary
interaction; reserve the I-beam for editor-like surfaces.

Nesting controls inside selectable content requires explicit pointer and selection overrides. If a selectable Markdown
run contains a custom-rendered button, wrap that button in `DisableSelection` and force the arrow pointer icon so its
label is not selectable.

In reviews, scan descendant content for controls even when an ancestor composable declares the `SelectionContainer`. A
single `DisableSelection` around one button does not protect sibling `IconButton`s, links, menus, or other action
controls.

The right-click **text context menu** for selectable text (copy/select-all) is covered under context menus in
`interaction-and-affordances.md`.

## Deriving discrete state from a scroll position

A continuous scroll position does not map cleanly onto discrete selection or active state, so deriving one from the
other fails at the boundaries. Whenever code computes the active item from `LazyListState` (first-visible index or
offset) instead of owning it as state, check the end of the list and empty or heterogeneous content.

The canonical case is a "scroll-spy" nav that highlights the active section from the first visible item:

```kotlin
val current = derivedStateOf { items[lazyListState.firstVisibleItemIndex] }
```

This derivation cannot reach trailing items. At the bottom of the list, the last item sits at the bottom of the
viewport while an earlier item still occupies the top, so `firstVisibleItemIndex` never reaches the last index.

Any trailing item whose predecessors do not fill a viewport suffers the same bug, as do scroll-derived progress labels,
sticky headers, and paging triggers. When a position-derived active state is required, a working model:

- keys off a **threshold crossing** (e.g., the item whose start has passed a point near the top), not raw first-visible;
- **special-cases the end of scroll** ("scrolled to the bottom → last item active"), since no threshold rule covers it;
- defines behavior for the end state explicitly, where several short items may be visible at once.

First check whether the derivation is needed at all. Often the cleanest design **owns the state directly** instead of
deriving it. In a jump-list, clicking a nav entry scrolls to the target while `SingleSelectionLazyColumn` or
`MultiSelectionLazyColumn` owns the selection with no scroll-to-selection coupling. Prefer deleting scroll-position
machinery when the feature does not need two-way sync.

Separately, check **index-mapping safety** whenever code indexes one list by another's position (`items[i]`,
`items.indexOf(x)`), which assumes the lists are 1:1 and non-empty. Headers, spacers, empty states, or loading rows
desync the mapping or throw `IndexOutOfBoundsException`. Use explicit anchors (each section carrying its content index,
or a `sectionId -> itemIndex` map) and guard the empty case.

## Reporting

- For list findings, recommend `SingleSelectionLazyColumn`/`MultiSelectionLazyColumn` + `SimpleListItem` (not the
  deprecated `SelectableLazyColumn`) and name the dropped capabilities (keyboard nav, focus).
  - `SimpleListItem` is the right row component; the defect is placing it in a hand-rolled selected
    `Column`/`LazyColumn` instead of a selection container.
  - If `SimpleListItem` is already used for row content, record it under **Justified as-is** because it preserves
    standard row styling, selection visuals, and keyboard/focus semantics in the right container.
  - For non-trivial lists, recommend `SpeedSearchableLazyColumn`/`SpeedSearchArea` by default unless a good
    type-to-filter/search alternative exists; speed search is not built into the selection column.
- For over-broad selection findings, inspect descendants for every nested action control, even if an ancestor
  composable declares the `SelectionContainer`. Wrapping one button in `DisableSelection` does not protect sibling
  `IconButton`s, links, menus, or other controls.
- For text-selection findings, state the over-broad scope and the wrong-pointer/boundary-bleed consequence; recommend
  scoping selection to real text runs and forcing the arrow pointer (and `DisableSelection`) on nested non-text
  elements.
- For state derived from scroll position, explain the end-of-list failure (such as unreachable trailing items).
  Recommend either a threshold-based model with an end-of-scroll special case, or owning the state directly (such as a
  jump-list) instead of deriving it.
