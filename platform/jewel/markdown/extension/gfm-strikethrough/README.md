# GitHub Flavored Markdown — strikethrough extension

This extension adds support for strikethrough,
a [GFM extension](https://github.github.com/gfm/#strikethrough-extension-) over
CommonMark. Strikethrough nodes are parsed by the `commonmark-ext-gfm-strikethrough` library, and rendered simply by
wrapping their content in a `SpanStyle` that applies the `LineThrough` decoration.

![Screenshot of a strikethrough](../../../art/docs/gfm-strikethrough.png)

## Usage

To use the strikethrough extension, you need to add the `GitHubStrikethroughProcessorExtension` to your
`MarkdownProcessor`, and the
`GitHubStrikethroughRendererExtension` to the `MarkdownBlockRenderer`. For example, in standalone mode:

```kotlin
val isDark = JewelTheme.isDark

val markdownStyling = remember(isDark) { if (isDark) MarkdownStyling.dark() else MarkdownStyling.light() }

val processor = remember { MarkdownProcessor(listOf(GitHubStrikethroughProcessorExtension)) }

val blockRenderer =
    remember(markdownStyling) {
        if (isDark) {
            MarkdownBlockRenderer.dark(
                styling = markdownStyling,
                rendererExtensions = listOf(GitHubStrikethroughRendererExtension),
            )
        } else {
            MarkdownBlockRenderer.light(
                styling = markdownStyling,
                rendererExtensions = listOf(GitHubStrikethroughRendererExtension),
            )
        }
    }

ProvideMarkdownStyling(markdownStyling, blockRenderer, NoOpCodeHighlighter) {
    // Your UI that renders Markdown goes here
}
```