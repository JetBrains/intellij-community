# Spacing and Layout

Use when reviewing padding, margins, and inter-element gaps in nested Compose layouts.

## Quick triggers

- **Nested `spacedBy` + child padding:** compute the effective gap by hand; flag additive stacking when multiple layers
  own the same seam.
- **Split pane without minimums:** `HorizontalSplitLayout`/`VerticalSplitLayout` panes need per-pane minimums; nested
  splitters need hoisted states and reset capability.
- **Resizable detail/tool-window content:** require a minimum floor derived from content collision boundaries (title +
  actions + gaps, intrinsic control sizes), not a round guess. Below the floor, clip or scroll intentionally instead of
  reflowing into overlap/off-screen controls.
- **Overflowing content:** long/unbounded lists, prose, details, and tables need scrolling through Jewel styled
  scrollable containers/scrollbars, not raw scroll with no themed scrollbar.
- **Long prose full width:** cap readable line length for Markdown/docs/chat/release notes instead of letting prose fill
  a wide tool window, and keep the capped prose column start-aligned in LTR layouts unless a product spec says
  otherwise.

## Compute the effective gap by hand

Spacing bugs in nested Compose layouts are usually invisible at any single call site. They emerge from **multiple
layers, each adding spacing without knowing about the others**, so values stack additively. To find them, trace every
spacing source between two elements and sum them.

### The additive-stacking pattern

A vertical gap between two stacked rows can come from three independent sources at once:

```text
row A bottom padding (padding(vertical = X))        = X
row B top padding    (padding(vertical = X))        = X
parent Column inter-child arrangement (spacedBy(X)) = X
--------------------------------------------------------
effective gap between A and B                       = 3X
```

Meanwhile, the horizontal gap between two side-by-side items may come from a single source (`Arrangement.spacedBy(Y)`) =
Y. Now the vertical and horizontal gutters (3X vs. Y) are produced by different mechanisms and have no reason to match —
and usually don't.

**Rule:** a gap between siblings should come from one source — the parent's `Arrangement.spacedBy` — not parent
arrangement plus each child's outer padding plus a grandparent arrangement. When a child already lives in a `spacedBy`
parent, adding outer padding on the child double-counts. Missing spacings are as worthy of scrutiny as excessive ones.

The exception is when there is the design intent to have different spacing (e.g., before a section header in a list);
it's worth checking with the user what the spec says, and if vision input is supported by the model, asking them for a
screenshot of the design spec, or for read access to the actual spec (e.g., Figma MCP).

## Check per-axis symmetry

Compare effective horizontal vs. vertical spacing at each seam: inner padding, inter-item, inter-row. A common defect:
uniform `padding(8.dp)` sets inner spacing on both axes, but content blocks are *additionally* separated vertically by a
content-rhythm value (e.g., `blockVerticalSpacing = 12.dp`) that does not collapse into the padding. Result: ~8.dp
horizontal inner margin but ~20.dp vertical — cramped left/right text inside oversized vertical space. Symmetric-looking
code (`padding(8.dp)`) produces asymmetric output because a second mechanism adds to one axis only.

## Do not reuse content-rhythm tokens as layout margins

A Markdown/prose `blockVerticalSpacing` is a *content* rhythm value (gap between paragraphs). Borrowing it as a layout
margin — e.g., `padding(vertical = blockVerticalSpacing)` on a grid row — couples grid gutters to prose density: change
how prose is spaced and the grid silently reflows. Keep layout spacing and content spacing as separate tokens.

## Resizable layouts and splitters

Split panes are the canonical resizable IDE surface. Jewel ships `HorizontalSplitLayout` and `VerticalSplitLayout` (
`org.jetbrains.jewel.ui.component`) driven by a `SplitLayoutState` (`dividerPosition` fraction 0f..1f; create with
`rememberSplitLayoutState(initialSplitFraction)`). Use these instead of hand-rolling a draggable `Divider` — they
provide the draggable hit area (`draggableWidth`, wider than the visible divider), the resize pointer cursor, and
per-pane minimums out of the box. Prefer them over a raw `Box` + pointer-drag.

