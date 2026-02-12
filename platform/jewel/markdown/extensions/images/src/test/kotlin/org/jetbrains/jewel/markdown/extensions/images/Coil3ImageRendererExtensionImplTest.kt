// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.markdown.extensions.images

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.ColorImage
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.annotation.ExperimentalCoilApi
import coil3.decode.DataSource
import coil3.request.ErrorResult
import coil3.request.SuccessResult
import coil3.test.FakeImageLoaderEngine
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.markdown.DimensionSize
import org.jetbrains.jewel.markdown.InlineMarkdown
import org.jetbrains.jewel.markdown.MarkdownBlock.Paragraph
import org.jetbrains.jewel.markdown.extensions.ImageRenderResult
import org.jetbrains.jewel.markdown.extensions.ImageRendererExtension
import org.jetbrains.jewel.markdown.extensions.MarkdownRendererExtension
import org.jetbrains.jewel.markdown.rendering.DOWNSCALED_INLINE_CONTENT_TAG
import org.jetbrains.jewel.markdown.rendering.DefaultInlineMarkdownRenderer
import org.jetbrains.jewel.markdown.rendering.DefaultMarkdownBlockRenderer
import org.jetbrains.jewel.markdown.rendering.InlinesStyling
import org.jetbrains.jewel.markdown.rendering.MarkdownStyling
import org.jetbrains.jewel.markdown.testing.createMarkdownTestStyling
import org.jetbrains.jewel.markdown.testing.createMarkdownTestThemeDefinition
import org.jetbrains.jewel.ui.component.Text as JewelText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val PARAGRAPH_WRAPPER_TAG = "paragraph-wrapper"

