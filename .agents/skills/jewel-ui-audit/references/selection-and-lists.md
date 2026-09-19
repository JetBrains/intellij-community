# Selection and Lists

Use when reviewing selectable lists/trees, text-selection scope and pointer behavior, and any "active item" state
derived from a list's scroll position. For hover/focus/keyboard affordances, tooltips, and context menus, see
`interaction-and-affordances.md`.

## Quick triggers

- **Manual selected rows in `Column`/`LazyColumn`:** prefer `SingleSelectionLazyColumn`/`MultiSelectionLazyColumn` with
  `SimpleListItem`; hand-rolled selection usually drops keyboard navigation and focus handling. `SimpleListItem` itself
  is not a smell — it is usually the right row component.
- **Searchable/non-trivial list with no speed search:** recommend `SpeedSearchableLazyColumn`/`SpeedSearchArea` by
  default. Omit only when there is a good alternative, such as a visible type-to-filter/search field.
- **Root/ancestor `SelectionContainer` over cards/list rows:** flag I-beam cursor over non-text regions and selection
  bleed across row/card boundaries. Scope selection to text runs and inspect descendants for action controls that need
  `DisableSelection`, even when the selection container is declared in an ancestor composable.
- **Continuous prose exception:** broad selection can be correct for one long document/Markdown/prose continuum where
  cross-paragraph copying is expected.
- **Scroll position drives active item:** check end-of-list and empty/heterogeneous cases; first-visible-item logic
  often cannot ever select trailing items.

## Selectable lists: use the built-in

A selectable list should use a built-in selection container whose rows are usually `SimpleListItem`s. Prefer
`SingleSelectionLazyColumn`/`MultiSelectionLazyColumn` (both in `org.jetbrains.jewel.foundation.lazy`); the older
`SelectableLazyColumn` still exists but is **deprecated** in favor of those two. `SimpleListItem` is not itself a smell:
it is the right choice for most list rows because it preserves the standard row styling, density, selection visuals, and
keyboard/focus semantics when used in the correct container. The built-in owns:

- selection state (single/multi, correct semantics),
- keyboard navigation (arrow keys, home/end),
- focus handling.

The antipattern is a plain `Column` or `LazyColumn` with each row a `SimpleListItem` (or custom row) wired with
`selected = (item == something)` and a manual `.clickable { }`. `SimpleListItem` is the right *row*, but in the wrong
*container*: this reimplements selection by hand and silently drops the built-in keyboard navigation and focus handling.

Speed search (type-to-filter) is **not** part of the selection column itself — it is a separate Jewel facility:
`SpeedSearchableLazyColumn`/wrapping in a `SpeedSearchArea` (with `SpeedSearchableTree`/`SpeedSearchableComboBox`
siblings; all currently `@ExperimentalJewelApi`). Speed search is an expected IDE pattern for non-trivial lists and
should be the default recommendation. When reviewing a manual selected list, evaluate speed search as a separate loss
from keyboard/focus selection semantics. Skip it only when the surface already has a good type-to-filter/search
mechanism, the list is tiny/static enough that filtering is genuinely unnecessary, or product requirements explicitly
choose a different search affordance. Do not claim a plain selection column provides speed search for free.

## Text selection scope and pointer

Scoping text selection too broadly — wrapping a large composite region in a `SelectionContainer`, or `selectable = true`
over a whole layout rather than the specific text runs — has two costs that are easy to miss:

- **I-beam everywhere.** A selection-enabled region shows the text I-beam cursor across its whole area, including over
  any non-text parts it contains (images, icons, padding, structural chrome). For a read-only informational surface this
  is the wrong pointer affordance; it signals an editor/text field where there is none.
- **Selection crosses visual boundaries.** Whole-region selection lets the user rubber-band across elements meant to
  read as separate (discrete cards, list rows, structural gaps), producing highlights that span those boundaries.

Decide selectability deliberately and scope it tightly: enable selection only on the runs of text where copying is a
real use case, not on the entire layout. Do **not** wrap an entire `LazyColumn`, card feed, or list of discrete rows in
a single `SelectionContainer`: it creates the wrong pointer over padding/icons and lets selection bleed across row/card
boundaries. The exception is a genuine continuum of selectable prose — for example, paragraphs in one long document or
Markdown/text article — where cross-paragraph selection is an expected copy behavior. If a surface is genuinely
read-only, leave it non-selectable so the arrow pointer is shown. If broad copy support is wanted, still verify the
cursor over non-text regions and that selection does not visually bleed across boundaries. A card/grid feed is the
common offender, but this applies to any composite read-only surface.

