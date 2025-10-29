// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.markdown.rendering

import junit.framework.TestCase
import junit.framework.TestCase.assertNull
import org.jetbrains.jewel.markdown.assertNotNull
import org.junit.Test

public class DefaultImageSourceResolverTest {
    @Test
    public fun `resolves full URI as raw destination`() {
        val fullUri = "https://example.com/image.png"

        val result = DefaultImageSourceResolver().resolve(fullUri)

        TestCase.assertEquals(fullUri, result)
    }

    @Test
    public fun `resolves full file URI as raw destination`() {
        val fullUri = "file:///image.png"

        val result = DefaultImageSourceResolver().resolve(fullUri)

        TestCase.assertEquals(fullUri, result)
    }

    @Test
    public fun `resolves existing classpath resource`() {
        // This test requires a file named `test-image.svg` to exist in `src/test/resources/`.
        val resourceName = "test-image.svg"

        val result = DefaultImageSourceResolver().resolve(resourceName)
        result.assertNotNull()

        assert(result.startsWith("file:/") || result.startsWith("jar:file:/")) {
            "Expected result to start with 'file:/' or 'jar:file:/', but got '$result'"
        }
        assert(result.endsWith(resourceName)) { "Expected result to end with '$resourceName', but got '$result'" }
    }

    @Test
    public fun `resolves existing classpath resource with slash prefix`() {
        // This test requires a file named `test-image.svg` to exist in `src/test/resources/`.
        val resourceName = "/test-image.svg"

        val result = DefaultImageSourceResolver().resolve(resourceName)
        result.assertNotNull()

        assert(result.startsWith("file:/") || result.startsWith("jar:file:/")) {
            "Expected result to start with 'file:/' or 'jar:file:/', but got '$result'"
        }
        assert(result.endsWith(resourceName)) { "Expected result to end with '$resourceName', but got '$result'" }
    }

    @Test
    public fun `resolves absolute paths`() {
        val absolutePath = "/absolute/path/to/image.png"

        val result = DefaultImageSourceResolver().resolve(absolutePath)

        TestCase.assertEquals(absolutePath, result)
    }

    @Test
    public fun `doesn't resolve non-existent resource`() {
        val nonExistentResource = "this_file_does_not_exist.jpg"

        val result = DefaultImageSourceResolver().resolve(nonExistentResource)
        assertNull(result)
    }

    @Test
    public fun `doesn't resolve URIs without capability`() {
        val fullUri = "https://example.com/image.png"

        val result =
            DefaultImageSourceResolver(
                    resolveCapabilities = setOf(ImageSourceResolver.ResolveCapability.RelativePathInResources())
                )
                .resolve(fullUri)
        assertNull(result)
    }

    @Test
    public fun `doesn't resolve existing classpath resource without capability`() {
        val resourceName = "/test-image.svg"

        val result =
            DefaultImageSourceResolver(resolveCapabilities = setOf(ImageSourceResolver.ResolveCapability.PlainUri))
                .resolve(resourceName)
        assertNull(result)
    }
}
