# Images module

This module adds support for inline images, typically rendered from `![alt](url)` syntax.

It's based on core CommonMark but allows users to not depend on Coil if they don't need images support and avoid runtime image loading dependencies.

* No Coil or UI image loading logic is included in the image parser module.
* Keeps the structure modular, consistent with how other extensions are handled.

## Usage

To use the image module, add the `Coil3ImagesRendererExtension` to your `rendererExtensions`.

While this extension provides a utility function to create an instance quickly, you should always prefer using a shared `ImageLoader` instance for
the entire application/plugin instead, so it can share the cache and contain memory usage. The utility function creates a basic loader with a small
in-memory cache only, and it is not recommended to be used except in very simple cases.
