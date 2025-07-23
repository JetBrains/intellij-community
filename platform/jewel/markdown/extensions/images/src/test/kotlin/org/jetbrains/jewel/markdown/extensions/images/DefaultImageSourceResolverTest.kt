// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.markdown.extensions.images

import junit.framework.TestCase.assertEquals
import org.junit.Test

public class DefaultImageSourceResolverTest {
    @Test
    public fun `resolveImageSource returns raw destination for full URI`() {
        val defaultImageSourceResolver = DefaultImageSourceResolver()
        val fullUri = "https://example.com/image.png"

        val result = defaultImageSourceResolver.resolve(fullUri)

        assertEquals(fullUri, result)
    }

    @Test
    public fun `resolveImageSource resolves existing classpath resource`() {
        // This test requires a file named `test-image.svg` to exist in `src/test/resources/`.
        val defaultImageSourceResolver = DefaultImageSourceResolver()
        val resourceName = "test-image.svg"

        val result = defaultImageSourceResolver.resolve(resourceName)

        assert(result.startsWith("file:/"))
        assert(result.endsWith(resourceName))
    }

    @Test
    public fun `resolveImageSource with slash prefix resolves existing classpath resource`() {
        // This test requires a file named `test-image.svg` to exist in `src/test/resources/`.
        val defaultImageSourceResolver = DefaultImageSourceResolver()
        val resourceName = "/test-image.svg"

        val result = defaultImageSourceResolver.resolve(resourceName)

        assert(result.startsWith("file:/"))
        assert(result.endsWith(resourceName))
    }

    @Test
    public fun `resolveImageSource returns raw destination for non-existent resource`() {
        val defaultImageSourceResolver = DefaultImageSourceResolver()
        val nonExistentResource = "this_file_does_not_exist.jpg"

        val result = defaultImageSourceResolver.resolve(nonExistentResource)

        // It should return the original string, which will later cause Coil to fail.
        assertEquals(nonExistentResource, result)
    }
}
