# Code highlighting

How fenced/indented code blocks get syntax highlighting in Jewel Markdown. Read this when code blocks render as plain text, when wiring a
highlighter, or when writing a custom renderer that must keep highlighting working.

## How it works

- The block renderer renders code via `LocalCodeHighlighter.current` (a composition local of type `CodeHighlighter`), not via a hardcoded
  lexer.
- `CodeHighlighter` (file: `platform/jewel/foundation/.../code/highlighting/CodeHighlighter.kt`) exposes:
  - `highlight(code: String, language: String = ""): Flow<AnnotatedString>` — current API. `language` is the fenced info string (`kt`,
    `python`, `js`, etc.).
  - `highlight(code, mimeType: MimeType?)` — deprecated; do not use in new code.
- It returns a `Flow<AnnotatedString>`, not a single value, so a highlighter can emit progressively (e.g., a fast pass, then a richer one)
  and re-emit when the color scheme changes. The renderer collects it with `collectAsState(AnnotatedString(content))`, so the raw text shows
  until the first highlighted value arrives.
- For static highlighting, emit a single value (`flowOf(highlighted)`); the renderer uses the first emission.

## Default behavior

Whether you get highlighting out of the box depends on which `ProvideMarkdownStyling` you use:

- IDE bridge (`ide-laf-bridge-styling`), `Project`-aware overloads: **highlighting is on by default.** These overloads build an IJPL-backed
  highlighter via `project.service<CodeHighlighterFactory>().createHighlighter()`, so fenced code is highlighted using the IDE's own syntax
  highlighting. Prefer these overloads inside a plugin.
- IDE bridge, overloads **without** a `Project`: default to `NoOpCodeHighlighter` (no highlighting) unless you pass a `codeHighlighter`. The
  KDoc explicitly steers you to the `Project` overload when you have one.
- Standalone (`int-ui-standalone-styling`): currently defaults to `NoOpCodeHighlighter`, so standalone apps that want the highlighting must
  supply a `CodeHighlighter`.
  - This is changing: JEWEL-1313 adds lexer-based highlighting for standalone, initially for a limited set of languages, expected to expand
    over time. Validate against ground truth before assuming standalone has no built-in highlighting.

The underlying default value of the `codeHighlighter` parameter is `NoOpCodeHighlighter`: it emits the code as a plain `AnnotatedString`
with no styling. So "no colors" is expected
only when a no-op highlighter is in effect (standalone, or a bridge overload without a `Project`), not in the `Project`-aware bridge path.

## How the default renderer dispatches

In `DefaultMarkdownBlockRenderer.RenderFencedCodeBlock`:

- If the info string looks like a MIME type (matches `^\w+/.+$`), it goes through the deprecated `RenderCodeWithMimeType` path.
- Otherwise, it calls `RenderCodeWithLanguage`, which calls `highlighter.highlight(content, block.language.orEmpty())`.

Prefer plain language names/extensions in fenced blocks (` ```kotlin `) so the modern `highlight(code, language)` path is used.

## Wiring a highlighter

```kotlin
ProvideMarkdownStyling(
  markdownStyling = styling,
  markdownBlockRenderer = blockRenderer,
  codeHighlighter = myCodeHighlighter, // defaults to NoOpCodeHighlighter
) {
  Markdown(blocks)
}
```

Or provide `LocalCodeHighlighter` directly if you are composing your own provider stack.

In a plugin, prefer the `Project`-aware bridge `ProvideMarkdownStyling` overload: it wires the IJPL `CodeHighlighterFactory` highlighter for
you, so don't hand-roll one. Only standalone apps (or bridge code with no `Project`) need to supply their own `CodeHighlighter`
implementation (e.g., backed by TextMate bundles or another lexer).

## Implementing a custom `CodeHighlighter`

- Implement `highlight(code, language)`. Resolve the language from the raw string yourself; do not rely on the deprecated `MimeType`
  resolution.
- Return a `Flow`. Emit once for static highlighting or multiple times if you support theme changes/async enrichment.
- If `language` is blank or unknown, emit the original code as a plain `AnnotatedString` (mirror `NoOpCodeHighlighter`).

## Gotchas

- "No colors": check which styling path is in use. In the `Project`-aware bridge overload highlighting is already on; in standalone or a
  bridge overload without a `Project` the default is `NoOpCodeHighlighter` and you must provide a highlighter. If a plugin shows no colors,
  check whether the non-`Project` overload was used by mistake.
- The flow's first emission should be safe/cheap; the UI shows raw text until then. Don't block.
- A custom block renderer that re-implements code block rendering must still read `LocalCodeHighlighter.current` and collect its flow, or
  highlighting breaks. Prefer subclassing `DefaultMarkdownBlockRenderer` and overriding only what you need.
- The `MimeType`-based APIs are deprecated and don't scale to languages defined outside the `MimeType` enum (e.g. TextMate grammars). Use
  the `language`-string overload.
