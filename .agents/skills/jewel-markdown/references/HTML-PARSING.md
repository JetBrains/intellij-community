# HTML parsing

How Jewel handles embedded HTML in Markdown. Read this when HTML is ignored/shown as raw text, when mapping custom tags to Markdown blocks,
or when HTML alignment attributes matter.

## Opt-in

Embedded HTML is off by default. A `MarkdownProcessor` ignores it unless constructed with `parseEmbeddedHtml = true`:

```kotlin
val processor = MarkdownProcessor(extensions = extensions, parseEmbeddedHtml = true)
```

When off, `htmlConverterExtension`s are not even collected, and HTML stays as raw `HtmlBlock` / inline HTML.

## Three outcomes for an HTML fragment

With `parseEmbeddedHtml = true`, an HTML fragment ends up as one of:

1. A native `MarkdownBlock`, via a built-in converter for a known tag.
2. A native `MarkdownBlock` produced by a `MarkdownHtmlConverterExtension` you supplied (custom tag).
3. Raw, uninterpreted HTML — `MarkdownBlock.HtmlBlock` (block) or `InlineMarkdown.HtmlInline` (inline) — rendered as text via the
   `htmlBlock` styling. Jewel does **not** do general inline-HTML rendering.

## Built-in tag converters

Tags converted to native Markdown when embedded HTML is on:

`p`, `li`, `ol`, `ul`, `h1`-`h6`, `code`, `pre`, `img`.

These produce normal blocks/inlines and render through the standard renderers. Parsing of the raw HTML into an intermediate tree uses
Jsoup in `MarkdownHtmlNode`; a single CommonMark HTML block can yield multiple Markdown blocks.

## Custom tag conversion: `MarkdownHtmlConverterExtension`

To map an extra tag (e.g. `<table>`) onto a Markdown block your code understands:

- Implement `MarkdownHtmlConverterExtension`:
  - `val supportedTags: Set<String>` — tags this extension handles.
  - `fun provideConverter(tagName): HtmlElementConverter` — converter per tag.
- Expose it from your `MarkdownProcessorExtension.htmlConverterExtension`.
- `HtmlElementConverter`:
  - `convert(htmlElement, convertChildren, convertInlines): MarkdownBlock?` — produce a block; use the `convertChildren` / `convertInlines`
    lambdas to recurse into children instead of reparsing.
  - `convertInlines(element, convertSubInlines): List<InlineMarkdown>?` — produce inline content instead of a block.
  - Return `null` when the element doesn't apply.
- The processor selects a converter via `provideExtensionHtmlElementConverterFor(tag)` — the first extension whose `supportedTags` contains
  the tag.

References (both ship a `MarkdownHtmlConverterExtension` from their processor extension):

- `gfm-tables`: `GitHubTableProcessorExtension` converts `<table>` into a table block (block-level converter).
- `gfm-strikethrough`: `GitHubStrikethroughProcessorExtension` converts `<s>`, `<strike>`, `<del>` into strikethrough inline nodes (inline converter, via `convertInlines`).

Use these as the models to copy. If you just need those tags, add the corresponding extension rather than reimplementing the converter.

Custom conversion is parse-side only. If your converter emits a custom block type, you still need a matching renderer extension to display
it.

## Attribute-carrying HTML and alignment

HTML that carries layout attributes (notably `align`) becomes a `MarkdownBlock.HtmlBlockWithAttributes(mdBlock, attributes)` (file:
`core/.../MarkdownBlock.kt`) wrapping the converted inner block.

- `DefaultMarkdownBlockRenderer.RenderHtmlBlockWithAttributes(...)` reads `attributes["align"]` (`left`/`center`/`right`), wraps the child
  in a width-filling `Box` with the matching alignment, and provides the corresponding `TextAlign` to the (internal) `LocalTextAlignment` so
  nested text inherits it.
- This is why custom renderers must forward text alignment: a renderer that overrides text-bearing blocks but ignores the current text
  alignment will silently break centered/right-aligned HTML. See the custom-renderer gotchas in `SKILL.md`.

## Wiring

```kotlin
val processor =
  MarkdownProcessor(
    extensions = listOf(GitHubTableProcessorExtension), // exposes an htmlConverterExtension for <table>
    parseEmbeddedHtml = true,                            // required, or the converter is ignored
  )

// Renderer side still needs the matching renderer extension for the produced block:
val blockRenderer =
  MarkdownBlockRenderer.light(
    styling = styling,
    rendererExtensions = listOf(GitHubTableRendererExtension(GfmTableStyling.light(), styling)),
  )
```

## Gotchas

- `htmlConverterExtension` added but nothing happens: `parseEmbeddedHtml` is still `false`.
- Expecting arbitrary HTML to render as UI: only the built-in tag set plus your converters become native UI; everything else is raw text.
  There is no general HTML renderer.
- Converter emits a custom block but it doesn't show: missing the paired renderer extension.
- Don't re-parse raw HTML yourself inside a converter; use the provided `convertChildren` / `convertInlines` lambdas.
- Inline-HTML rendering and most non-CommonMark HTML features are explicitly out of scope upstream; validate against current sources before
  promising support.
