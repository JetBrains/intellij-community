## Jewel Markdown Renderer

> [!IMPORTANT]
> The Jewel Markdown renderer is currently considered **experimental**. Its API and implementations may change at any
> time, and no guarantees are made for binary and source compatibility. It might also have bugs and missing features.

Adds the ability to render Markdown as native Compose UI.

Currently supports the [CommonMark 0.31.2](https://spec.commonmark.org/0.31.2/) specs.

Additional supported Markdown, via extensions:

* Alerts ([GitHub Flavored Markdown][alerts-specs]) — see [`extension-gfm-alerts`](extension-gfm-alerts)

[alerts-specs]: https://github.com/orgs/community/discussions/16925

On the roadmap, but not currently supported — in no particular order:

* Tables ([GitHub Flavored Markdown](https://github.github.com/gfm/#tables-extension-))
* Strikethrough ([GitHub Flavored Markdown](https://github.github.com/gfm/#strikethrough-extension-))
* Image loading (via [Coil 3](https://coil-kt.github.io/coil/upgrading_to_coil3/))
* Auto-linking ([GitHub Flavored Markdown](https://github.github.com/gfm/#autolinks-extension-))
* Task list items ([GitHub Flavored Markdown](https://github.github.com/gfm/#task-list-items-extension-))
* Keyboard shortcuts highlighting (specialized HTML handling)
* Collapsing sections ([GitHub Flavored Markdown][details-specs])
* Theme-sensitive image loading ([GitHub Flavored Markdown][dark-mode-pics-specs])
* Emojis ([GitHub Flavored Markdown][emoji-specs])
* Footnotes ([GitHub Flavored Markdown][footnotes-specs])

[details-specs]: https://docs.github.com/en/get-started/writing-on-github/working-with-advanced-formatting/organizing-information-with-collapsed-sections

[dark-mode-pics-specs]: https://docs.github.com/en/get-started/writing-on-github/getting-started-with-writing-and-formatting-on-github/basic-writing-and-formatting-syntax#specifying-the-theme-an-image-is-shown-to

[emoji-specs]: https://docs.github.com/en/get-started/writing-on-github/getting-started-with-writing-and-formatting-on-github/basic-writing-and-formatting-syntax#using-emojis

[footnotes-specs]: https://docs.github.com/en/get-started/writing-on-github/getting-started-with-writing-and-formatting-on-github/basic-writing-and-formatting-syntax#footnotes

Not supported, and not on the roadmap:

* Inline HTML rendering
* Mermaid diagrams (GitHub Flavored Markdown)
* LaTeX rendering, both inline and not (GitHub Flavored Markdown)
* topoJSON/geoJSON rendering (GitHub Flavored Markdown)
* 3D STL models (GitHub Flavored Markdown)
* Rich rendering of embeds such as videos, YouTube, GitHub Gists/...

## Add the Markdown renderer to your project

You need to add the renderer **alongside** either a `jewel-standalone` or `jewel-ide-laf-bridge-*` dependency in order
for the renderer to work, as it assumes that the necessary `jewel-ui` and `jewel-foundation` are on the classpath 
already.

If you want to use extensions, you also need to add them **alongside** the `jewel-markdown-core`:

```kotlin
dependencies {
    implementation(libs.jewel.standalone)
    implementation(libs.jewel.markdown.core)
    implementation(libs.jewel.markdown.extension.gfm.alerts) // Optional
    // Et cetera...
}
```

## How to use Jewel's Markdown renderer

The process that leads to rendering Markdown in a native UI is two-pass.

The first pass is an upfront rendering that pre-processes blocks into `MarkdownBlock`s but doesn't touch the inline
Markdown. It's recommended to run this outside of the composition, since it has no dependencies on it.

```kotlin
// Somewhere outside of composition...
val processor = MarkdownProcessor()
val rawMarkdown = "..."
val processedBlocks = processor.processMarkdownDocument(rawMarkdown)
```

The second pass is done in the composition, and essentially renders a series of `MarkdownBlock`s into native Jewel UI:

```kotlin
@Composable
fun Markdown(blocks: List<MarkdownBlock>) {
    val isDark = JewelTheme.isDark
    val markdownStyling =
        remember(isDark) { if (isDark) MarkdownStyling.dark() else MarkdownStyling.light() }
    val blockRenderer = remember(markdownStyling, isDark) {
        if (isDark) MarkdownBlockRenderer.dark() else MarkdownBlockRenderer.light()
    }

    SelectionContainer(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(markdownStyling.blockVerticalSpacing),
        ) {
            blockRenderer.render(blocks)
        }
    }
}
```

If you expect long Markdown documents, you can also use a `LazyColumn` to get better performances.

### Using extensions

By default, the processor will ignore any kind of Markdown it doesn't support. To support additional features, such as
ones found in GitHub Flavored Markdown, you can use extensions. If you don't specify any extension, the processor will
be restricted to the [CommonMark specs](https://specs.commonmark.org) as supported by
[`commonmark-java`](https://github.com/commonmark/commonmark-java).

Extensions are composed of two parts: a parsing and a rendering part. The two parts need to be passed to the
`MarkdownProcessor` and `MarkdownBlockRenderer`, respectively:

```kotlin
// Where the parsing happens...
val parsingExtensions = listOf(/*...*/)
val processor = MarkdownProcessor(extensions)

// Where the rendering happens...
val blockRenderer = remember(markdownStyling, isDark) {
    if (isDark) {
        MarkdownBlockRenderer.dark(
            rendererExtensions = listOf(/*...*/),
            inlineRenderer = InlineMarkdownRenderer.default(parsingExtensions),
        )
    } else {
        MarkdownBlockRenderer.light(
            rendererExtensions = listOf(/*...*/),
            inlineRenderer = InlineMarkdownRenderer.default(parsingExtensions),
        )
    }
}
```

It is strongly recommended to use the corresponding set of rendering extensions as the ones used for parsing, otherwise
the custom blocks will be parsed but not rendered.

Note that you should create an `InlineMarkdownRenderer` with the same list of extensions that was used to build the
processor, as even though inline rendering extensions are not supported yet, they will be in the future.