As a rule of thumb, when using the default text style/font and its derivatives, the mouse pointer should be the default
arrow, not an I-Beam, even if the text is selectable. This is because making UI text selectable is a convenience but not
its main reason to be. Editor-like surfaces should show an I-Beam instead.

Another important rule: nesting means you must be careful with pointer icons. Say you have a selectable run of content,
such as Markdown, and you want it to be selectable, but you also have a button in there (e.g., because of some custom
rendering). Then you want the button pointer icon to be an arrow even if it is inside a `SelectionContainer`, and its
text should likely not be selectable (i.e., the button should be inside `DisableSelection` and force the pointer). In
reviews, scan descendant content for controls even when the `SelectionContainer` is declared in an ancestor composable:
one `DisableSelection` around one button does not protect sibling `IconButton`s, links, menus, or other action controls.

The right-click **text context menu** for selectable text (copy/select-all) is covered under context menus in
`interaction-and-affordances.md`.

## Deriving discrete state from a scroll position

General principle: **a continuous scroll position does not map cleanly onto discrete selection/active state, so deriving
one from the other is error-prone at the boundaries.** Whenever "which item is active" is computed from `LazyListState`
(first-visible index, offset, etc.) rather than owned as real state, scrutinize the edges — especially the end of the
list and empty/heterogeneous content.

The canonical instance is a "scroll-spy" nav that highlights the section matching the scroll position via the first
visible item:

```kotlin
val current = derivedStateOf { items[lazyListState.firstVisibleItemIndex] }
```

This is structurally unable to reach trailing items: once scrolled to the end, the last item sits at the *bottom* of the
viewport while an earlier item still owns the top, so `firstVisibleItemIndex` never reaches the last index and the last
entry can never become active. The last item is the most visible victim, but any trailing item whose predecessors don't
fill a viewport is affected. The same class of edge bug appears in other "state from scroll" derivations (a progress
label, a sticky-header choice, a paging trigger) — the fix pattern is the same.

When a position-derived active state is genuinely wanted, a correct model:

- keys off a **threshold crossing** (e.g., the item whose start has passed a point near the top), not raw first-visible;
- **special-cases the end of scroll** ("scrolled to the bottom → last item active"), since no threshold rule covers it;
- defines behavior for the end state explicitly, where several short items may be visible at once.

But first ask whether the derivation is needed at all. Often the simplest correct design is to **own the state instead
of deriving it** — e.g., a jump-list where clicking a nav entry scrolls to the target and selection is owned by the
selection column (`SingleSelectionLazyColumn`/`MultiSelectionLazyColumn`), with no scroll→selection coupling. Prefer
deleting the scroll-position machinery over fixing it when the feature doesn't actually need two-way sync.

Orthogonally, check **index-mapping safety** for any code that indexes one list by another's position (`items[i]`,
`items.indexOf(x)`): it assumes the two lists are 1:1 and non-empty. Headers, spacers, empty states, or loading rows
break that assumption — the mapping desyncs or throws `IndexOutOfBoundsException`. Recommend explicit anchors (each
section carrying its own content index, or a `sectionId -> itemIndex` map) over positional indexing, and guard the empty
case.

## Reporting

- For list findings, recommend `SingleSelectionLazyColumn`/`MultiSelectionLazyColumn` + `SimpleListItem` (not the
  deprecated `SelectableLazyColumn`) and call out the dropped capabilities (keyboard nav, focus). Be precise:
  `SimpleListItem` is normally the right row; the smell is using it in a hand-rolled selected `Column`/`LazyColumn`
  instead of a selection container. If `SimpleListItem` is already used for row content, put that in **Justified
  as-is**: it preserves standard row styling, selection visuals, and keyboard/focus semantics when used in the right
  container.
  For non-trivial lists, recommend `SpeedSearchableLazyColumn`/`SpeedSearchArea` by default unless there is a good
  existing type-to-filter/search alternative — speed search is not built into the selection column.
- For over-broad selection findings, inspect descendants for every nested action control, even if the
  `SelectionContainer` is in an ancestor composable. Existing `DisableSelection` on one button is not enough; sibling
  `IconButton`s, links,
  menus, and other controls need their own selection-disabling/arrow-pointer handling.
- For text-selection findings, state the over-broad scope and the wrong-pointer/boundary-bleed consequence; recommend
  scoping selection to real text runs and forcing the arrow pointer (and `DisableSelection`) on nested non-text
  elements.
- For state derived from scroll position, explain the end-of-list/edge failure concretely (e.g., the trailing-item
  impossibility), and recommend either a threshold-based model with an end-of-scroll special case, or owning the state
  directly (e.g., a jump-list) instead of deriving it.
