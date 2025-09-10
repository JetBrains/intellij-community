// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.markdown.rendering

import junit.framework.TestCase
import org.junit.Test

public class DefaultImageSourceResolverTest {
    @Test
    public fun `resolveImageSource returns raw destination for full URI`() {
        val fullUri = "https://example.com/image.png"

        val result = DefaultImageSourceResolver.resolve(fullUri)

        TestCase.assertEquals(fullUri, result)
    }

    @Test
    public fun `resolveImageSource resolves existing classpath resource`() {
        // This test requires a file named `test-image.svg` to exist in `src/test/resources/`.
        val resourceName = "test-image.svg"

        val result = DefaultImageSourceResolver.resolve(resourceName)

        assert(result.startsWith("file:/") || result.startsWith("jar:file:/")) {
            "Expected result to start with 'file:/' or 'jar:file:/', but got '$result'"
        }
        assert(result.endsWith(resourceName)) { "Expected result to end with '$resourceName', but got '$result'" }
    }

    @Test
    public fun `resolveImageSource with slash prefix resolves existing classpath resource`() {
        // This test requires a file named `test-image.svg` to exist in `src/test/resources/`.
        val resourceName = "/test-image.svg"

        val result = DefaultImageSourceResolver.resolve(resourceName)

        assert(result.startsWith("file:/") || result.startsWith("jar:file:/")) {
            "Expected result to start with 'file:/' or 'jar:file:/', but got '$result'"
        }
        assert(result.endsWith(resourceName)) { "Expected result to end with '$resourceName', but got '$result'" }
    }

    @Test
    public fun `resolveImageSource returns raw destination for non-existent resource`() {
        val nonExistentResource = "this_file_does_not_exist.jpg"

        val result = DefaultImageSourceResolver.resolve(nonExistentResource)

        // It should return the original string, which will later cause Coil to fail.
        TestCase.assertEquals(nonExistentResource, result)
    }
}
