# Spacing and Layout

Use when reviewing padding, margins, and inter-element gaps in nested Compose layouts.

## Quick triggers

- When nested `spacedBy` combines with child padding, compute the effective gap by hand and flag additive stacking
  where multiple layers own the same seam.
- If a split pane omits minimums, require per-pane minimums on `HorizontalSplitLayout` and `VerticalSplitLayout` panes,
  and require hoisted states with reset support on nested splitters.
- **Resizable detail/tool-window content:** require a minimum floor derived from content collision boundaries (title +
  actions + gaps, intrinsic control sizes), not a round guess. Below the floor, clip or scroll intentionally instead of
  reflowing into overlap/off-screen controls.
- **Overflowing content:** long/unbounded lists, prose, details, and tables need scrolling through Jewel styled
  scrollable containers/scrollbars, not raw scroll with no themed scrollbar.
- **Mixed text style baseline alignment:** when combining multiple text styles (e.g., default UI font + editor font)
  on the same line, `Alignment.CenterVertically` is insufficient. Use `AnnotatedString` or baseline alignment
  modifiers, and always visually verify baseline alignment.
- **Long prose full width:** cap readable line length for Markdown/docs/chat/release notes instead of letting prose fill
  a wide tool window. Keep the capped prose column start-aligned in LTR layouts unless a product spec says otherwise.

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

**Rule:** a gap between siblings must come from one source (the parent's `Arrangement.spacedBy`), not parent
arrangement plus each child's outer padding plus a grandparent arrangement. When a child already lives in a `spacedBy`
parent, adding outer padding on the child double-counts. Missing spacings deserve the same scrutiny as excessive ones.

An exception applies when the design intentionally varies spacing (such as before a section header in a list). Check
what the spec requires with the user. If the model supports vision input, ask for a design spec screenshot or read
access to the spec (for example, via Figma MCP).

## Check per-axis symmetry

Compare effective horizontal vs. vertical spacing at each seam: inner padding, inter-item, and inter-row. Uniform
`padding(8.dp)` sets inner spacing on both axes, while content blocks are also separated vertically by a content-rhythm
value (such as `blockVerticalSpacing = 12.dp`) that does not collapse into padding.

That combination produces ~8.dp horizontal inner margin against ~20.dp vertical spacing, cramping left/right text inside
oversized vertical space. Symmetric-looking code (`padding(8.dp)`) produces asymmetric output when a second mechanism
adds to one axis only.

## Do not reuse content-rhythm tokens as layout margins

A Markdown or prose `blockVerticalSpacing` is a *content* rhythm value (gap between paragraphs). Borrowing it as a
layout margin (such as `padding(vertical = blockVerticalSpacing)` on a grid row) couples grid gutters to prose density:
changing how prose is spaced causes the grid to reflow. Keep layout spacing and content spacing as separate tokens.

## Resizable layouts and splitters

Split panes are the canonical resizable IDE surface. Jewel ships `HorizontalSplitLayout` and `VerticalSplitLayout`
(`org.jetbrains.jewel.ui.component`) driven by a `SplitLayoutState` (`dividerPosition` fraction 0f..1f, created with
`rememberSplitLayoutState(initialSplitFraction)`).

Use these instead of a raw `Box` + pointer-drag or a hand-rolled draggable `Divider`. They provide the wider draggable
hit area (`draggableWidth`), the resize pointer cursor, and per-pane minimums out of the box.

- **Two-way:** one `HorizontalSplitLayout` (or vertical). Always set `firstPaneMinWidth` and `secondPaneMinWidth` —
  leaving them `Dp.Unspecified` lets a pane be dragged to zero, which collapses content. These minimums are the
  split-level guardrail; they are separate from the content's own minimum (below).
- **Three-way/nested:** compose two 2-way splitters by placing a second `SplitLayout` inside the `first`/`second` slot
  of the outer one, each with its own `SplitLayoutState`. Hoist all states rather than nesting `remember` calls so a
  "reset layout" action can restore every divider at once.
  - See the Jewel showcase `SplitLayouts` sample: an outer horizontal split whose second pane is a vertical split whose
    second pane is another horizontal split, each pane carrying its own min width.

**Flag:**
- A `SplitLayout` (or any nested splitter) with no per-pane minimums.
- Independent `remember { SplitLayoutState(...) }` calls buried inside panes with no way to reset them together.
- A hand-built draggable divider that reimplements `HorizontalSplitLayout` (usually missing the cursor, wider drag
  area, or minimums).

## Every resizable UI needs a minimum size

Almost every surface in an IDE can be resized by the user or a parent split/tool window. Each resizable surface needs a
defined minimum size below which the layout stops shrinking and **clips** instead of breaking down into overlapping
text, negative space arithmetic, off-screen controls, or broken `Arrangement`.

Compute this minimum from the content (such as a single-line control's intrinsic height, or the narrowest width a label
and button row can take before colliding) rather than guessing.

- The per-pane splitter minimums (above) handle the split seam. The **content inside** a pane, a tool window, or any
  resizable container needs its own floor too, or it will still break when the whole surface is small.
- The desired behavior under the floor is **clip, not reflow-into-garbage**: the container reports its minimum, takes
  the parent's smaller size, and lets overflow clip. Don't let a layout compute negative or zero space.
- A reusable `Modifier` that clamps the measured size into a `[min, max]` box and clips beyond it enforces this floor at
  one call site rather than sprinkling `sizeIn`/`heightIn` everywhere.

**Flag:**
- Flag any resizable panel, dialog, tool-window content, or split pane that defines no minimum size.
- Flag layouts that visibly break (with overlapping text, clipped controls, or mis-arranged rows) at small sizes
  instead of clipping cleanly.
- Minimums that are round guesses (`200.dp`) rather than derived from what the content needs to stay coherent. Use
  screenshots or semantics inspections (for example, spectre.sebastiano.dev when configured) to validate layout
  analysis.

## Overflowing content must scroll — Jewel's scrollable containers

Content that can exceed its available space must be **scrollable**, not clipped and unreachable or forced to grow the
window. Common cases include lists of unknown length, long prose, tall details panes, and wide tables.

Route scrolling through Jewel's **styled scrollable containers**, not a raw `Modifier.verticalScroll` with no scrollbar
or a hand-rolled scrollbar adapter. Styled containers match the IDE's native look (macOS over or beside content,
Windows/Linux overlay), track the theme, and honor platform show/hide rules. Custom scrollbar implementations miss these
platform details and should be avoided.

