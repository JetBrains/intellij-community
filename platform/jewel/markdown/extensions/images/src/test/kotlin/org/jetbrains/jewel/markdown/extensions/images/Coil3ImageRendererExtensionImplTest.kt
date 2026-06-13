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
import org.jetbrains.jewel.markdown.DimensionSize
import org.jetbrains.jewel.markdown.InlineMarkdown
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoilApi::class)
@Suppress("LargeClass")
public class Coil3ImageRendererExtensionImplTest {
    @get:Rule public val composeTestRule: ComposeContentTestRule = createComposeRule()

    private val platformContext: PlatformContext = PlatformContext.INSTANCE
    private val imageUrl = "https://example.com/image.png"

    @Test
    public fun `image renders with correct size on success`() {
        val fakeImageWidth = 150
        val fakeImageHeight = 100
        val fakeImage = ColorImage(Color.Red.toArgb(), width = fakeImageWidth, height = fakeImageHeight)

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

        composeTestRule.onNodeWithContentDescription("Failed to load image").assertDoesNotExist()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    public fun `placeholder remains small during loading state then changes to image size`() {
        val testDispatcher = StandardTestDispatcher()

        val fakeImageWidth = 150
        val fakeImageHeight = 150
        val fakeImage = ColorImage(Color.Red.toArgb(), width = fakeImageWidth, height = fakeImageHeight)
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

    @Test
    public fun `image renders with specified pixel width and height`() {
        val fakeImageWidth = 200
        val fakeImageHeight = 100
        val fakeImage = ColorImage(Color.Blue.toArgb(), width = fakeImageWidth, height = fakeImageHeight)

        val engine = FakeImageLoaderEngine.Builder().intercept({ it == imageUrl }, fakeImage).build()

        val imageLoaderWithFakeEngine = ImageLoader.Builder(platformContext).components { add(engine) }.build()

        val extension = Coil3ImageRendererExtensionImpl(imageLoaderWithFakeEngine)

        // Specify different dimensions than the actual image
        val specifiedWidth = 100
        val specifiedHeight = 50
        val imageMarkdown =
            InlineMarkdown.Image(
                source = imageUrl,
                alt = "Alt text",
                title = "Sized image",
                width = DimensionSize.Pixels(specifiedWidth),
                height = DimensionSize.Pixels(specifiedHeight),
                inlineContent = emptyList(),
            )

        setContent(extension, imageMarkdown)

        composeTestRule
            .onNodeWithContentDescription("Sized image")
            .assertExists()
            .assertWidthIsEqualTo(specifiedWidth.dp)
            .assertHeightIsEqualTo(specifiedHeight.dp)
    }

    @Test
    public fun `image with only pixel width specified scales height proportionally`() {
        val fakeImageWidth = 200
        val fakeImageHeight = 100
        val fakeImage = ColorImage(Color.Green.toArgb(), width = fakeImageWidth, height = fakeImageHeight)

        val engine = FakeImageLoaderEngine.Builder().intercept({ it == imageUrl }, fakeImage).build()

        val imageLoaderWithFakeEngine = ImageLoader.Builder(platformContext).components { add(engine) }.build()

        val extension = Coil3ImageRendererExtensionImpl(imageLoaderWithFakeEngine)

        // Specify only width - height should be scaled proportionally
        val specifiedWidth = 100
        // Expected height: 100 / 200 * 100 = 50
        val expectedHeight = 50
        val imageMarkdown =
            InlineMarkdown.Image(
                source = imageUrl,
                alt = "Alt text",
                title = "Width only image",
                width = DimensionSize.Pixels(specifiedWidth),
                height = null,
                inlineContent = emptyList(),
            )

        setContent(extension, imageMarkdown)

        composeTestRule
            .onNodeWithContentDescription("Width only image")
            .assertExists()
            .assertWidthIsEqualTo(specifiedWidth.dp)
            .assertHeightIsEqualTo(expectedHeight.dp)
    }

    @Test
    public fun `image with only pixel height specified scales width proportionally`() {
        val fakeImageWidth = 200
        val fakeImageHeight = 100
        val fakeImage = ColorImage(Color.Yellow.toArgb(), width = fakeImageWidth, height = fakeImageHeight)

        val engine = FakeImageLoaderEngine.Builder().intercept({ it == imageUrl }, fakeImage).build()

        val imageLoaderWithFakeEngine = ImageLoader.Builder(platformContext).components { add(engine) }.build()

        val extension = Coil3ImageRendererExtensionImpl(imageLoaderWithFakeEngine)

        // Specify only height - width should be scaled proportionally
        val specifiedHeight = 50
        // Expected width: 50 / 100 * 200 = 100
        val expectedWidth = 100
        val imageMarkdown =
            InlineMarkdown.Image(
                source = imageUrl,
                alt = "Alt text",
                title = "Height only image",
                width = null,
                height = DimensionSize.Pixels(specifiedHeight),
                inlineContent = emptyList(),
            )

        setContent(extension, imageMarkdown)

        composeTestRule
            .onNodeWithContentDescription("Height only image")
            .assertExists()
            .assertWidthIsEqualTo(expectedWidth.dp)
            .assertHeightIsEqualTo(specifiedHeight.dp)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    public fun `placeholder with specified pixel dimensions shows correct size during loading`() {
        val testDispatcher = StandardTestDispatcher()

        val fakeImageWidth = 200
        val fakeImageHeight = 100
        val fakeImage = ColorImage(Color.Magenta.toArgb(), width = fakeImageWidth, height = fakeImageHeight)
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

        val specifiedWidth = 150
        val specifiedHeight = 75
        val imageMarkdown =
            InlineMarkdown.Image(
                source = imageUrl,
                alt = "Alt text",
                title = "Loading sized image",
                width = DimensionSize.Pixels(specifiedWidth),
                height = DimensionSize.Pixels(specifiedHeight),
                inlineContent = emptyList(),
            )

        setContent(extension, imageMarkdown)

        // During loading, placeholder should have the specified dimensions
        composeTestRule
            .onNodeWithContentDescription("Loading sized image")
            .assertExists()
            .assertWidthIsEqualTo(specifiedWidth.dp)
            .assertHeightIsEqualTo(specifiedHeight.dp)

        // Fast forward to complete loading
        testDispatcher.scheduler.advanceTimeBy(501)
        testDispatcher.scheduler.runCurrent()

        // After loading, should still have the specified dimensions
        composeTestRule
            .onNodeWithContentDescription("Loading sized image")
            .assertWidthIsEqualTo(specifiedWidth.dp)
            .assertHeightIsEqualTo(specifiedHeight.dp)
    }

    @Test
    public fun `image with percentage width scales to percentage of original`() {
        val fakeImageWidth = 200
        val fakeImageHeight = 100
        val fakeImage = ColorImage(Color.Cyan.toArgb(), width = fakeImageWidth, height = fakeImageHeight)

        val engine = FakeImageLoaderEngine.Builder().intercept({ it == imageUrl }, fakeImage).build()

        val imageLoaderWithFakeEngine = ImageLoader.Builder(platformContext).components { add(engine) }.build()

        val extension = Coil3ImageRendererExtensionImpl(imageLoaderWithFakeEngine)

        // Specify 50% width - should be 100px (50% of 200)
        // Height should scale proportionally: 100 / 200 * 100 = 50
        val imageMarkdown =
            InlineMarkdown.Image(
                source = imageUrl,
                alt = "Alt text",
                title = "Percentage width image",
                width = DimensionSize.Percent(50),
                height = null,
                inlineContent = emptyList(),
            )

        setContent(extension, imageMarkdown)

        composeTestRule
            .onNodeWithContentDescription("Percentage width image")
            .assertExists()
            .assertWidthIsEqualTo(100.dp) // 50% of 200
            .assertHeightIsEqualTo(50.dp) // scaled proportionally
    }

    @Test
    public fun `image with percentage height scales to percentage of original`() {
        val fakeImageWidth = 200
        val fakeImageHeight = 100
        val fakeImage = ColorImage(Color.Gray.toArgb(), width = fakeImageWidth, height = fakeImageHeight)

        val engine = FakeImageLoaderEngine.Builder().intercept({ it == imageUrl }, fakeImage).build()

        val imageLoaderWithFakeEngine = ImageLoader.Builder(platformContext).components { add(engine) }.build()

        val extension = Coil3ImageRendererExtensionImpl(imageLoaderWithFakeEngine)

        // Specify 50% height - should be 50px (50% of 100)
        // Width should scale proportionally: 50 / 100 * 200 = 100
        val imageMarkdown =
            InlineMarkdown.Image(
                source = imageUrl,
                alt = "Alt text",
                title = "Percentage height image",
                width = null,
                height = DimensionSize.Percent(50),
                inlineContent = emptyList(),
            )

        setContent(extension, imageMarkdown)

        composeTestRule
            .onNodeWithContentDescription("Percentage height image")
            .assertExists()
            .assertWidthIsEqualTo(100.dp) // scaled proportionally
            .assertHeightIsEqualTo(50.dp) // 50% of 100
    }

    @Test
    public fun `image with both percentage dimensions`() {
        val fakeImageWidth = 200
        val fakeImageHeight = 100
        val fakeImage = ColorImage(Color.LightGray.toArgb(), width = fakeImageWidth, height = fakeImageHeight)

        val engine = FakeImageLoaderEngine.Builder().intercept({ it == imageUrl }, fakeImage).build()

        val imageLoaderWithFakeEngine = ImageLoader.Builder(platformContext).components { add(engine) }.build()

        val extension = Coil3ImageRendererExtensionImpl(imageLoaderWithFakeEngine)

        // Specify 25% width and 50% height
        val imageMarkdown =
            InlineMarkdown.Image(
                source = imageUrl,
                alt = "Alt text",
                title = "Both percentage image",
                width = DimensionSize.Percent(25),
                height = DimensionSize.Percent(50),
                inlineContent = emptyList(),
            )

        setContent(extension, imageMarkdown)

        composeTestRule
            .onNodeWithContentDescription("Both percentage image")
            .assertExists()
            .assertWidthIsEqualTo(50.dp) // 25% of 200
            .assertHeightIsEqualTo(50.dp) // 50% of 100
    }

    @Test
    public fun `image with pixel width and percentage height`() {
        val fakeImageWidth = 200
        val fakeImageHeight = 100
        val fakeImage = ColorImage(Color.Red.toArgb(), width = fakeImageWidth, height = fakeImageHeight)

        val engine = FakeImageLoaderEngine.Builder().intercept({ it == imageUrl }, fakeImage).build()

        val imageLoaderWithFakeEngine = ImageLoader.Builder(platformContext).components { add(engine) }.build()

        val extension = Coil3ImageRendererExtensionImpl(imageLoaderWithFakeEngine)

        // Specify 150px width and 50% height (50px = 50% of 100)
        val imageMarkdown =
            InlineMarkdown.Image(
                source = imageUrl,
                alt = "Alt text",
                title = "Mixed pixel width percentage height",
                width = DimensionSize.Pixels(150),
                height = DimensionSize.Percent(50),
                inlineContent = emptyList(),
            )

        setContent(extension, imageMarkdown)

        composeTestRule
            .onNodeWithContentDescription("Mixed pixel width percentage height")
            .assertExists()
            .assertWidthIsEqualTo(150.dp)
            .assertHeightIsEqualTo(50.dp) // 50% of 100
    }

    @Test
    public fun `image with percentage width and pixel height`() {
        val fakeImageWidth = 200
        val fakeImageHeight = 100
        val fakeImage = ColorImage(Color.Blue.toArgb(), width = fakeImageWidth, height = fakeImageHeight)

        val engine = FakeImageLoaderEngine.Builder().intercept({ it == imageUrl }, fakeImage).build()

        val imageLoaderWithFakeEngine = ImageLoader.Builder(platformContext).components { add(engine) }.build()

        val extension = Coil3ImageRendererExtensionImpl(imageLoaderWithFakeEngine)

        // Specify 75% width (150px = 75% of 200) and 80px height
        val imageMarkdown =
            InlineMarkdown.Image(
                source = imageUrl,
                alt = "Alt text",
                title = "Mixed percentage width pixel height",
                width = DimensionSize.Percent(75),
                height = DimensionSize.Pixels(80),
                inlineContent = emptyList(),
            )

        setContent(extension, imageMarkdown)

        composeTestRule
            .onNodeWithContentDescription("Mixed percentage width pixel height")
            .assertExists()
            .assertWidthIsEqualTo(150.dp) // 75% of 200
            .assertHeightIsEqualTo(80.dp)
    }

    @Test
    public fun `image with both dimensions specified but different aspect ratio - stretched`() {
        // Original image is 200x100 (2:1 aspect ratio)
        val fakeImageWidth = 200
        val fakeImageHeight = 100
        val fakeImage = ColorImage(Color.Green.toArgb(), width = fakeImageWidth, height = fakeImageHeight)

        val engine = FakeImageLoaderEngine.Builder().intercept({ it == imageUrl }, fakeImage).build()

        val imageLoaderWithFakeEngine = ImageLoader.Builder(platformContext).components { add(engine) }.build()

        val extension = Coil3ImageRendererExtensionImpl(imageLoaderWithFakeEngine)

        // Specify 100x100 (1:1 aspect ratio) - image will be stretched/squished
        val imageMarkdown =
            InlineMarkdown.Image(
                source = imageUrl,
                alt = "Alt text",
                title = "Stretched square image",
                width = DimensionSize.Pixels(100),
                height = DimensionSize.Pixels(100),
                inlineContent = emptyList(),
            )

        setContent(extension, imageMarkdown)

        // Both dimensions should be exactly as specified, even though aspect ratio differs
        composeTestRule
            .onNodeWithContentDescription("Stretched square image")
            .assertExists()
            .assertWidthIsEqualTo(100.dp)
            .assertHeightIsEqualTo(100.dp)
    }

    @Test
    public fun `image with both dimensions specified - taller than original aspect ratio`() {
        // Original image is 200x100 (2:1 aspect ratio)
        val fakeImageWidth = 200
        val fakeImageHeight = 100
        val fakeImage = ColorImage(Color.Yellow.toArgb(), width = fakeImageWidth, height = fakeImageHeight)

        val engine = FakeImageLoaderEngine.Builder().intercept({ it == imageUrl }, fakeImage).build()

        val imageLoaderWithFakeEngine = ImageLoader.Builder(platformContext).components { add(engine) }.build()

        val extension = Coil3ImageRendererExtensionImpl(imageLoaderWithFakeEngine)

        // Specify 50x200 (1:4 aspect ratio) - very different from original 2:1
        val imageMarkdown =
            InlineMarkdown.Image(
                source = imageUrl,
                alt = "Alt text",
                title = "Tall stretched image",
                width = DimensionSize.Pixels(50),
                height = DimensionSize.Pixels(200),
                inlineContent = emptyList(),
            )

        setContent(extension, imageMarkdown)

        composeTestRule
            .onNodeWithContentDescription("Tall stretched image")
            .assertExists()
            .assertWidthIsEqualTo(50.dp)
            .assertHeightIsEqualTo(200.dp)
    }

    @Test
    public fun `image with both dimensions specified - wider than original aspect ratio`() {
        // Original image is 200x100 (2:1 aspect ratio)
        val fakeImageWidth = 200
        val fakeImageHeight = 100
        val fakeImage = ColorImage(Color.Magenta.toArgb(), width = fakeImageWidth, height = fakeImageHeight)

        val engine = FakeImageLoaderEngine.Builder().intercept({ it == imageUrl }, fakeImage).build()

        val imageLoaderWithFakeEngine = ImageLoader.Builder(platformContext).components { add(engine) }.build()

        val extension = Coil3ImageRendererExtensionImpl(imageLoaderWithFakeEngine)

        // Specify 300x50 (6:1 aspect ratio) - wider than original 2:1
        val imageMarkdown =
            InlineMarkdown.Image(
                source = imageUrl,
                alt = "Alt text",
                title = "Wide stretched image",
                width = DimensionSize.Pixels(300),
                height = DimensionSize.Pixels(50),
                inlineContent = emptyList(),
            )

        setContent(extension, imageMarkdown)

        composeTestRule
            .onNodeWithContentDescription("Wide stretched image")
            .assertExists()
            .assertWidthIsEqualTo(300.dp)
            .assertHeightIsEqualTo(50.dp)
    }

    @Test
    public fun `image with both percentage dimensions - different aspect ratio`() {
        // Original image is 200x100 (2:1 aspect ratio)
        val fakeImageWidth = 200
        val fakeImageHeight = 100
        val fakeImage = ColorImage(Color.Cyan.toArgb(), width = fakeImageWidth, height = fakeImageHeight)

        val engine = FakeImageLoaderEngine.Builder().intercept({ it == imageUrl }, fakeImage).build()

        val imageLoaderWithFakeEngine = ImageLoader.Builder(platformContext).components { add(engine) }.build()

        val extension = Coil3ImageRendererExtensionImpl(imageLoaderWithFakeEngine)

        // Specify 50% width (100px) and 100% height (100px) - becomes 1:1 aspect ratio
        val imageMarkdown =
            InlineMarkdown.Image(
                source = imageUrl,
                alt = "Alt text",
                title = "Percentage different aspect",
                width = DimensionSize.Percent(50),
                height = DimensionSize.Percent(100),
                inlineContent = emptyList(),
            )

        setContent(extension, imageMarkdown)

        composeTestRule
            .onNodeWithContentDescription("Percentage different aspect")
            .assertExists()
            .assertWidthIsEqualTo(100.dp) // 50% of 200
            .assertHeightIsEqualTo(100.dp) // 100% of 100
    }

    private fun setContent(extension: Coil3ImageRendererExtensionImpl, image: InlineMarkdown.Image) {
        composeTestRule.setContent {
            val inlineContent = buildMap { extension.renderImageContent(image)?.let { put("inlineTextContent", it) } }
            val annotatedString = buildAnnotatedString {
                append("Rendered inline text image: ")
                appendInlineContent("inlineTextContent", "[rendered image]")
            }
            BasicText(text = annotatedString, inlineContent = inlineContent)
        }
    }
}
