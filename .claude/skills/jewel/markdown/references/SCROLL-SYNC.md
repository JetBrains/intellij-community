# Scroll synchronization (editor preview)

How to keep a Markdown preview scrolled in sync with a source editor. Read this when building an editor + preview, or when preview scrolling
behaves oddly.

## When it applies

Scroll sync is an editor-preview feature. It only works when:

- the `MarkdownProcessor` is in `MarkdownMode.EditorPreview(scrollingSynchronizer = ...)`, and
- the block renderer is scroll-sync-aware (`ScrollSyncMarkdownBlockRenderer`, which the editor-preview styling wires up), and
- the source view reports the current line so the preview can scroll to it.

In `MarkdownMode.Standalone` there is no synchronizer and nothing to sync.

## Core type: `ScrollingSynchronizer`

File: `core/.../scrolling/ScrollingSynchronizer.kt`.

- Create one with `ScrollingSynchronizer.create(scrollState)`:
  - `ScrollState` → returns a working `PerLine` synchronizer.
  - `LazyListState` → currently **not supported**; it logs a warning and returns `null`. This is the key gotcha (see below).
- Pass it into `MarkdownMode.EditorPreview(scrollingSynchronizer)`, which you give to the `MarkdownProcessor`.
- Drive it from the editor: `suspend fun scrollToLine(sourceLine: Int, animationSpec)` scrolls the preview to the best match for that source
  line.
- `process(action)` wraps a reparse with `beforeProcessing()` / `afterProcessing()`; `MarkdownProcessor.processMarkdownDocument` already
  calls this when a synchronizer is present.

## How the mapping is built

The synchronizer maintains source-line ↔ rendered-position mappings, fed by three renderer callbacks (handled for you by
`ScrollSyncMarkdownBlockRenderer`):

- `acceptBlockSpans(block, sourceRange)` — maps each block to the source lines it spans (depth-first; innermost block wins per line). Wraps
  blocks as `LocatableMarkdownBlock` to disambiguate equal blocks.
- `acceptGlobalPosition(block, coordinates)` — maps a block to its global Y position; triggered on first composition, the changed block, and
  blocks below it.
- `acceptTextLayout(block, textLayout)` — maps per-line offsets inside code blocks, so scrolling can target a specific line within a
  fenced/indented code block (1:1 source-to-preview line mapping).

`scrollToLine` finds the block on the line (or the closest following/preceding block) and scrolls to its top, adding the intra-code-block
offset when applicable.

## Identity gotcha: `LocatableMarkdownBlock`

Many `MarkdownBlock`s are value-equal by content (two identical paragraphs are `equals`). Using raw blocks as map keys would let one block's
layout overwrite another's, causing erratic scrolling. The synchronizer decorates blocks with their source line range via
`LocatableMarkdownBlock` so equal blocks in different places stay distinct. If you implement a custom synchronizer, preserve this identity
discipline.

## Gotcha: custom renderers must preserve scroll-sync wiring

Scroll sync is not in `DefaultMarkdownBlockRenderer`. It is implemented entirely by its subclass `ScrollSyncMarkdownBlockRenderer` (file:
`core/.../scrolling/ScrollSyncMarkdownBlockRenderer.kt`), which overrides the standard `Render*` methods to wrap blocks in
`AutoScrollableBlock` (reporting global position) and to call `acceptTextLayout` for code blocks. The IntelliJ Markdown plugin instantiates
this subclass directly for its editor preview; the core/standalone/bridge `MarkdownBlockRenderer` factories do **not** return it.

Two consequences:

- Custom block renderer: if you provide your own `MarkdownBlockRenderer` for an editor preview by subclassing `DefaultMarkdownBlockRenderer`
  (or building one from scratch), you bypass `ScrollSyncMarkdownBlockRenderer` and lose scroll sync for every block. Subclass
  `ScrollSyncMarkdownBlockRenderer` instead and call `super` from your overrides, or replicate its `AutoScrollableBlock` wiring.
- Custom block from an extension: even when `ScrollSyncMarkdownBlockRenderer` is in use, it only wraps the standard blocks it overrides
  (paragraph, heading, fenced/indented code). It does **not** wrap `CustomBlock`s rendered by a `MarkdownBlockRendererExtension`, so a custom
  block does not report its position and the preview cannot scroll to lines inside it (the synchronizer falls back to the nearest standard
  block).

If scroll sync matters for your custom block, opt in from the extension's `RenderCustomBlock` (this works regardless of which of the two
cases above applies, as long as a `ScrollingSynchronizer` is present):

- Wrap your block content in `AutoScrollableBlock(block, synchronizer) { ... }` (public experimental API, file:
  `core/.../scrolling/AutoScrollingUtil.kt`). It reports global position via `onGloballyPositioned` for you.
- Get the synchronizer from `(JewelTheme.markdownMode as? MarkdownMode.EditorPreview)?.scrollingSynchronizer`; if it's `null` (standalone
  mode), render normally without the wrapper. Mirror the default renderer's pattern.
- For a multi-line custom block where scrolling to an inner line matters (like code blocks), also call
  `synchronizer.acceptTextLayout(block, textLayoutResult)` from the relevant `Text`'s `onTextLayout`. Without it the block still scrolls as
  a unit, just not to a specific inner line.
- The block you pass should be the one handed to `RenderCustomBlock`; the default renderer uses the `LocatableMarkdownBlock` wrapper
  internally, but `AutoScrollableBlock` unwraps it, so passing your block is fine.

This is opt-in by design: custom blocks that don't wrap simply won't be scroll-sync targets, which is acceptable for many extensions. Only
add the wrapper when the editor-preview UX needs to scroll into your block.

## Wiring sketch

```kotlin
// Source editor exposes a verticalScrollState: ScrollState and a current caret/first-visible line.
val synchronizer = remember(scrollState) { ScrollingSynchronizer.create(scrollState) }

val processor =
  remember(synchronizer) {
    MarkdownProcessor(
      markdownMode = MarkdownMode.EditorPreview(scrollingSynchronizer = synchronizer),
    )
  }

// When the editor scrolls / caret moves:
LaunchedEffect(currentSourceLine, synchronizer) {
  synchronizer?.scrollToLine(currentSourceLine)
}
```

Use the editor-preview-aware styling/renderer (which installs `ScrollSyncMarkdownBlockRenderer`) so the accept* callbacks are emitted.
Render with `LazyMarkdown` for documents.

## Gotchas

- `LazyListState` is not supported by `create(...)` yet — it returns `null`. For working scroll sync today, back the preview with a
  `ScrollState`-based scroll container (the `PerLine` synchronizer), even though large docs otherwise favor `LazyMarkdown`. Confirm against
  current sources before promising lazy-list scroll sync.
- A `null` synchronizer silently means "no sync"; check the `create(...)` return value.
- Don't share an `EditorPreview` processor (and its synchronizer) across unrelated documents — editor mode is stateful.
- Scroll sync depends on the scroll-sync renderer being active; a plain `DefaultMarkdownBlockRenderer` won't emit the position callbacks.
- Editing reshuffles mappings: `acceptGlobalPosition` fires for the changed block and those below it, `acceptTextLayout` not always for
  blocks below the change. Don't assume every block re-reports on every edit.