The idiomatic components (`org.jetbrains.jewel.ui.component`) are `VerticallyScrollableContainer` and
`HorizontallyScrollableContainer` (plus lower-level `VerticalScrollbar`/`HorizontalScrollbar` when positioning the bar
manually). Pick the overload that matches the content type:

- **Non-lazy content** (a `Column` of a bounded number of items, or a prose block): use the overload that takes a
  `ScrollState`. Because the container **owns the scroll modifier**, the `content` must **not** apply its own
  `verticalScroll`, or it will double-scroll.
- **Lazy content** (a `LazyColumn`/`LazyListState`, or a selection list): use the overload that takes the
  `ScrollableState`/`LazyListState` **shared with the lazy layout**. The lazy list owns the scroll while the container
  draws the matching scrollbar. Selection-aware Jewel lists like `SingleSelectionLazyColumn` can already integrate the
  styled scrollbar (see `selection-and-lists.md`).

**Do not DIY scrolling or scrollbars.** Flag any bespoke scroll or scrollbar machinery by default, including hand-drawn
scrollbars, custom scroll adapters, manual thumb sizing/positioning, custom drag/wheel handling, or custom scroll-to
logic. Reimplementing native scrollbar visuals, platform show/hide, thumb behavior, and theme tracking by hand drifts
from the IDE.

Use custom scrolling only for a **missing Jewel capability** that no `VerticallyScrollableContainer`,
`HorizontallyScrollableContainer`, or `VerticalScrollbar` overload can express. When that happens, **request the
feature from Jewel** and comment the tracking issue so the workaround stays temporary. Flag custom scroll code that
lacks this justification or only restyles the scrollbar.

