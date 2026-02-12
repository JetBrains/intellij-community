// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.markdown.extensions.images

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.markdown.InlineMarkdown
import org.jetbrains.jewel.markdown.MarkdownBlock.Paragraph
import org.jetbrains.jewel.markdown.createMarkdownStyling
import org.jetbrains.jewel.markdown.createThemeDefinition
import org.jetbrains.jewel.markdown.rendering.DefaultInlineMarkdownRenderer
import org.jetbrains.jewel.markdown.rendering.DefaultMarkdownBlockRenderer
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoilApi::class, ExperimentalJewelApi::class)
public class Coil3ImageRendererExtensionImplTest {
    @get:Rule public val composeTestRule: ComposeContentTestRule = createComposeRule()

    private val platformContext: PlatformContext = PlatformContext.INSTANCE
    private val loadingImageUrl = "https://example.com/image.png"
    private val failingImageUrl = "https://example.com/nonexistent.png"
    private val loadingImageUrl2 = "https://example.com/image2.png"

    @Test
    public fun `image renders with correct size on success`() {
        val fakeImageWidth = 150
        val fakeImageHeight = 100
        val fakeImage = ColorImage(Color.Red.toArgb(), width = fakeImageWidth, height = fakeImageHeight)

        val engine = FakeImageLoaderEngine.Builder().intercept({ it == loadingImageUrl }, fakeImage).build()

        val imageLoaderWithFakeEngine = ImageLoader.Builder(platformContext).components { add(engine) }.build()

        val imageMarkdown =
            InlineMarkdown.Image(source = loadingImageUrl, alt = "Alt text", title = "Image loaded successfully")

        setContent(imageLoaderWithFakeEngine, imageMarkdown)

        composeTestRule
            .onNodeWithContentDescription("Image loaded successfully")
            .assertExists()
            .assertWidthIsEqualTo(fakeImageWidth.dp)
            .assertHeightIsEqualTo(fakeImageHeight.dp)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    public fun `loading indicator shows during loading state then image appears on success`() {
        val testDispatcher = StandardTestDispatcher()

        val fakeImageWidth = 50
        val fakeImageHeight = 50
        val fakeImage = ColorImage(Color.Red.toArgb(), width = fakeImageWidth, height = fakeImageHeight)
        val engine =
            FakeImageLoaderEngine.Builder()
                .intercept(
                    predicate = { it == loadingImageUrl },
                    interceptor = {
                        delay(500) // simulating network delay

                        SuccessResult(fakeImage, it.request, DataSource.MEMORY)
                    },
                )
                .build()

        val imageLoader =
            ImageLoader.Builder(platformContext).components { add(engine) }.coroutineContext(testDispatcher).build()

        val imageMarkdown = InlineMarkdown.Image(source = loadingImageUrl, alt = "Alt text", title = "A loading image")

        setContent(imageLoader, imageMarkdown)

        // fast forwarding coil's internal dispatcher
        testDispatcher.scheduler.advanceTimeBy(250)
        testDispatcher.scheduler.runCurrent()

        // The actual image with its content description doesn't exist yet
        composeTestRule.onNodeWithContentDescription("A loading image").assertDoesNotExist()

        // Verify loading indicator is shown
        composeTestRule
            .onNodeWithContentDescription(Coil3ImageRendererExtensionImpl.LOADING_INDICATOR_DESCRIPTION)
            .assertExists()

        // fast forwarding coil's internal dispatcher
        testDispatcher.scheduler.advanceTimeBy(251)
        testDispatcher.scheduler.runCurrent()

        // After loading completes, image should appear and loading indicator should disappear
        composeTestRule.onNodeWithContentDescription("A loading image").assertExists()
        composeTestRule
            .onNodeWithContentDescription(Coil3ImageRendererExtensionImpl.LOADING_INDICATOR_DESCRIPTION)
            .assertDoesNotExist()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    public fun `loading indicator shows during loading state then disappears on failure`() {
        val testDispatcher = StandardTestDispatcher()

        val engine =
            FakeImageLoaderEngine.Builder()
                .intercept(
                    predicate = { it == failingImageUrl },
                    interceptor = {
                        delay(500) // simulating network delay

                        ErrorResult(null, it.request, IllegalStateException("Not found"))
                    },
                )
                .build()

        val imageLoader =
            ImageLoader.Builder(platformContext).components { add(engine) }.coroutineContext(testDispatcher).build()
        val altText = "Missing image"
        val failingImageMarkdown = InlineMarkdown.Image(source = failingImageUrl, alt = altText, title = null)

        setContent(imageLoader, failingImageMarkdown)

        // fast forwarding coil's internal dispatcher
        testDispatcher.scheduler.advanceTimeBy(250)
        testDispatcher.scheduler.runCurrent()

        // The actual image with its content description doesn't exist yet
        composeTestRule.onNodeWithContentDescription(altText).assertDoesNotExist()

        // Verify loading indicator is shown
        composeTestRule
            .onNodeWithContentDescription(Coil3ImageRendererExtensionImpl.LOADING_INDICATOR_DESCRIPTION)
            .assertExists()

        // fast forwarding coil's internal dispatcher
        testDispatcher.scheduler.advanceTimeBy(251)
        testDispatcher.scheduler.runCurrent()

        composeTestRule
            .onNodeWithContentDescription(Coil3ImageRendererExtensionImpl.LOADING_INDICATOR_DESCRIPTION)
            .assertDoesNotExist()

        // After loading completes, image should appear and loading indicator should disappear
        assertFailedLinkExists(altText)
    }

    @Test
    public fun `failed image is rendered as link with alt text`() {
        val engine =
            FakeImageLoaderEngine.Builder()
                .intercept(
                    predicate = { it == failingImageUrl },
                    interceptor = { ErrorResult(null, it.request, IllegalStateException("Not found")) },
                )
                .build()

        val imageLoader = ImageLoader.Builder(platformContext).components { add(engine) }.build()
        val altText = "Missing image"
        val failingImageMarkdown = InlineMarkdown.Image(source = failingImageUrl, alt = altText, title = null)

        setContent(imageLoader, failingImageMarkdown)

        // Image should not be rendered
        composeTestRule
            .onNodeWithContentDescription(Coil3ImageRendererExtensionImpl.LOADING_INDICATOR_DESCRIPTION)
            .assertDoesNotExist()

        // Failed image should be rendered as a link with alt text
        assertFailedLinkExists(altText)
    }

    @Test
    public fun `single image wider than container is downscaled to fit available width`() {
        val fakeImageWidth = 300
        val fakeImageHeight = 200
        val containerWidth = 100
        val fakeImage = ColorImage(Color.Blue.toArgb(), width = fakeImageWidth, height = fakeImageHeight)

        val engine = FakeImageLoaderEngine.Builder().intercept({ it == loadingImageUrl }, fakeImage).build()
        val imageLoader = ImageLoader.Builder(platformContext).components { add(engine) }.build()

        val paragraph =
            Paragraph(InlineMarkdown.Image(source = loadingImageUrl, alt = "Alt text", title = "HUGE image"))

        setConstrainedContentWithParagraph(imageLoader, paragraph, containerWidth)

        // The image should be scaled down to fit the container width
        // Scale factor = 100 / 300 = 0.333...
        // Expected height = 200 * 0.333... = 66.67
        val expectedHeight = (fakeImageHeight * containerWidth / fakeImageWidth)
        composeTestRule
            .onNodeWithContentDescription("HUGE image")
            .assertExists()
            .assertWidthIsEqualTo(containerWidth.dp)
            .assertHeightIsEqualTo(expectedHeight.dp)
    }

    @Test
    public fun `single image narrower than container is not upscaled`() {
        val fakeImageWidth = 50
        val fakeImageHeight = 30
        val containerWidth = 200
        val fakeImage = ColorImage(Color.Green.toArgb(), width = fakeImageWidth, height = fakeImageHeight)

        val engine = FakeImageLoaderEngine.Builder().intercept({ it == loadingImageUrl }, fakeImage).build()
        val imageLoader = ImageLoader.Builder(platformContext).components { add(engine) }.build()

        val paragraph =
            Paragraph(InlineMarkdown.Image(source = loadingImageUrl, alt = "Alt text", title = "smol image"))

        setConstrainedContentWithParagraph(imageLoader, paragraph, containerWidth)

        // The image should remain at its original size, not upscaled
        composeTestRule
            .onNodeWithContentDescription("smol image")
            .assertExists()
            .assertWidthIsEqualTo(fakeImageWidth.dp)
            .assertHeightIsEqualTo(fakeImageHeight.dp)
    }

    @Test
    public fun `text with leading image is downscaled when container is narrow`() {
        val fakeImageWidth = 200
        val fakeImageHeight = 100
        val containerWidth = 100
        val fakeImage = ColorImage(Color.Red.toArgb(), width = fakeImageWidth, height = fakeImageHeight)

        val engine = FakeImageLoaderEngine.Builder().intercept({ it == loadingImageUrl }, fakeImage).build()
        val imageLoader = ImageLoader.Builder(platformContext).components { add(engine) }.build()

        val paragraph =
            Paragraph(
                InlineMarkdown.Image(source = loadingImageUrl, alt = "Alt text", title = "HUGE image"),
                InlineMarkdown.Text(" and then some!"),
            )

        setConstrainedContentWithParagraph(imageLoader, paragraph, containerWidth)

        val expectedHeight = (fakeImageHeight * containerWidth / fakeImageWidth)
        composeTestRule
            .onNodeWithContentDescription("HUGE image")
            .assertExists()
            .assertWidthIsEqualTo(containerWidth.dp)
            .assertHeightIsEqualTo(expectedHeight.dp)
    }

    @Test
    public fun `text with trailing image is downscaled when container is narrow`() {
        val fakeImageWidth = 200
        val fakeImageHeight = 100
        val containerWidth = 100
        val fakeImage = ColorImage(Color.Blue.toArgb(), width = fakeImageWidth, height = fakeImageHeight)

        val engine = FakeImageLoaderEngine.Builder().intercept({ it == loadingImageUrl }, fakeImage).build()
        val imageLoader = ImageLoader.Builder(platformContext).components { add(engine) }.build()

        val paragraph =
            Paragraph(
                InlineMarkdown.Text("Behold the "),
                InlineMarkdown.Image(source = loadingImageUrl, alt = "Alt text", title = "HUGE image"),
            )

        setConstrainedContentWithParagraph(imageLoader, paragraph, containerWidth)

        val expectedHeight = (fakeImageHeight * containerWidth / fakeImageWidth)
        composeTestRule
            .onNodeWithContentDescription("HUGE image")
            .assertExists()
            .assertWidthIsEqualTo(containerWidth.dp)
            .assertHeightIsEqualTo(expectedHeight.dp)
    }

    @Test
    public fun `image between text is downscaled when container is narrow`() {
        val fakeImageWidth = 200
        val fakeImageHeight = 100
        val containerWidth = 100
        val fakeImage = ColorImage(Color.Green.toArgb(), width = fakeImageWidth, height = fakeImageHeight)

        val engine = FakeImageLoaderEngine.Builder().intercept({ it == loadingImageUrl }, fakeImage).build()
        val imageLoader = ImageLoader.Builder(platformContext).components { add(engine) }.build()

        val paragraph =
            Paragraph(
                InlineMarkdown.Text("Behold, the "),
                InlineMarkdown.Image(source = loadingImageUrl, alt = "Alt text", title = "HUGE image"),
                InlineMarkdown.Text("!!!"),
            )

        setConstrainedContentWithParagraph(imageLoader, paragraph, containerWidth)

        val expectedHeight = (fakeImageHeight * containerWidth / fakeImageWidth)
        composeTestRule
            .onNodeWithContentDescription("HUGE image")
            .assertExists()
            .assertWidthIsEqualTo(containerWidth.dp)
            .assertHeightIsEqualTo(expectedHeight.dp)
    }

    @Test
    public fun `two images in paragraph are both downscaled when container is narrow`() {
        val fakeImage1Width = 200
        val fakeImage1Height = 100
        val fakeImage2Width = 300
        val fakeImage2Height = 150
        val containerWidth = 100

        val fakeImage1 = ColorImage(Color.Red.toArgb(), width = fakeImage1Width, height = fakeImage1Height)
        val fakeImage2 = ColorImage(Color.Blue.toArgb(), width = fakeImage2Width, height = fakeImage2Height)

        val engine =
            FakeImageLoaderEngine.Builder()
                .intercept({ it == loadingImageUrl }, fakeImage1)
                .intercept({ it == loadingImageUrl2 }, fakeImage2)
                .build()
        val imageLoader = ImageLoader.Builder(platformContext).components { add(engine) }.build()

        val paragraph =
            Paragraph(
                InlineMarkdown.Image(source = loadingImageUrl, alt = "Alt text 1", title = "First image"),
                InlineMarkdown.Text(" text between "),
                InlineMarkdown.Image(source = loadingImageUrl2, alt = "Alt text 2", title = "Second image"),
            )

        setConstrainedContentWithParagraph(imageLoader, paragraph, containerWidth)

        val expectedHeight1 = (fakeImage1Height * containerWidth / fakeImage1Width)
        val expectedHeight2 = (fakeImage2Height * containerWidth / fakeImage2Width)

        composeTestRule
            .onNodeWithContentDescription("First image")
            .assertExists()
            .assertWidthIsEqualTo(containerWidth.dp)
            .assertHeightIsEqualTo(expectedHeight1.dp)

        composeTestRule
            .onNodeWithContentDescription("Second image")
            .assertExists()
            .assertWidthIsEqualTo(containerWidth.dp)
            .assertHeightIsEqualTo(expectedHeight2.dp)
    }

    @Test
    public fun `failed image without alt text shows URL as link text`() {
        val engine =
            FakeImageLoaderEngine.Builder()
                .intercept(
                    predicate = { it == failingImageUrl },
                    interceptor = { ErrorResult(null, it.request, IllegalStateException("Not found")) },
                )
                .build()

        val imageLoader = ImageLoader.Builder(platformContext).components { add(engine) }.build()
        val failingImageMarkdown = InlineMarkdown.Image(source = failingImageUrl, alt = "", title = null)

        setContent(imageLoader, failingImageMarkdown)

        // Failed image without alt text should show URL as link text
        assertFailedLinkExists(failingImageUrl)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    public fun `previously failed image shows link instead of loading indicator on retry`() {
        val testDispatcher = StandardTestDispatcher()

        val engine =
            FakeImageLoaderEngine.Builder()
                .intercept(
                    predicate = { it == failingImageUrl },
                    interceptor = {
                        delay(100)
                        ErrorResult(null, it.request, IllegalStateException("Failed"))
                    },
                )
                .build()

        val imageLoader =
            ImageLoader.Builder(platformContext).components { add(engine) }.coroutineContext(testDispatcher).build()
        val altText = "Failing image"
        val failingImageMarkdown = InlineMarkdown.Image(source = failingImageUrl, alt = altText, title = null)

        setContent(imageLoader, failingImageMarkdown)

        // Initially loading indicator should be shown
        composeTestRule
            .onNodeWithContentDescription(Coil3ImageRendererExtensionImpl.LOADING_INDICATOR_DESCRIPTION)
            .assertExists()

        // Complete first request - should fail and show link
        testDispatcher.scheduler.advanceTimeBy(101)
        testDispatcher.scheduler.runCurrent()

        // Wait for link to appear, then verify loading indicator is gone
        assertFailedLinkExists(altText)
        composeTestRule
            .onNodeWithContentDescription(Coil3ImageRendererExtensionImpl.LOADING_INDICATOR_DESCRIPTION)
            .assertDoesNotExist()
    }

    private fun assertFailedLinkExists(altText: String) {
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodes(hasText(altText, substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun setContent(imageLoader: ImageLoader, vararg images: InlineMarkdown.Image) {
        val paragraph = Paragraph(*images)
        setConstrainedContentWithParagraph(imageLoader, paragraph, containerWidthDp = Int.MAX_VALUE)
    }

    private fun setConstrainedContentWithParagraph(
        imageLoader: ImageLoader,
        paragraph: Paragraph,
        containerWidthDp: Int,
    ) {
        composeTestRule.setContent {
            JewelTheme(createThemeDefinition()) {
                val imageExtension = Coil3ImageRendererExtension(imageLoader)
                val markdownStyling = createMarkdownStyling()
                val blockRenderer =
                    DefaultMarkdownBlockRenderer(
                        rootStyling = markdownStyling,
                        rendererExtensions = listOf(imageExtension),
                        inlineRenderer = DefaultInlineMarkdownRenderer(listOf(imageExtension)),
                    )
                Box(modifier = Modifier.width(containerWidthDp.dp)) {
                    blockRenderer.RenderParagraph(
                        block = paragraph,
                        styling = markdownStyling.paragraph,
                        enabled = true,
                        onUrlClick = {},
                        modifier = Modifier,
                    )
                }
            }
        }
    }
}
