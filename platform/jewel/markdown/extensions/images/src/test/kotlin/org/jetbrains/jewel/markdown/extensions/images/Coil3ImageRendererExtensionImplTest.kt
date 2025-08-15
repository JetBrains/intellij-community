// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.markdown.extensions.images

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import coil3.ColorImage
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.annotation.ExperimentalCoilApi
import coil3.decode.DataSource
import coil3.request.ErrorResult
import coil3.request.SuccessResult
import coil3.test.FakeImageLoaderEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import org.jetbrains.jewel.markdown.InlineMarkdown
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoilApi::class)
public class Coil3ImageRendererExtensionImplTest {
    @get:Rule public val composeTestRule: ComposeContentTestRule = createComposeRule()

    private val platformContext: PlatformContext = PlatformContext.Companion.INSTANCE
    private val imageUrl = "https://example.com/image.png"

    @Test
    public fun `image renders with correct size on success`() {
        val fakeImageWidth = 150
        val fakeImageHeight = 100
        val fakeImage = ColorImage(Color.Companion.Red.toArgb(), width = fakeImageWidth, height = fakeImageHeight)

        val engine = FakeImageLoaderEngine.Builder().intercept({ it == imageUrl }, fakeImage).build()

        val imageLoaderWithFakeEngine = ImageLoader.Builder(platformContext).components { add(engine) }.build()

        val extension = Coil3ImageRendererExtensionImpl(imageLoaderWithFakeEngine)
        val imageMarkdown =
            InlineMarkdown.Image(source = imageUrl, alt = "Alt text", title = "Image loaded successfully")

        setContent(extension, imageMarkdown)

        composeTestRule
            .onNodeWithContentDescription("Image loaded successfully")
            .assertExists()
            .assertWidthIsEqualTo(fakeImageWidth.dp)
            .assertHeightIsEqualTo(fakeImageHeight.dp)
    }

    @Test
    public fun `placeholder remains small on error`() {
        val engine =
            FakeImageLoaderEngine.Builder()
                .intercept(
                    predicate = { it == imageUrl },
                    interceptor = { ErrorResult(null, it.request, IllegalStateException("Failed to load")) },
                )
                .build()

        val imageLoaderWithFakeEngine = ImageLoader.Builder(platformContext).components { add(engine) }.build()

        val extension = Coil3ImageRendererExtensionImpl(imageLoaderWithFakeEngine)
        val imageMarkdown = InlineMarkdown.Image(source = imageUrl, alt = "Alt text", title = "Failed to load image")

        setContent(extension, imageMarkdown)

        composeTestRule
            .onNodeWithContentDescription("Failed to load image")
            .assertExists()
            .assertWidthIsEqualTo(0.dp)
            .assertHeightIsEqualTo(1.dp)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    public fun `placeholder remains small during loading state then changes to image size`() {
        val testDispatcher = StandardTestDispatcher()

        val fakeImageWidth = 150
        val fakeImageHeight = 150
        val fakeImage = ColorImage(Color.Companion.Red.toArgb(), width = fakeImageWidth, height = fakeImageHeight)
        val engine =
            FakeImageLoaderEngine.Builder()
                .intercept(
                    predicate = { it == imageUrl },
                    interceptor = {
                        delay(500) // simulating network delay

                        SuccessResult(fakeImage, it.request, DataSource.MEMORY)
                    },
                )
                .build()

        val imageLoader =
            ImageLoader.Builder(platformContext).components { add(engine) }.coroutineContext(testDispatcher).build()

        val extension = Coil3ImageRendererExtensionImpl(imageLoader)
        val imageMarkdown = InlineMarkdown.Image(source = imageUrl, alt = "Alt text", title = "A loading image")

        setContent(extension, imageMarkdown)

        composeTestRule
            .onNodeWithContentDescription("A loading image")
            .assertExists()
            .assertWidthIsEqualTo(0.dp)
            .assertHeightIsEqualTo(1.dp)

        // fast forwarding coil's internal dispatcher
        testDispatcher.scheduler.advanceTimeBy(501)
        testDispatcher.scheduler.runCurrent()

        composeTestRule
            .onNodeWithContentDescription("A loading image")
            .assertWidthIsAtLeast(fakeImageWidth.dp)
            .assertHeightIsAtLeast(fakeImageHeight.dp)
    }

    private fun setContent(extension: Coil3ImageRendererExtensionImpl, image: InlineMarkdown.Image) {
        composeTestRule.setContent {
            val inlineContent = mapOf("inlineTextContent" to extension.renderImageContent(image))
            val annotatedString = buildAnnotatedString {
                append("Rendered inline text image: ")
                appendInlineContent("inlineTextContent", "[rendered image]")
            }
            BasicText(text = annotatedString, inlineContent = inlineContent)
        }
    }
}