- **Two-way:** one `HorizontalSplitLayout` (or vertical). Always set `firstPaneMinWidth` and `secondPaneMinWidth` —
  leaving them `Dp.Unspecified` lets a pane be dragged to zero, which collapses content. These minimums are the
  split-level guardrail; they are separate from the content's own minimum (below).
- **Three-way/nested:** compose two 2-way splitters — put a second `SplitLayout` inside the `first`/`second` slot of
  the outer one, each with its own `SplitLayoutState`. Hoist all the states (don't nest `remember`s implicitly) so a
  "reset layout" action can restore every divider at once. The Jewel showcase `SplitLayouts` sample is the reference: an
  outer horizontal split whose second pane is a vertical split whose second pane is another horizontal split, each pane
  carrying its own min width.

**Flag:** a `SplitLayout` (or any nested splitter) with no per-pane minimums; independent
`remember { SplitLayoutState(...) }` calls buried inside panes with no way to reset them together; or a hand-built
draggable divider that reimplements what `HorizontalSplitLayout` already gives you (usually missing the cursor, the
wider drag area, or the minimums).

## Every resizable UI needs a minimum size

Anything the user (or a parent split/tool-window) can resize — which is *almost everything* in an IDE — needs a defined
minimum size below which the layout stops shrinking and **clips** instead of breaking down (overlapping text, negative
space arithmetic, controls pushed off-screen, `Arrangement` going haywire). The minimum is not decorative: it is the
smallest size at which the layout still holds together, computed from the content (e.g., a single-line control's
intrinsic height, the narrowest a label and button row can be before they collide), not guessed.

- The per-pane splitter minimums (above) handle the split seam. The **content inside** a pane, a tool window, or any
  resizable container needs its own floor too, or it will still break when the whole surface is small.
- The desired behavior under the floor is **clip, not reflow-into-garbage**: the container reports its minimum, takes
  the parent's smaller size, and lets overflow clip. Don't let a layout compute negative or zero space.
- A reusable `Modifier` that clamps the measured size into a `[min, max]` box and clips beyond it is the clean way to
  enforce this at one call site rather than sprinkling `sizeIn`/`heightIn` everywhere.

**Flag:** a resizable panel, dialog, tool-window content, or split pane with no minimum size; layouts that visibly
break (overlap, clipped controls, mis-arranged rows) at small sizes instead of clipping cleanly; minimums that are round
guesses (`200.dp`) rather than derived from what the content actually needs to stay coherent. You can use screenshots or
semantics inspections — e.g., using spectre.sebastiano.dev if already set up — to validate your layout analysis.

## Overflowing content must scroll — Jewel's scrollable containers

The flip side of the minimum-size rule: content that can exceed the available space (a list of unknown length, long
prose, a details pane taller than its tool window, a wide table) must be **scrollable**, not clipped-and-unreachable or
forced to grow the whole window. And in Jewel that scrolling should go through the **styled scrollable containers**, not
a raw `Modifier.verticalScroll` with no scrollbar or a hand-rolled scrollbar adapter. This way the scrollbar matches the
IDE's native look (macOS over or beside the content, Windows/Linux overlay), tracks the theme, and honors platform
show/hide behaviors. There are many subtle behaviors that are easy to miss and hard to implement, so custom
implementations are generally strongly discouraged.

The idiomatic components (`org.jetbrains.jewel.ui.component`) are `VerticallyScrollableContainer`/
`HorizontallyScrollableContainer` (and the lower-level `VerticalScrollbar`/`HorizontalScrollbar` when you need to
place the bar yourself). Two usage modes, and picking the wrong one is a bug:

- **Non-lazy content** (a `Column` of a bounded number of items, a prose block): use the overload that takes a
  `ScrollState` — it **owns the scroll modifier**, so the `content` must **not** also apply its own `verticalScroll`.
  Applying both double-scrolls.
- **Lazy content** (a `LazyColumn`/`LazyListState`, or a selection list): use the overload that takes the
  `ScrollableState`/`LazyListState` **shared with the lazy layout in the content** — here the lazy list owns the scroll
  and the container just draws the matching scrollbar against the shared state. Selection-aware Jewel lists like
  `SingleSelectionLazyColumn` might already integrate the styled scrollbar; see `selection-and-lists.md`.

**Do not DIY scrolling or scrollbars.** As a general stance, treat any bespoke scroll/scrollbar machinery — a hand-drawn
scrollbar, a custom scroll-adapter, manual thumb sizing/positioning, custom drag/wheel handling, a home-grown "scroll
to" — as a finding by default, not just the specific smells above. Getting native scrollbar look, platform show/hide,
thumb behavior, and theme tracking right by hand is hard and drifts from the IDE. The bar is high: reach for custom
scrolling only for a genuinely **missing Jewel capability** that no `VerticallyScrollableContainer`/
`HorizontallyScrollableContainer`/`VerticalScrollbar` overload can express. When that happens, the right move is to
**request the feature from Jewel** and leave a code comment marking the gap (ideally with the tracking issue) if it is
generally useful — so the workaround is temporary and discoverable, not a permanent private reimplementation. A custom
scroll solution with no such justification (or one that merely restyles the scrollbar for looks) should be flagged and
replaced with the built-in.

**Flag:** content that can plausibly exceed its space (unbounded list, long text, a pane in a resizable/tool-window
surface) with **no scroll affordance** at all, so overflow is silently clipped or pushes the layout past the viewport; a
raw `Modifier.verticalScroll`/`horizontalScroll` (or a bare `LazyColumn`) with **no Jewel scrollbar**, so there is no
visible, themed scroll indicator; a **hand-rolled scrollbar** or custom scroll-adapter reimplementing what
`VerticallyScrollableContainer` provides; and the double-scroll bug (a `ScrollState`-owning container whose content
*also* applies a scroll modifier). Do **not** flag a genuinely bounded, always-fits layout (a short fixed toolbar, a
3-field row) for lacking a scroll container.

## Cap the width of free-text/prose runs

Long-form running text — Markdown content, multi-sentence descriptions, release notes, chat/LLM output, doc panels —
needs a **maximum** width, not just `fillMaxWidth()`. Line length drives readability: the well-established guideline
is ~50–75 characters per line (≈66 optimal), and WCAG 1.4.8 caps readable text at 80 characters (40 for CJK). Past that,
the eye loses its place tracking from the end of one line back to the start of the next, and comprehension drops. In a
resizable IDE surface (a wide tool window, a maximized editor tab, a stretched dialog), an unconstrained prose column
will run to hundreds of characters per line.

We can be looser than a marketing site — IDE density and experienced readers tolerate the upper end — but the rule still
applies: give prose a `widthIn(max = ...)` (or a max-width wrapper) sized to a sane measure, and let the *container*
fill the width while the *text column* stays capped (typically start-aligned, not centered, in a left-to-right IDE
panel). Sizing by an `em`/character-based measure is more robust than a raw `dp` guess, since the comfortable width
scales with the font. This applies only to running prose — labels, table cells, code, and single-line controls should
not be capped this way.

**Flag:** Markdown/prose/description text that `fillMaxWidth()`s into the full width of a resizable panel with no
`widthIn(max=...)` cap; a prose column that visibly runs to 100+ characters per line when the window is wide; centering
a long text block instead of capping its measure and start-aligning it.

## Shared spacing scale

When values like 8/12/32 are picked independently per file, the ratios between inner padding and gutters are accidental.
Gutters ending up ~4x the inner padding is a symptom, not a decision. Recommend pulling spacing from a small named scale
so per-axis rhythm and inner-vs.-gutter ratios are deliberate and reviewable.

## Reporting

- Show the arithmetic: list each spacing source between the two elements and the sum, so the additive stack is
  undeniable.
- Give horizontal-vs.-vertical effective numbers side by side for asymmetry findings.
- Recommend consolidating to a single spacing owner (usually parent `Arrangement.spacedBy`) and a shared scale, rather
  than hand-tuning one of the layers.
  </content>
