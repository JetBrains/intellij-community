# Image loading

How images in Markdown become visible pixels in Jewel. Read this when images don't show, when wiring image loading, or when resolving
relative/custom image paths.

## Two independent pieces

Image support needs both of these; they solve different problems:

1. `ImageRendererExtension` — actually loads and renders the image. Without one, images are not displayed; the default inline renderer falls
   back to rendering the image's raw Markdown syntax as text.
2. `ImageSourceResolver` — turns the raw Markdown destination string (`![alt](my-image.png)`) into a fully qualified, loadable source
   string. Provided via the `LocalMarkdownImageSourceResolver` composition local.

A renderer extension without a resolver may fail on relative paths; a resolver without a renderer extension still shows nothing. Wire both
for non-trivial cases.

## ImageRendererExtension

- Interface `ImageRendererExtension`.
- It is exposed from a `MarkdownRendererExtension.imageRendererExtension` and added to the renderer's extension list.
- The default block renderer collects images from a block's inline content and asks the first available `imageRendererExtension` to render
  each one (`renderedImages(...)` in `DefaultMarkdownBlockRenderer`). Returning `null` means "no image" (e.g., load error) and the renderer
  drops the placeholder.

### Coil3 reference implementation

- See `Coil3ImageRendererExtension`.
- Construct with an app-wide Coil `ImageLoader`: `Coil3ImageRendererExtension(imageLoader)`.
- Convenience: `Coil3ImageRendererExtension.withDefaultLoader()` / `withDefaultLoader(context)` create a loader with a small in-memory
  cache. Each call creates a new `ImageLoader`; create one and share it process-wide rather than calling repeatedly.
- The impl resolves the source via `LocalMarkdownImageSourceResolver.current`, requests `Size.ORIGINAL`, renders into an `InlineTextContent`
  whose placeholder is resized to the loaded image's pixel size, and returns `null` on error.

## ImageSourceResolver

- Interface `ImageSourceResolver`.
- Provided via `LocalMarkdownImageSourceResolver`; the default value is `ImageSourceResolver.create()` with default capabilities.
- Build one with `ImageSourceResolver.create(resolveCapabilities, logResolveFailure)` or
  `ImageSourceResolver.create(rootDir, logResolveFailure)`.

### Default capabilities

`ImageSourceResolver.create()` supports:

- `PlainUri` — absolute URIs returned as-is (`https://...`, `file:///...`).
- `RelativePathInResources(resourceClass?)` — relative path looked up in a classloader's resources.
- `AbsolutePath` — absolute filesystem paths returned as-is.

The `create(rootDir, ...)` overload adds `RelativePath(rootDir)` so relative paths resolve against a base directory.

Capabilities are tried in order; the first non-null result wins. If none resolve and `logResolveFailure` is true, the resolver logs the
failure and returns `null` (image won't load).

## Wiring

```kotlin
// Provide a resolver (e.g. resolve relative paths against a doc root)
val resolver = ImageSourceResolver.create(rootDir = docRoot, logResolveFailure = true)

ProvideMarkdownStyling(
  imageSourceResolver = resolver,           // overload that seeds LocalMarkdownImageSourceResolver
  markdownStyling = styling,
  markdownBlockRenderer =
    MarkdownBlockRenderer.light(
      styling = styling,
      rendererExtensions = listOf(Coil3ImageRendererExtension(imageLoader)),
    ),
  codeHighlighter = codeHighlighter,
) {
  Markdown(blocks)
}
```

For a fully custom scheme (e.g., a base URL or asset pipeline), implement `ImageSourceResolver` directly and provide it via
`LocalMarkdownImageSourceResolver` or `ProvideMarkdownStyling`.

## Sized images

Images can carry explicit dimensions, parsed into `InlineMarkdown.Image.width`/`height` (type `DimensionSize?`). 

Two source syntaxes are parsed:

- GitLab Markdown image attribute block: `![alt](image.png){width=100 height=50}` — appended immediately after the image. Values may be
  unquoted, single-, or double-quoted; a unitless number or a `px` suffix is accepted. The attribute block is consumed from the following
  text node, so it does not show up as literal text.
- HTML `<img>` attributes: `<img src="image.png" width="100" height="50">` (requires `parseEmbeddedHtml = true`). The `width`/`height`
  attributes are parsed the same way.

`DimensionSize` currently has a single variant, `DimensionSize.Pixels(value)` (non-negative). Only pixel/unitless values are supported right
now; percentage and other units are not yet parsed (`%` is tracked as a TODO under JEWEL-1333). Unparseable or unsupported values resolve to
`null` (treated as unspecified), and a `{ ... }` block that yields no width/height is left as literal text.

### How dimensions affect rendering

`InlineMarkdown.Image`'s KDoc defines the contract that standard renderers follow, and `Coil3ImageRendererExtensionImpl` implements it:

- Both `width` and `height` specified: the image is rendered at exactly those dimensions (`ContentScale.FillBounds`), stretching if the
  aspect ratio differs from the original.
- Only one specified: the other is scaled proportionally from the loaded image's aspect ratio (`ContentScale.Fit`).
- Neither specified: the image renders at its intrinsic loaded size (existing behavior).

While loading, a known pixel dimension is reserved for the placeholder; unspecified dimensions fall back to a minimal placeholder until the
image loads.

If you write a custom `ImageRendererExtension`, read `image.width` / `image.height` and apply the same three-way rule yourself; the
dimensions are data on the node, not something the renderer gets for free.

## Custom image rendering

Implement `ImageRendererExtension.renderImageContent` to return your own `InlineTextContent` (size placeholder + composable). Resolve the
raw source through `LocalMarkdownImageSourceResolver.current` so path handling stays consistent. Return `null` to render nothing for an
image (e.g., on error).

## Gotchas

- "Images show as text/markdown": no `ImageRendererExtension` is wired. Add one.
- "Remote images work, local ones don't": the resolver lacks a capability for that path shape (e.g., relative path with no `rootDir`). Use
  the `rootDir` overload or a custom resolver.
- Styling vs loading: `MarkdownStyling.Image` controls alignment/scale/border/background; it does not load images. Loading is the
  extension + resolver.
- Share a single Coil `ImageLoader`; don't call `withDefaultLoader()` repeatedly.
- Sized images: only pixel/unitless dimensions are supported; `%` and other units are ignored (parsed as unspecified) for now. A custom
  `ImageRendererExtension` must honor `image.width` / `image.height` itself, or sizing silently has no effect.