@OptIn(ExperimentalCoilApi::class, ExperimentalJewelApi::class)
@Suppress("LargeClass")
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
                        delay(500.milliseconds) // simulating network delay

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
                        delay(500.milliseconds) // simulating network delay

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
            .assertWidthIsEqualTo(1.dp)
            .assertHeightIsEqualTo(1.dp)

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
    public fun `image renders with specified pixel width and height`() {
        val fakeImageWidth = 200
        val fakeImageHeight = 100
        val fakeImage = ColorImage(Color.Blue.toArgb(), width = fakeImageWidth, height = fakeImageHeight)

        val engine = FakeImageLoaderEngine.Builder().intercept({ it == loadingImageUrl }, fakeImage).build()

        // Specify different dimensions than the actual image
        val specifiedWidth = 100
        val specifiedHeight = 50
        val imageMarkdown =
            InlineMarkdown.Image(
                source = loadingImageUrl,
                alt = "Alt text",
                title = "Sized image",
                width = DimensionSize.Pixels(specifiedWidth),
                height = DimensionSize.Pixels(specifiedHeight),
                inlineContent = emptyList(),
            )
        val imageLoader = ImageLoader.Builder(platformContext).components { add(engine) }.build()

        setContent(imageLoader, imageMarkdown)

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

        val engine = FakeImageLoaderEngine.Builder().intercept({ it == loadingImageUrl }, fakeImage).build()

        // Specify only width - height should be scaled proportionally
        val specifiedWidth = 100
        // Expected height: 100 / 200 * 100 = 50
        val expectedHeight = 50
        val imageMarkdown =
            InlineMarkdown.Image(
                source = loadingImageUrl,
                alt = "Alt text",
                title = "Width only image",
                width = DimensionSize.Pixels(specifiedWidth),
                height = null,
                inlineContent = emptyList(),
            )
        val imageLoader = ImageLoader.Builder(platformContext).components { add(engine) }.build()

        setContent(imageLoader, imageMarkdown)

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

        val engine = FakeImageLoaderEngine.Builder().intercept({ it == loadingImageUrl }, fakeImage).build()

        // Specify only height - width should be scaled proportionally
        val specifiedHeight = 50
        // Expected width: 50 / 100 * 200 = 100
        val expectedWidth = 100
        val imageMarkdown =
            InlineMarkdown.Image(
                source = loadingImageUrl,
                alt = "Alt text",
                title = "Height only image",
                width = null,
                height = DimensionSize.Pixels(specifiedHeight),
                inlineContent = emptyList(),
            )
        val imageLoader = ImageLoader.Builder(platformContext).components { add(engine) }.build()

        setContent(imageLoader, imageMarkdown)

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
                    predicate = { it == loadingImageUrl },
                    interceptor = {
                        delay(500.milliseconds) // simulating network delay

                        SuccessResult(fakeImage, it.request, DataSource.MEMORY)
                    },
                )
                .build()

        val imageLoader =
            ImageLoader.Builder(platformContext).components { add(engine) }.coroutineContext(testDispatcher).build()

        val specifiedWidth = 150
        val specifiedHeight = 75
        val imageMarkdown =
            InlineMarkdown.Image(
                source = loadingImageUrl,
                alt = "Alt text",
                title = "Loading sized image",
                width = DimensionSize.Pixels(specifiedWidth),
                height = DimensionSize.Pixels(specifiedHeight),
                inlineContent = emptyList(),
            )

        setContent(imageLoader, imageMarkdown)

        // During loading, placeholder should have the specified dimensions
        composeTestRule
            .onNodeWithContentDescription("Loading sized image")
            .assertExists()
            .assertWidthIsEqualTo(specifiedWidth.dp)
            .assertHeightIsEqualTo(specifiedHeight.dp)

        // Fast-forward to complete loading
        testDispatcher.scheduler.advanceTimeBy(501)
        testDispatcher.scheduler.runCurrent()

        // After loading, should still have the specified dimensions
        composeTestRule
            .onNodeWithContentDescription("Loading sized image")
            .assertWidthIsEqualTo(specifiedWidth.dp)
            .assertHeightIsEqualTo(specifiedHeight.dp)
    }

    @Test
    public fun `image with both dimensions specified but different aspect ratio - stretched`() {
        // Original image is 200x100 (2:1 aspect ratio)
        val fakeImageWidth = 200
        val fakeImageHeight = 100
        val fakeImage = ColorImage(Color.Green.toArgb(), width = fakeImageWidth, height = fakeImageHeight)

        val engine = FakeImageLoaderEngine.Builder().intercept({ it == loadingImageUrl }, fakeImage).build()

        val imageLoaderWithFakeEngine = ImageLoader.Builder(platformContext).components { add(engine) }.build()

        // Specify 100x100 (1:1 aspect ratio) - image will be stretched/squished
        val imageMarkdown =
            InlineMarkdown.Image(
                source = loadingImageUrl,
                alt = "Alt text",
                title = "Stretched square image",
                width = DimensionSize.Pixels(100),
                height = DimensionSize.Pixels(100),
                inlineContent = emptyList(),
            )

        setContent(imageLoaderWithFakeEngine, imageMarkdown)

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

        val engine = FakeImageLoaderEngine.Builder().intercept({ it == loadingImageUrl }, fakeImage).build()

        val imageLoaderWithFakeEngine = ImageLoader.Builder(platformContext).components { add(engine) }.build()

        // Specify 50x200 (1:4 aspect ratio) - very different from original 2:1
        val imageMarkdown =
            InlineMarkdown.Image(
                source = loadingImageUrl,
                alt = "Alt text",
                title = "Tall stretched image",
                width = DimensionSize.Pixels(50),
                height = DimensionSize.Pixels(200),
                inlineContent = emptyList(),
            )

        setContent(imageLoaderWithFakeEngine, imageMarkdown)

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

        val engine = FakeImageLoaderEngine.Builder().intercept({ it == loadingImageUrl }, fakeImage).build()

        val imageLoaderWithFakeEngine = ImageLoader.Builder(platformContext).components { add(engine) }.build()

        // Specify 300x50 (6:1 aspect ratio) - wider than original 2:1
        val imageMarkdown =
            InlineMarkdown.Image(
                source = loadingImageUrl,
                alt = "Alt text",
                title = "Wide stretched image",
                width = DimensionSize.Pixels(300),
                height = DimensionSize.Pixels(50),
                inlineContent = emptyList(),
            )

        setContent(imageLoaderWithFakeEngine, imageMarkdown)

        composeTestRule
            .onNodeWithContentDescription("Wide stretched image")
            .assertExists()
            .assertWidthIsEqualTo(300.dp)
            .assertHeightIsEqualTo(50.dp)
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

        composeTestRule.onNodeWithTag(DOWNSCALED_INLINE_CONTENT_TAG).assertExists()
    }

    @Test
    public fun `paragraph height matches scaled image height, not original placeholder size`() {
        val fakeImageWidth = 300
        val fakeImageHeight = 200
        val containerWidth = 100
        val fakeImage = ColorImage(Color.Blue.toArgb(), width = fakeImageWidth, height = fakeImageHeight)

        val engine = FakeImageLoaderEngine.Builder().intercept({ it == loadingImageUrl }, fakeImage).build()
        val imageLoader = ImageLoader.Builder(platformContext).components { add(engine) }.build()

        val paragraph =
            Paragraph(InlineMarkdown.Image(source = loadingImageUrl, alt = "Alt text", title = "HUGE image"))

        setConstrainedContentWithParagraph(imageLoader, paragraph, containerWidth)

        // The outer Layout must use scaled intrinsics, not original image dimensions.
        // A regression would show ~200dp here instead of the scaled ~67dp.
        val scaledHeight = ceil(fakeImageHeight.toFloat() * containerWidth / fakeImageWidth).toInt()
        composeTestRule.onNodeWithTag(PARAGRAPH_WRAPPER_TAG).assertHeightIsEqualTo(scaledHeight.dp)
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

        composeTestRule.onNodeWithTag(DOWNSCALED_INLINE_CONTENT_TAG).assertExists()
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

        composeTestRule.onNodeWithTag(DOWNSCALED_INLINE_CONTENT_TAG).assertExists()
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

        composeTestRule.onNodeWithTag(DOWNSCALED_INLINE_CONTENT_TAG).assertExists()
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

        composeTestRule.onNodeWithTag(DOWNSCALED_INLINE_CONTENT_TAG).assertExists()
    }

    @Test
    public fun `images don't affect the min intrinsic width of paragraph`() {
        val fakeImageWidth = 200
        val fakeImageHeight = 100
        val fakeImage = ColorImage(Color.Yellow.toArgb(), width = fakeImageWidth, height = fakeImageHeight)

        val engine = FakeImageLoaderEngine.Builder().intercept({ it == loadingImageUrl }, fakeImage).build()
        val imageLoader = ImageLoader.Builder(platformContext).components { add(engine) }.build()
        val paragraph =
            Paragraph(
                InlineMarkdown.Text("Prefix "),
                InlineMarkdown.Image(source = loadingImageUrl, alt = "Alt text", title = "Intrinsic image"),
                InlineMarkdown.Text("followedbyanincrediblelonguninterrupredsequenceofwords"),
            )

        var paragraphMinWidth = -1
        var textMinWidth = -1

        composeTestRule.setContent {
            JewelTheme(createMarkdownTestThemeDefinition()) {
                val imageExtension = Coil3ImageRendererExtension(imageLoader)
                val markdownStyling = createMarkdownTestStyling()
                val blockRenderer =
                    DefaultMarkdownBlockRenderer(
                        rootStyling = markdownStyling,
                        rendererExtensions = listOf(imageExtension),
                        inlineRenderer = DefaultInlineMarkdownRenderer(listOf(imageExtension)),
                    )

                Column {
                    Box(
                        modifier =
                            Modifier.width(IntrinsicSize.Min).onGloballyPositioned { paragraphMinWidth = it.size.width }
                    ) {
                        blockRenderer.RenderParagraph(
                            block = paragraph,
                            styling = markdownStyling.paragraph,
                            enabled = true,
                            onUrlClick = {},
                            modifier = Modifier,
                        )
                    }

                    Box(
                        modifier =
                            Modifier.width(IntrinsicSize.Min).onGloballyPositioned { textMinWidth = it.size.width }
                    ) {
                        JewelText(
                            text =
                                buildAnnotatedString {
                                    append("Prefix followedbyanincrediblelonguninterrupredsequenceofwords")
                                },
                            style = markdownStyling.paragraph.inlinesStyling.textStyle,
                        )
                    }
                }
            }
        }

        composeTestRule.runOnIdle {
            assertTrue("Expected the paragraph min intrinsic width to be positive.", paragraphMinWidth > 0)
            assertEquals(textMinWidth, paragraphMinWidth)
        }
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

        composeTestRule.onNodeWithTag(DOWNSCALED_INLINE_CONTENT_TAG).assertExists()
    }

    @Test
    public fun `text only paragraph uses fast path without scaling logic`() {
        val containerWidth = 100

        // Create an empty engine since we don't expect any image loading
        val engine = FakeImageLoaderEngine.Builder().build()
        val imageLoader = ImageLoader.Builder(platformContext).components { add(engine) }.build()

        val paragraph = Paragraph(InlineMarkdown.Text("Just plain text without any images"))

        setConstrainedContentWithParagraph(imageLoader, paragraph, containerWidth)

        composeTestRule.onNodeWithTag(DOWNSCALED_INLINE_CONTENT_TAG).assertDoesNotExist()
    }

    @Test
    public fun `zero width image does not render and composition goes fast path`() {
        // Tests that zero-width images trigger the fast path: placeholderWidths.sum() <= 0.01f
        val fakeImageWidth = 0
        val fakeImageHeight = 100
        val containerWidth = 50
        val fakeImage = ColorImage(Color.Cyan.toArgb(), width = fakeImageWidth, height = fakeImageHeight)

        val engine = FakeImageLoaderEngine.Builder().intercept({ it == loadingImageUrl }, fakeImage).build()
        val imageLoader = ImageLoader.Builder(platformContext).components { add(engine) }.build()

        val paragraph =
            Paragraph(
                InlineMarkdown.Text("Zero width: "),
                InlineMarkdown.Image(source = loadingImageUrl, alt = "Alt text", title = "Zero width image"),
            )

        setConstrainedContentWithParagraph(imageLoader, paragraph, containerWidth)

        // Fast path: SubcomposeLayout is NOT used for zero-width images
        composeTestRule.onNodeWithTag(DOWNSCALED_INLINE_CONTENT_TAG).assertDoesNotExist()
    }

    @Test
    public fun `first image fits but second image needs downscaling`() {
        val fakeImage1Width = 80 // Fits in container
        val fakeImage1Height = 60
        val fakeImage2Width = 200 // Needs downscaling
        val fakeImage2Height = 100
        val containerWidth = 100

        val fakeImage1 = ColorImage(Color.Green.toArgb(), width = fakeImage1Width, height = fakeImage1Height)
        val fakeImage2 = ColorImage(Color.Red.toArgb(), width = fakeImage2Width, height = fakeImage2Height)

        val engine =
            FakeImageLoaderEngine.Builder()
                .intercept({ it == loadingImageUrl }, fakeImage1)
                .intercept({ it == loadingImageUrl2 }, fakeImage2)
                .build()
        val imageLoader = ImageLoader.Builder(platformContext).components { add(engine) }.build()

        val paragraph =
            Paragraph(
                InlineMarkdown.Image(source = loadingImageUrl, alt = "Alt text 1", title = "smol image"),
                InlineMarkdown.Text(" and "),
                InlineMarkdown.Image(source = loadingImageUrl2, alt = "Alt text 2", title = "beeg image"),
            )

        setConstrainedContentWithParagraph(imageLoader, paragraph, containerWidth)

        // First image should keep original size (fits in container)
        composeTestRule
            .onNodeWithContentDescription("smol image")
            .assertExists()
            .assertWidthIsEqualTo(fakeImage1Width.dp)
            .assertHeightIsEqualTo(fakeImage1Height.dp)

        // Second image should be downscaled
        val downscaledImage2Height = (fakeImage2Height * containerWidth / fakeImage2Width)
        composeTestRule
            .onNodeWithContentDescription("beeg image")
            .assertExists()
            .assertWidthIsEqualTo(containerWidth.dp)
            .assertHeightIsEqualTo(downscaledImage2Height.dp)

        composeTestRule.onNodeWithTag(DOWNSCALED_INLINE_CONTENT_TAG).assertExists()
    }

    @Test
    public fun `image barely exceeding container width is scaled without rounding errors`() {
        val fakeImageWidth = 101
        val fakeImageHeight = 100
        val containerWidth = 100
        val fakeImage = ColorImage(Color.Magenta.toArgb(), width = fakeImageWidth, height = fakeImageHeight)

        val engine = FakeImageLoaderEngine.Builder().intercept({ it == loadingImageUrl }, fakeImage).build()
        val imageLoader = ImageLoader.Builder(platformContext).components { add(engine) }.build()

        val paragraph =
            Paragraph(
                InlineMarkdown.Text("Edge case: "),
                InlineMarkdown.Image(source = loadingImageUrl, alt = "Alt text", title = "Barely oversized"),
            )

        setConstrainedContentWithParagraph(imageLoader, paragraph, containerWidth)

        // Scale factor = 100 / 101 ≈ 0.99
        // Expected height = 100 * (100 / 101) ≈ 99
        val expectedHeightRounded = 99
        composeTestRule
            .onNodeWithContentDescription("Barely oversized")
            .assertExists()
            .assertWidthIsEqualTo(containerWidth.dp)
            .assertHeightIsEqualTo(expectedHeightRounded.dp)

        composeTestRule.onNodeWithTag(DOWNSCALED_INLINE_CONTENT_TAG).assertExists()
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
                        delay(100.milliseconds)
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

    @Test
    public fun `same source with different alt text renders each occurrence independently`() {
        val fakeImage = ColorImage(Color.Green.toArgb(), width = 120, height = 80)
        val engine = FakeImageLoaderEngine.Builder().intercept({ it == loadingImageUrl }, fakeImage).build()
        val imageLoader = ImageLoader.Builder(platformContext).components { add(engine) }.build()

        // Same URL, different alt/title: the two occurrences must not collapse onto a single one.
        setContent(
            imageLoader,
            InlineMarkdown.Image(source = loadingImageUrl, alt = "Alt A", title = "Image A"),
            InlineMarkdown.Image(source = loadingImageUrl, alt = "Alt B", title = "Image B"),
        )

        // Rendering the second occurrence must not break the first: both are present.
        composeTestRule.onNodeWithContentDescription("Image A").assertExists()
        composeTestRule.onNodeWithContentDescription("Image B").assertExists()
    }

    @Test
    public fun `same source with different specified widths sizes each occurrence independently`() {
        val fakeImage = ColorImage(Color.Red.toArgb(), width = 200, height = 100)
        val engine = FakeImageLoaderEngine.Builder().intercept({ it == loadingImageUrl }, fakeImage).build()
        val imageLoader = ImageLoader.Builder(platformContext).components { add(engine) }.build()

        // Same URL and alt, different specified width: each occurrence keeps its own size.
        setContent(
            imageLoader,
            InlineMarkdown.Image(
                source = loadingImageUrl,
                alt = "Alt",
                title = "Narrow",
                width = DimensionSize.Pixels(50),
            ),
            InlineMarkdown.Image(
                source = loadingImageUrl,
                alt = "Alt",
                title = "Wide",
                width = DimensionSize.Pixels(99),
            ),
        )

        composeTestRule.onNodeWithContentDescription("Narrow").assertExists().assertWidthIsEqualTo(50.dp)
        composeTestRule.onNodeWithContentDescription("Wide").assertExists().assertWidthIsEqualTo(99.dp)
    }

    @Test
    public fun `identical images share the same fate when loading fails`() {
        val engine =
            FakeImageLoaderEngine.Builder()
                .intercept(
                    predicate = { it == failingImageUrl },
                    interceptor = { ErrorResult(null, it.request, IllegalStateException("Not found")) },
                )
                .build()
        val imageLoader = ImageLoader.Builder(platformContext).components { add(engine) }.build()

        // Two byte-for-byte identical occurrences share a content id, so they coalesce and fail together.
        setContent(
            imageLoader,
            InlineMarkdown.Image(source = failingImageUrl, alt = "Shared alt", title = "Shared image"),
            InlineMarkdown.Image(source = failingImageUrl, alt = "Shared alt", title = "Shared image"),
        )

        // The fallback link appears once loading fails...
        assertFailedLinkExists("Shared alt")
        // ...and neither occurrence rendered as an image: if either had succeeded, its content-description node
        // would exist. Its absence proves both share the failed fate.
        composeTestRule.onNodeWithContentDescription("Shared image").assertDoesNotExist()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    public fun `rapid source changes are debounced so only the settled source is loaded`() {
        val firstImage = ColorImage(Color.Green.toArgb(), width = 40, height = 40)
        val secondImage = ColorImage(Color.Blue.toArgb(), width = 80, height = 80)

        val firstUrlLoads = AtomicInteger(0)
        val secondUrlLoads = AtomicInteger(0)

        val testDispatcher = StandardTestDispatcher()
        val engine =
            FakeImageLoaderEngine.Builder()
                .intercept(
                    predicate = { it == loadingImageUrl },
                    interceptor = {
                        firstUrlLoads.incrementAndGet()
                        SuccessResult(firstImage, it.request, DataSource.MEMORY)
                    },
                )
                .intercept(
                    predicate = { it == loadingImageUrl2 },
                    interceptor = {
                        secondUrlLoads.incrementAndGet()
                        SuccessResult(secondImage, it.request, DataSource.MEMORY)
                    },
                )
                .build()
        val imageLoader =
            ImageLoader.Builder(platformContext).components { add(engine) }.coroutineContext(testDispatcher).build()

        val source = mutableStateOf(loadingImageUrl)

        // Drive the debounce clock manually: the delay lives in a LaunchedEffect, so it advances with the
        // Compose mainClock, while the actual Coil load advances with testDispatcher.
        composeTestRule.mainClock.autoAdvance = false
        setContentWithDynamicSource(imageLoader, source)

        // Initial composition: the first source is already settled, so it loads immediately (no debounce).
        composeTestRule.mainClock.advanceTimeBy(16)
        testDispatcher.scheduler.advanceUntilIdle()
        composeTestRule.mainClock.advanceTimeBy(16)
        assertEquals(1, firstUrlLoads.get())
        assertEquals(0, secondUrlLoads.get())

        // "Type" a new URL; let the recomposition launch the debounce coroutine.
        source.value = loadingImageUrl2
        composeTestRule.mainClock.advanceTimeBy(16)
        testDispatcher.scheduler.advanceUntilIdle()

        // Still inside the 300ms window: the new URL must not have been requested yet.
        composeTestRule.mainClock.advanceTimeBy(200)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("new source must not load during the debounce window", 0, secondUrlLoads.get())

        // Cross the debounce threshold: now the settled source loads, exactly once.
        composeTestRule.mainClock.advanceTimeBy(150)
        testDispatcher.scheduler.advanceUntilIdle()
        composeTestRule.mainClock.advanceTimeBy(16)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("settled source must load after the debounce window", 1, secondUrlLoads.get())
        // The first URL was never reloaded while the second was being typed.
        assertEquals(1, firstUrlLoads.get())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    public fun `intermediate source values typed within the debounce window never load`() {
        val intermediateUrl = "https://example.com/intermediate.png"

        val initialImage = ColorImage(Color.Green.toArgb(), width = 40, height = 40)
        val intermediateImage = ColorImage(Color.Yellow.toArgb(), width = 60, height = 60)
        val finalImage = ColorImage(Color.Blue.toArgb(), width = 80, height = 80)

        val initialLoads = AtomicInteger(0)
        val intermediateLoads = AtomicInteger(0)
        val finalLoads = AtomicInteger(0)

        val testDispatcher = StandardTestDispatcher()
        val engine =
            FakeImageLoaderEngine.Builder()
                .intercept(
                    predicate = { it == loadingImageUrl },
                    interceptor = {
                        initialLoads.incrementAndGet()
                        SuccessResult(initialImage, it.request, DataSource.MEMORY)
                    },
                )
                .intercept(
                    predicate = { it == intermediateUrl },
                    interceptor = {
                        intermediateLoads.incrementAndGet()
                        SuccessResult(intermediateImage, it.request, DataSource.MEMORY)
                    },
                )
                .intercept(
                    predicate = { it == loadingImageUrl2 },
                    interceptor = {
                        finalLoads.incrementAndGet()
                        SuccessResult(finalImage, it.request, DataSource.MEMORY)
                    },
                )
                .build()
        val imageLoader =
            ImageLoader.Builder(platformContext).components { add(engine) }.coroutineContext(testDispatcher).build()

        val source = mutableStateOf(loadingImageUrl)

        composeTestRule.mainClock.autoAdvance = false
        setContentWithDynamicSource(imageLoader, source)

        // Settle on the initial source.
        composeTestRule.mainClock.advanceTimeBy(16)
        testDispatcher.scheduler.advanceUntilIdle()
        composeTestRule.mainClock.advanceTimeBy(16)
        assertEquals(1, initialLoads.get())

        // Type an intermediate value; its debounce coroutine starts and gets partway through the window...
        source.value = intermediateUrl
        composeTestRule.mainClock.advanceTimeBy(16)
        testDispatcher.scheduler.advanceUntilIdle()
        composeTestRule.mainClock.advanceTimeBy(100)

        // ...then change again before the window elapses. The intermediate coroutine is cancelled mid-delay,
        // so its assignment (which happens after delay) never runs and its URL is never requested.
        source.value = loadingImageUrl2
        composeTestRule.mainClock.advanceTimeBy(16)
        testDispatcher.scheduler.advanceUntilIdle()

        // Still inside the final source's window: neither the intermediate nor the final value has loaded.
        composeTestRule.mainClock.advanceTimeBy(200)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("intermediate value must never load", 0, intermediateLoads.get())
        assertEquals(0, finalLoads.get())

        // After the final source settles, only it loads.
        composeTestRule.mainClock.advanceTimeBy(150)
        testDispatcher.scheduler.advanceUntilIdle()
        composeTestRule.mainClock.advanceTimeBy(16)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("intermediate value must never load", 0, intermediateLoads.get())
        assertEquals("only the settled final source loads", 1, finalLoads.get())
        assertEquals(1, initialLoads.get())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    public fun `switching between two valid sources never flashes the loading indicator`() {
        val firstImage = ColorImage(Color.Green.toArgb(), width = 40, height = 40)
        val secondImage = ColorImage(Color.Blue.toArgb(), width = 80, height = 80)

        val testDispatcher = StandardTestDispatcher()
        val engine =
            FakeImageLoaderEngine.Builder()
                .intercept(
                    predicate = { it == loadingImageUrl },
                    interceptor = {
                        delay(200.milliseconds)
                        SuccessResult(firstImage, it.request, DataSource.MEMORY)
                    },
                )
                .intercept(
                    predicate = { it == loadingImageUrl2 },
                    interceptor = {
                        delay(200.milliseconds)
                        SuccessResult(secondImage, it.request, DataSource.MEMORY)
                    },
                )
                .build()
        val imageLoader =
            ImageLoader.Builder(platformContext).components { add(engine) }.coroutineContext(testDispatcher).build()

        val source = mutableStateOf(loadingImageUrl)

        composeTestRule.mainClock.autoAdvance = false
        setContentWithDynamicSource(imageLoader, source)

        // Load the first source fully.
        composeTestRule.mainClock.advanceTimeBy(16)
        testDispatcher.scheduler.advanceUntilIdle()
        composeTestRule.mainClock.advanceTimeBy(16)
        composeTestRule.onNodeWithContentDescription("img").assertExists()

        // Switch to the second (also valid) source and let the debounce settle, but do NOT complete its load yet.
        source.value = loadingImageUrl2
        composeTestRule.mainClock.advanceTimeBy(16)
        composeTestRule.mainClock.advanceTimeBy(320)

        // While the new source is loading, the previous image is kept (stale-while-revalidate): the image node
        // is still present and the loading indicator never appears.
        composeTestRule
            .onNodeWithContentDescription(Coil3ImageRendererExtensionImpl.LOADING_INDICATOR_DESCRIPTION)
            .assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("img").assertExists()

        // Once the new source finishes loading, it is shown at its own size, still without any spinner.
        testDispatcher.scheduler.advanceUntilIdle()
        composeTestRule.mainClock.advanceTimeBy(16)
        composeTestRule
            .onNodeWithContentDescription(Coil3ImageRendererExtensionImpl.LOADING_INDICATOR_DESCRIPTION)
            .assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("img").assertExists().assertWidthIsEqualTo(80.dp)
    }

    @Test
    public fun `failed image nested in emphasis keeps the emphasis style in the fallback link`() {
        val engine =
            FakeImageLoaderEngine.Builder()
                .intercept(
                    predicate = { it == failingImageUrl },
                    interceptor = { ErrorResult(null, it.request, IllegalStateException("Not found")) },
                )
                .build()
        val imageLoader = ImageLoader.Builder(platformContext).components { add(engine) }.build()

        // Emphasis is the only source of italics here, so italic in the fallback link can only have come from the
        // enclosing emphasis - not the base text style or the link style.
        val inlinesStyling =
            InlinesStyling(
                textStyle = TextStyle.Default,
                inlineCode = SpanStyle(Color.Black),
                link = SpanStyle(Color.Blue),
                linkDisabled = SpanStyle(Color.Gray),
                linkFocused = SpanStyle(Color.Blue),
                linkHovered = SpanStyle(Color.Blue),
                linkPressed = SpanStyle(Color.Blue),
                linkVisited = SpanStyle(Color.Blue),
                emphasis = SpanStyle(fontStyle = FontStyle.Italic),
                strongEmphasis = SpanStyle(fontWeight = FontWeight.Bold),
                inlineHtml = SpanStyle(Color.Black),
            )
        val paragraphStyling = MarkdownStyling.Paragraph(inlinesStyling)

        val paragraph =
            Paragraph(
                InlineMarkdown.Emphasis(
                    "*",
                    InlineMarkdown.Image(source = failingImageUrl, alt = "broken", title = null),
                )
            )

        var rendered: AnnotatedString? = null
        composeTestRule.setContent {
            JewelTheme(createMarkdownTestThemeDefinition()) {
                val imageExtension = Coil3ImageRendererExtension(imageLoader)
                val renderer =
                    DefaultMarkdownBlockRenderer(
                        rootStyling = createMarkdownTestStyling(),
                        rendererExtensions = listOf(imageExtension),
                        inlineRenderer = DefaultInlineMarkdownRenderer(listOf(imageExtension)),
                    )
                Box(modifier = Modifier.width(Int.MAX_VALUE.dp)) {
                    renderer.RenderParagraph(
                        block = paragraph,
                        styling = paragraphStyling,
                        enabled = true,
                        onUrlClick = {},
                        onTextLayout = { rendered = it.layoutInput.text },
                        modifier = Modifier,
                        overflow = TextOverflow.Clip,
                        softWrap = true,
                        maxLines = Int.MAX_VALUE,
                    )
                }
            }
        }

        // Wait until the failed image has been rebuilt into a fallback link.
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            rendered?.let { it.getLinkAnnotations(0, it.length).isNotEmpty() } == true
        }

        val text = rendered!!
        val link = text.getLinkAnnotations(0, text.length).first().item as LinkAnnotation.Clickable
        // The fallback link inherits the emphasis it was nested in, matching a real link in the same context.
        assertEquals(FontStyle.Italic.value, link.styles?.style?.fontStyle?.value)
    }

    @Test
    public fun `failed image nested in a link points the fallback link at the outer destination`() {
        val engine =
            FakeImageLoaderEngine.Builder()
                .intercept(
                    predicate = { it == failingImageUrl },
                    interceptor = { ErrorResult(null, it.request, IllegalStateException("Not found")) },
                )
                .build()
        val imageLoader = ImageLoader.Builder(platformContext).components { add(engine) }.build()

        val destination = "https://destination.example"
        // Paragraph with the text: [![broken](failing)](destination)
        val paragraph =
            Paragraph(
                InlineMarkdown.Link(
                    destination,
                    null,
                    InlineMarkdown.Image(source = failingImageUrl, alt = "broken", title = null),
                )
            )

        var rendered: AnnotatedString? = null
        composeTestRule.setContent {
            JewelTheme(createMarkdownTestThemeDefinition()) {
                val imageExtension = Coil3ImageRendererExtension(imageLoader)
                val renderer =
                    DefaultMarkdownBlockRenderer(
                        rootStyling = createMarkdownTestStyling(),
                        rendererExtensions = listOf(imageExtension),
                        inlineRenderer = DefaultInlineMarkdownRenderer(listOf(imageExtension)),
                    )
                Box(modifier = Modifier.width(Int.MAX_VALUE.dp)) {
                    renderer.RenderParagraph(
                        block = paragraph,
                        styling = createMarkdownTestStyling().paragraph,
                        enabled = true,
                        onUrlClick = {},
                        onTextLayout = { rendered = it.layoutInput.text },
                        modifier = Modifier,
                        overflow = TextOverflow.Clip,
                        softWrap = true,
                        maxLines = Int.MAX_VALUE,
                    )
                }
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            rendered?.let { it.getLinkAnnotations(0, it.length).isNotEmpty() } == true
        }

        val text = rendered!!
        val link = text.getLinkAnnotations(0, text.length).first().item as LinkAnnotation.Clickable
        // The fallback link targets the enclosing link's destination, not the (broken) image source.
        assertEquals(destination, link.tag)
    }

    @Test
    public fun `loading with null content drops a previously loaded image`() {
        val loading = mutableStateOf(false)
        val successContent =
            InlineTextContent(Placeholder(10.sp, 10.sp, PlaceholderVerticalAlign.Bottom)) {
                Box(Modifier.fillMaxSize().testTag("loaded-image"))
            }
        // A custom renderer that shows a loaded image, then "refreshes" it and returns Loading(null).
        val extension =
            object : MarkdownRendererExtension {
                override val imageRendererExtension =
                    object : ImageRendererExtension {
                        @Composable
                        override fun renderImage(image: InlineMarkdown.Image): ImageRenderResult =
                            if (loading.value) {
                                ImageRenderResult.Loading(null)
                            } else {
                                ImageRenderResult.Success(successContent)
                            }
                    }
            }

        val paragraph = Paragraph(InlineMarkdown.Image(source = loadingImageUrl, alt = "alt", title = null))

        composeTestRule.setContent {
            JewelTheme(createMarkdownTestThemeDefinition()) {
                val styling = createMarkdownTestStyling()
                val renderer =
                    DefaultMarkdownBlockRenderer(
                        rootStyling = styling,
                        rendererExtensions = listOf(extension),
                        inlineRenderer = DefaultInlineMarkdownRenderer(listOf(extension)),
                    )
                Box(modifier = Modifier.width(Int.MAX_VALUE.dp)) {
                    renderer.RenderParagraph(
                        block = paragraph,
                        styling = styling.paragraph,
                        enabled = true,
                        onUrlClick = {},
                        modifier = Modifier,
                    )
                }
            }
        }

        // The loaded image content is shown.
        composeTestRule.onNodeWithTag("loaded-image").assertExists()

        // Refreshing into Loading(null) must drop the stale success content instead of keeping it on screen.
        loading.value = true
        composeTestRule.onNodeWithTag("loaded-image").assertDoesNotExist()
    }

    @Test
    public fun `failed image with no alt in a link shows the destination as the fallback text`() {
        val engine =
            FakeImageLoaderEngine.Builder()
                .intercept(
                    predicate = { it == failingImageUrl },
                    interceptor = { ErrorResult(null, it.request, IllegalStateException("Not found")) },
                )
                .build()
        val imageLoader = ImageLoader.Builder(platformContext).components { add(engine) }.build()

        val destination = "https://destination.example"
        // Paragraph with the text: [![](failing)](destination)
        val paragraph =
            Paragraph(
                InlineMarkdown.Link(
                    destination,
                    null,
                    InlineMarkdown.Image(source = failingImageUrl, alt = "", title = null),
                )
            )

        var rendered: AnnotatedString? = null
        composeTestRule.setContent {
            JewelTheme(createMarkdownTestThemeDefinition()) {
                val imageExtension = Coil3ImageRendererExtension(imageLoader)
                val renderer =
                    DefaultMarkdownBlockRenderer(
                        rootStyling = createMarkdownTestStyling(),
                        rendererExtensions = listOf(imageExtension),
                        inlineRenderer = DefaultInlineMarkdownRenderer(listOf(imageExtension)),
                    )
                Box(modifier = Modifier.width(Int.MAX_VALUE.dp)) {
                    renderer.RenderParagraph(
                        block = paragraph,
                        styling = createMarkdownTestStyling().paragraph,
                        enabled = true,
                        onUrlClick = {},
                        onTextLayout = { rendered = it.layoutInput.text },
                        modifier = Modifier,
                        overflow = TextOverflow.Clip,
                        softWrap = true,
                        maxLines = Int.MAX_VALUE,
                    )
                }
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            rendered?.let { it.getLinkAnnotations(0, it.length).isNotEmpty() } == true
        }

        val text = rendered!!
        // With no alt, the visible text falls back to the destination (not the broken image source), so it
        // matches the link's click target.
        assertTrue(text.text.contains(destination))
        assertTrue(!text.text.contains(failingImageUrl))
        val link = text.getLinkAnnotations(0, text.length).first().item as LinkAnnotation.Clickable
        assertEquals(destination, link.tag)
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
            JewelTheme(createMarkdownTestThemeDefinition()) {
                val imageExtension = Coil3ImageRendererExtension(imageLoader)
                val markdownStyling = createMarkdownTestStyling()
                val blockRenderer =
                    DefaultMarkdownBlockRenderer(
                        rootStyling = markdownStyling,
                        rendererExtensions = listOf(imageExtension),
                        inlineRenderer = DefaultInlineMarkdownRenderer(listOf(imageExtension)),
                    )
                Box(modifier = Modifier.width(containerWidthDp.dp).testTag(PARAGRAPH_WRAPPER_TAG)) {
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

    private fun setContentWithDynamicSource(imageLoader: ImageLoader, source: State<String>) {
        composeTestRule.setContent {
            JewelTheme(createMarkdownTestThemeDefinition()) {
                val imageExtension = Coil3ImageRendererExtension(imageLoader)
                val markdownStyling = createMarkdownTestStyling()
                val blockRenderer =
                    DefaultMarkdownBlockRenderer(
                        rootStyling = markdownStyling,
                        rendererExtensions = listOf(imageExtension),
                        inlineRenderer = DefaultInlineMarkdownRenderer(listOf(imageExtension)),
                    )
                val paragraph = Paragraph(InlineMarkdown.Image(source = source.value, alt = "Alt", title = "img"))
                Box(modifier = Modifier.width(Int.MAX_VALUE.dp).testTag(PARAGRAPH_WRAPPER_TAG)) {
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
