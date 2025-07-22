// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.markdown.extensions.images

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.test.FakeImageLoaderEngine
import junit.framework.TestCase.assertEquals
import org.junit.Test

public class ImageSourceResolverTest {
    private val dummyImageLoader: ImageLoader =
        ImageLoader.Builder(PlatformContext.INSTANCE)
            .components { add(FakeImageLoaderEngine.Builder().build()) }
            .build()

    @Test
    public fun `resolveImageSource returns raw destination for full URI`() {
        val extension = Coil3ImagesRendererExtensionImpl(dummyImageLoader)
        val fullUri = "https://example.com/image.png"

        val result = extension.resolveImageSource(fullUri)

        assertEquals(fullUri, result)
    }

    @Test
    public fun `resolveImageSource resolves existing classpath resource`() {
        // This test requires a file named `test-image.svg` to exist in `src/test/resources/`.
        val extension = Coil3ImagesRendererExtensionImpl(dummyImageLoader)
        val resourceName = "test-image.svg"

        val result = extension.resolveImageSource(resourceName)

        assert(result.startsWith("file:/"))
        assert(result.endsWith(resourceName))
    }

    @Test
    public fun `resolveImageSource returns raw destination for non-existent resource`() {
        val extension = Coil3ImagesRendererExtensionImpl(dummyImageLoader)
        val nonExistentResource = "this_file_does_not_exist.jpg"

        val result = extension.resolveImageSource(nonExistentResource)

        // It should return the original string, which will later cause Coil to fail.
        assertEquals(nonExistentResource, result)
    }
}
