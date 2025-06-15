# Images module

This module adds support for inline images, typically rendered from `![alt](url)` syntax.
It's based on core CommonMark but allows users to not depend on Coil if they don't need images support
and avoiding runtime image loading dependencies.

* No Coil or UI image loading logic is included in the image parser module.
* Keeps the structure modular, consistent with how other extensions are handled.

## Usage

To use the image module, add the `Coil3ImagesRendererExtension` to your `rendererExtensions`.
If you don't want to use library-specific image loader, you can pass it as a parameter,
but you have to handle its initialization as well as making sure it's the same instance.