**Flag:**
- Flag content that can exceed its space (an unbounded list, long text, or a resizable/tool-window pane) with **no
  scroll affordance**, which clips overflow or pushes the layout past the viewport.
- Flag any raw `Modifier.verticalScroll`/`horizontalScroll` or bare `LazyColumn` that omits a **Jewel scrollbar**.
- A **hand-rolled scrollbar** or custom scroll adapter reimplementing `VerticallyScrollableContainer`.
- The double-scroll bug (`ScrollState`-owning container whose content *also* applies a scroll modifier). Do **not**
  flag a bounded layout that always fits (such as a short fixed toolbar or 3-field row).

## Baseline alignment for mixed text styles

When multiple text styles appear on the same horizontal line (such as pairing `JewelTheme.defaultTextStyle` with
monospace `JewelTheme.editorTextStyle`, or mixing font sizes), **vertical centering (`Alignment.CenterVertically`) is
not enough**.

Different font families and sizes have distinct font metrics, line heights, cap heights, and descenders, so
`Alignment.CenterVertically` centers the `Text` bounding boxes rather than the glyph baselines. Adjacent labels then
visibly jump or misalign along their text baseline.

Bounding-box centering is acceptable for visually distinct components with their own padding and background, such as
badges. When adjacent labels share a row as running text (such as UI text beside editor-style text, or primary text
beside medium-body info text), validate baseline alignment.

**Best Practices & Solutions**:

1. **Prefer `AnnotatedString`**: When possible, use a single `Text` composable rendering an `AnnotatedString` with
   multiple `SpanStyle`s. A single `Text` composable aligns font baselines across spans automatically.
2. **Explicit Baseline Alignment**: If separate `Text` composables are necessary, align them using
   `Modifier.alignByBaseline()` within a `Row`.
3. **Mandatory Visual Verification**: Aligning multiple labels with different text styles is subtle. Authors and
   reviewers must **always visually verify baseline alignment** (via UI preview or live rendering) when implementing or
   fixing mixed-style text lines.

Flag mixed font styles or font sizes placed side-by-side in a `Row` with `Alignment.CenterVertically` when they omit
baseline alignment or `AnnotatedString`. Also flag text alignment fixes applied without visual verification of the
font baselines.

## Cap the width of free-text/prose runs

Long-form running text (Markdown content, multi-sentence descriptions, release notes, chat/LLM output, doc panels) needs
a **maximum** width, not only `fillMaxWidth()`. Readable line length sits around 50–75 characters per line (≈66
optimal), and WCAG 1.4.8 caps readable text at 80 characters (40 for CJK).

Beyond 80 characters, the reader's eye loses its place moving from the end of one line to the start of the next. In a
wide tool window, maximized editor tab, or stretched dialog, an unconstrained prose column stretches to hundreds of
characters per line.

IDE density and experienced readers tolerate the upper end of the range, but prose still requires a `widthIn(max = ...)`
or max-width wrapper. Let the *container* fill the width while the *text column* stays capped and start-aligned (not
centered) in left-to-right IDE panels.

Size the cap with an `em` or character-based measure rather than a raw `dp` guess so the width scales with the font.
Apply this cap only to running prose; do not cap labels, table cells, code, or single-line controls.

**Flag:**
- Markdown, prose, or description text that uses `fillMaxWidth()` across a resizable panel with no `widthIn(max = ...)`
  cap.
- A prose column that runs to 100+ characters per line when the window is wide.
- Centering a long text block instead of capping its measure and start-aligning it.

## Shared spacing scale

When values like 8/12/32 are picked independently per file, the ratios between inner padding and gutters are accidental
(for example, gutters ending up ~4x the inner padding). Pull spacing from a small named scale so per-axis rhythm and
inner-vs.-gutter ratios are deliberate and reviewable.

## Reporting

- Show the arithmetic: list each spacing source between the two elements and the sum, so the additive stack is
  undeniable.
- Give horizontal-vs.-vertical effective numbers side by side for asymmetry findings.
- Recommend consolidating to a single spacing owner (usually parent `Arrangement.spacedBy`) and a shared scale, rather
  than hand-tuning one of the layers.
