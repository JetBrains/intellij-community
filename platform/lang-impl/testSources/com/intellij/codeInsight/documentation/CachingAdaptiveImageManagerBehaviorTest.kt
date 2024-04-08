package com.intellij.codeInsight.documentation


import com.intellij.openapi.Disposable
import com.intellij.openapi.rd.fill2DRoundRect
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.platform.diagnostic.telemetry.helpers.Milliseconds
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.ui.icons.HiDPIImage
import com.intellij.util.DataUrl
import com.intellij.util.ui.html.image.*
import com.intellij.util.ui.html.image.ImageDimension.Unit as IDUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.*
import javax.imageio.ImageIO

@ExperimentalCoroutinesApi
class CachingAdaptiveImageManagerBehaviorTest {

  private val testScope = TestScope()

  private lateinit var testDisposable: Disposable

  @Test
  fun `load and rasterize svg`() = runTest {
    val loaderSpy = spy(::getImageSourceData)
    val rasterizerSpy = spy(::rasterizeSVGImage)
    val manager = spy(createAdaptiveImageManager(loaderSpy, rasterizerSpy))
    val rendererEvents = mutableListOf<AdaptiveImageRendererEvent>()
    val renderer = manager.createRenderer(testScope) { rendererEvents.add(it) }

    // Action: initial renderer setup
    renderer.setRenderConfig(32f, 32f, 1f)
    renderer.setOrigin(AdaptiveImageOrigin.DataUrl(DataUrl.parse(generateSvgDataUrl(TEST_SVG_GREEN))))
    testScope.advanceUntilIdle()

    // Verification: loading is not started
    verifyNoInteractions(loaderSpy)
    assertThat(rendererEvents).isEmpty()


    // Action: first render attempt
    val renderAttempt1 = renderer.getRenderedImage()

    // Verification: nothing has happened synchronously
    assertThat(renderAttempt1).isNull()
    assertThat(rendererEvents).isEmpty()
    verifyNoInteractions(loaderSpy)


    // Action: async operations after first render attempt
    testScope.advanceUntilIdle()

    // Verification: image is loaded
    assertThat(rendererEvents).`as`("Should have single 'Load' event").hasSize(1).satisfiesExactly(
      {
        assertThat(it).isEqualTo(AdaptiveImageRendererEvent.Loaded(
          dimensions = ImageDimensions(100f, IDUnit.PX, 100f, IDUnit.PX, 100f, 100f),
          vector = true
        ))
      }
    )
    verify(loaderSpy, times(1)).invoke(any())
    rendererEvents.clear()
    reset(loaderSpy)


    // Action: view size update and second render attempt
    renderer.setRenderConfig(100f, 100f, 1.75f)
    val renderAttempt2 = renderer.getRenderedImage()

    // Verification: nothing has happened synchronously
    assertThat(renderAttempt2).isNull()
    assertThat(rendererEvents).isEmpty()


    // Action: async operations after view size update and second render attempt
    testScope.advanceUntilIdle()

    // Verification: image is rasterized
    assertThat(rendererEvents).`as`("Should have single 'Render' event").hasSize(1).satisfiesExactly(
      {
        assertThat(it).isEqualTo(AdaptiveImageRendererEvent.Rasterized(
          dimensions = ImageDimensions(100f, IDUnit.PX, 100f, IDUnit.PX, 100f, 100f),
          vector = true
        ))
      }
    )
    verify(rasterizerSpy, times(1)).invoke(any())
    verifyNoInteractions(loaderSpy) // Do not (re)load when rasterizing

    reset(rasterizerSpy)
    rendererEvents.clear()


    // Action: third render attempt
    val renderAttempt3 = renderer.getRenderedImage()

    // Verification: image is returned immediately and it has correct dimensions
    assertThat(renderAttempt3).`as`("Rasterized image should have correct dimensions")
      .isInstanceOf(HiDPIImage::class.java)
      .extracting("width", "height", "userWidth", "userHeight", "scale")
      .containsExactly(175, 175, 100, 100, 1.75)


    // Action: async operations after third render attempt
    testScope.advanceUntilIdle()

    // Verification: nothing has happened
    verifyNoInteractions(loaderSpy)
    verifyNoInteractions(rasterizerSpy)
    assertThat(rendererEvents).isEmpty()
  }

  @Test
  fun `svg rasterization throttle`() = runTest {
    val loaderSpy = spy(::getImageSourceData)
    val rasterizerSpy = spy(::rasterizeSVGImage)
    val manager = spy(createAdaptiveImageManager(loaderSpy, rasterizerSpy))
    val rendererEvents = mutableListOf<AdaptiveImageRendererEvent>()
    val renderer = manager.createRenderer(testScope) { rendererEvents.add(it) }
    renderer.setRenderConfig(200f, 200f, 1f)
    renderer.setOrigin(AdaptiveImageOrigin.DataUrl(DataUrl.parse(generateSvgDataUrl(TEST_SVG_GREEN))))
    while (renderer.getRenderedImage() == null && !renderer.hasError()) {
      testScope.advanceUntilIdle()
    }
    reset(rasterizerSpy, loaderSpy)
    rendererEvents.clear()

    // Action: set new view dimensions
    renderer.setRenderConfig(100f, 100f, 1.75f)

    // Verify: old image is returned
    renderer.getRenderedImage().also { img ->
      assertThat(img).`as`("Old image is returned").isNotNull()
        .extracting("width", "height")
        .containsExactly(200, 200)
    }


    // Action: immediate async actions after view dimensions change
    testScope.runCurrent()

    // Verify: nothing has happened
    assertThat(rendererEvents).isEmpty()
    verifyNoInteractions(rasterizerSpy)


    // Action: advance virtual time by RENDER_THROTTLE_MS
    testScope.advanceTimeBy(RENDER_THROTTLE_MS)

    // Verify: nothing has happened
    verifyNoInteractions(loaderSpy)
    verifyNoInteractions(rasterizerSpy)
    assertThat(rendererEvents).isEmpty()


    // Action: run immediate async operations
    testScope.runCurrent()

    // Verify: image is rasterized and correct logical dimensions are reported
    assertThat(rendererEvents).`as`("Should have single 'Render' event").hasSize(1).satisfiesExactly(
      {
        assertThat(it).isEqualTo(AdaptiveImageRendererEvent.Rasterized(
          dimensions = ImageDimensions(100f, IDUnit.PX, 100f, IDUnit.PX, 100f, 100f),
          vector = true
        ))
      }
    )
    verify(rasterizerSpy, times(1)).invoke(any())
    verifyNoInteractions(loaderSpy)
    reset(rasterizerSpy, loaderSpy)
    rendererEvents.clear()


    // Action: get rasterized image
    val newImage = renderer.getRenderedImage()

    // Verify: Image has correct dimensions
    assertThat(newImage).`as`("Rasterized image should have correct dimensions").isInstanceOf(HiDPIImage::class.java).extracting("width", "height", "userWidth", "userHeight", "scale").containsExactly(175, 175, 100, 100, 1.75)


    // Action: run remaining async operations (if any)
    testScope.advanceUntilIdle()

    // Verify: nothing has happened
    verifyNoInteractions(loaderSpy)
    verifyNoInteractions(rasterizerSpy)
    assertThat(rendererEvents).isEmpty()
  }

  @Test
  fun `svg rasterization throttle, return if image is rasterized by another renderer`() = runTest {
    val loaderSpy = spy(::getImageSourceData)
    val rasterizerSpy = spy(::rasterizeSVGImage)
    val manager = spy(createAdaptiveImageManager(loaderSpy, rasterizerSpy))
    val rendererEvents = mutableListOf<AdaptiveImageRendererEvent>()
    val renderer = manager.createRenderer(testScope) { rendererEvents.add(it) }
    renderer.setRenderConfig(200f, 200f, 1f)
    renderer.setOrigin(AdaptiveImageOrigin.DataUrl(DataUrl.parse(generateSvgDataUrl(TEST_SVG_GREEN))))
    while (renderer.getRenderedImage() == null && !renderer.hasError()) {
      testScope.advanceUntilIdle()
    }
    reset(rasterizerSpy, loaderSpy)
    rendererEvents.clear()

    // Action: set new view dimensions
    renderer.setRenderConfig(100f, 100f, 1.75f)

    // Verify: old image is returned
    renderer.getRenderedImage().also { img ->
      assertThat(img).`as`("Old image is returned").isNotNull()
        .extracting("width", "height")
        .containsExactly(200, 200)
    }


    // Action: immediate async actions after view dimensions change
    testScope.runCurrent()

    // Verify: nothing has happened
    assertThat(rendererEvents).isEmpty()
    verifyNoInteractions(rasterizerSpy)


    // Action: another renderer rasterizes the image with the same config
    val otherRenderer = manager.createRenderer(testScope) { }
    otherRenderer.setRenderConfig(100f, 100f, 1.75f)
    otherRenderer.setOrigin(AdaptiveImageOrigin.DataUrl(DataUrl.parse(generateSvgDataUrl(TEST_SVG_GREEN))))
    while (otherRenderer.getRenderedImage() == null) {
      testScope.advanceUntilIdle()
    }
    reset(rasterizerSpy, loaderSpy)

    // Verify: rasterized image is returned immediately
    val newImage = renderer.getRenderedImage()
    assertThat(newImage).`as`("Rasterized image should have correct dimensions")
      .isInstanceOf(HiDPIImage::class.java)
      .extracting("width", "height", "userWidth", "userHeight", "scale")
      .containsExactly(175, 175, 100, 100, 1.75)
    assertThat(rendererEvents).`as`("Should have single 'Render' event").hasSize(1).satisfiesExactly(
      {
        assertThat(it).isEqualTo(AdaptiveImageRendererEvent.Rasterized(
          dimensions = ImageDimensions(100f, IDUnit.PX, 100f, IDUnit.PX, 100f, 100f),
          vector = true
        ))
      }
    )
    verifyNoInteractions(rasterizerSpy)
    verifyNoInteractions(loaderSpy)

    reset(rasterizerSpy, loaderSpy)
    rendererEvents.clear()


    // Action: run remaining async operations (if any)
    testScope.advanceUntilIdle()

    // Verify: nothing has happened
    verifyNoInteractions(loaderSpy)
    verifyNoInteractions(rasterizerSpy)
    assertThat(rendererEvents).isEmpty()
  }

  @Test
  fun `load png`() = runTest {
    val loaderSpy = spy(::getImageSourceData)
    val manager = spy(createAdaptiveImageManager(loaderSpy))
    val rendererEvents = mutableListOf<AdaptiveImageRendererEvent>()
    val renderer = manager.createRenderer(testScope) { rendererEvents.add(it) }

    // Action: initial renderer setup
    renderer.setRenderConfig(32f, 32f, 1f)
    renderer.setOrigin(AdaptiveImageOrigin.DataUrl(DataUrl.parse(generatePngDataUrl(generateTestPng()))))
    testScope.advanceUntilIdle()

    // Verification: loading is not started
    verifyNoInteractions(loaderSpy)
    assertThat(rendererEvents).isEmpty()


    // Action: first render attempt
    val renderAttempt1 = renderer.getRenderedImage()

    // Verification: nothing has happened synchronously
    assertThat(renderAttempt1).isNull()
    assertThat(rendererEvents).isEmpty()
    verifyNoInteractions(loaderSpy)


    // Action: async operations after first render attempt
    testScope.advanceUntilIdle()

    // Verification: image is loaded
    assertThat(rendererEvents).`as`("Should have single 'Load' event").hasSize(1).satisfiesExactly(
      {
        assertThat(it).isEqualTo(AdaptiveImageRendererEvent.Loaded(
          dimensions = ImageDimensions(100f, IDUnit.PX, 100f, IDUnit.PX, 100f, 100f),
          vector = false
        ))
      }
    )
    verify(loaderSpy, times(1)).invoke(any())
    rendererEvents.clear()
    reset(loaderSpy)

    // Action: second render attempt
    val renderAttempt2 = renderer.getRenderedImage()

    // Verification: image is returned immediately and it has correct dimensions
    assertThat(renderAttempt2).`as`("Image should have correct dimensions").isInstanceOf(BufferedImage::class.java).extracting("width", "height").containsExactly(100, 100)


    // Action: async operations after second render attempt
    testScope.advanceUntilIdle()

    // Verification: nothing has happened
    verifyNoInteractions(loaderSpy)
    assertThat(rendererEvents).isEmpty()
  }

  @Test
  fun `multiple renderers load and rasterize the same svg simultaneously`() = runTest {
    val loaderSpy = spy(::getImageSourceData)
    val rasterizerSpy = spy(::rasterizeSVGImage)
    val manager = spy(createAdaptiveImageManager(loaderSpy, rasterizerSpy))
    val renderer1Events = mutableListOf<AdaptiveImageRendererEvent>()
    val renderer1 = manager.createRenderer(testScope) { renderer1Events.add(it) }
    val renderer2Events = mutableListOf<AdaptiveImageRendererEvent>()
    val renderer2 = manager.createRenderer(testScope) { renderer2Events.add(it) }

    // Action: initial renderers setup & trigger loading
    listOf(renderer1, renderer2).forEach {
      it.setRenderConfig(32f, 32f, 1f)
      it.setOrigin(AdaptiveImageOrigin.DataUrl(DataUrl.parse(generateSvgDataUrl(TEST_SVG_GREEN))))
      it.getRenderedImage()
    }

    // Verification: loading initiated, but not performed
    assertThat(manager.getImagesBeingLoadedCount()).isEqualTo(1)
    verifyNoInteractions(loaderSpy)
    assertThat(renderer1Events).isEmpty()
    assertThat(renderer2Events).isEmpty()


    // Action: perform async loading operations
    testScope.advanceUntilIdle()

    // Verification: both renderers report image loaded, loader called once
    assertThat(renderer1Events).`as`("Renderer1 should receive single 'Load' event").hasSize(1).satisfiesExactly(
      {
        assertThat(it).isEqualTo(AdaptiveImageRendererEvent.Loaded(
          dimensions = ImageDimensions(100f, IDUnit.PX, 100f, IDUnit.PX, 100f, 100f),
          vector = true
        ))
      }
    )
    assertThat(renderer2Events).`as`("Renderer2 should receive 'Load' event").hasSize(1).satisfiesExactly(
      {
        assertThat(it).isEqualTo(AdaptiveImageRendererEvent.Loaded(
          dimensions = ImageDimensions(100f, IDUnit.PX, 100f, IDUnit.PX, 100f, 100f),
          vector = true
        ))
      }
    )
    verify(loaderSpy, times(1)).invoke(any())
    verifyNoInteractions(rasterizerSpy)

    listOf(renderer1Events, renderer2Events).forEach { it.clear() }
    reset(loaderSpy)


    // Action: Trigger rasterization on both renderers
    listOf(renderer1, renderer2).forEach {
      it.setRenderConfig(100f, 100f, 1.75f)
      it.getRenderedImage()
    }

    // Verification: Rasterization initiated, but not performed
    assertThat(manager.getImagesBeingRasterizedCount()).isEqualTo(1)
    verifyNoInteractions(loaderSpy)
    verifyNoInteractions(rasterizerSpy)
    assertThat(renderer1Events).isEmpty()
    assertThat(renderer2Events).isEmpty()


    // Action: perform async rasterization operations
    testScope.advanceUntilIdle()

    // Verification: both renderers report image rasterized, rasterizer called once
    assertThat(renderer1Events).`as`("Renderer1 should receive single 'Render' event").hasSize(1).satisfiesExactly(
      {
        assertThat(it).isEqualTo(AdaptiveImageRendererEvent.Rasterized(
          dimensions = ImageDimensions(100f, IDUnit.PX, 100f, IDUnit.PX, 100f, 100f),
          vector = true
        ))
      }
    )
    assertThat(renderer2Events).`as`("Renderer2 should receive single 'Render' event").hasSize(1).satisfiesExactly(
      {
        assertThat(it).isEqualTo(AdaptiveImageRendererEvent.Rasterized(
          dimensions = ImageDimensions(100f, IDUnit.PX, 100f, IDUnit.PX, 100f, 100f),
          vector = true
        ))
      }
    )
    verifyNoInteractions(loaderSpy)
    verify(rasterizerSpy, times(1)).invoke(any())

    reset(rasterizerSpy)
    listOf(renderer1Events, renderer2Events).forEach { it.clear() }


    // Action: get rendered images
    val renderedImage1 = renderer1.getRenderedImage()
    val renderedImage2 = renderer2.getRenderedImage()

    // Verification: images are returned immediately and they have correct dimensions
    assertThat(renderedImage1).`as`("Rasterized image should have correct dimensions")
      .isInstanceOf(HiDPIImage::class.java)
      .extracting("width", "height", "userWidth", "userHeight", "scale")
      .containsExactly(175, 175, 100, 100, 1.75)
    assertThat(renderedImage2).isSameAs(renderedImage1)


    // Action: async operations after getting rendered images
    testScope.advanceUntilIdle()

    // Verification: nothing has happened
    verifyNoInteractions(loaderSpy)
    verifyNoInteractions(rasterizerSpy)
    assertThat(renderer1Events).isEmpty()
    assertThat(renderer2Events).isEmpty()
  }

  @Test
  fun `multiple renderers load the same png simultaneously`() = runTest {
    val loaderSpy = spy(::getImageSourceData)
    val manager = spy(createAdaptiveImageManager(loaderSpy))
    val renderer1Events = mutableListOf<AdaptiveImageRendererEvent>()
    val renderer1 = manager.createRenderer(testScope) { renderer1Events.add(it) }
    val renderer2Events = mutableListOf<AdaptiveImageRendererEvent>()
    val renderer2 = manager.createRenderer(testScope) { renderer2Events.add(it) }

    // Action: initial renderers setup & trigger loading
    listOf(renderer1, renderer2).forEach {
      it.setRenderConfig(32f, 32f, 1f)
      it.setOrigin(AdaptiveImageOrigin.DataUrl(DataUrl.parse(generatePngDataUrl(generateTestPng()))))
      it.getRenderedImage()
    }

    // Verification: loading initiated, but not performed
    assertThat(manager.getImagesBeingLoadedCount()).isEqualTo(1)
    verifyNoInteractions(loaderSpy)
    assertThat(renderer1Events).isEmpty()
    assertThat(renderer2Events).isEmpty()


    // Action: async load operation
    testScope.advanceUntilIdle()

    // Verification: both renderers report image loaded, loader called once
    assertThat(renderer1Events).`as`("Renderer1 should receive single 'Load' event").hasSize(1).satisfiesExactly(
      {
        assertThat(it).isEqualTo(AdaptiveImageRendererEvent.Loaded(
          dimensions = ImageDimensions(100f, IDUnit.PX, 100f, IDUnit.PX, 100f, 100f),
          vector = false
        ))
      }
    )
    assertThat(renderer2Events).`as`("Renderer2 should receive 'Load' event").hasSize(1).satisfiesExactly(
      {
        assertThat(it).isEqualTo(AdaptiveImageRendererEvent.Loaded(
          dimensions = ImageDimensions(100f, IDUnit.PX, 100f, IDUnit.PX, 100f, 100f),
          vector = false
        ))
      }
    )
    verify(loaderSpy, times(1)).invoke(any())

    listOf(renderer1Events, renderer2Events).forEach { it.clear() }
    reset(loaderSpy)


    // Action: get rendered images
    val renderedImage1 = renderer1.getRenderedImage()
    val renderedImage2 = renderer2.getRenderedImage()

    // Verification: images are returned immediately and they have correct dimensions
    assertThat(renderedImage1).`as`("Rasterized image should have correct dimensions")
      .isInstanceOf(BufferedImage::class.java)
      .extracting("width", "height")
      .containsExactly(100, 100)
    assertThat(renderedImage2).isSameAs(renderedImage1)


    // Action: async operations after getting rendered images
    testScope.advanceUntilIdle()

    // Verification: nothing has happened
    verifyNoInteractions(loaderSpy)
    assertThat(renderer1Events).isEmpty()
    assertThat(renderer2Events).isEmpty()
  }

  @Test
  fun `render svg, load cache hit and raster cache hit`() = runTest {
    val loaderSpy = spy(::getImageSourceData)
    val rasterizerSpy = spy(::rasterizeSVGImage)
    val manager = spy(createAdaptiveImageManager(loaderSpy, rasterizerSpy))
    val rendererEvents = mutableListOf<AdaptiveImageRendererEvent>()
    val renderer = manager.createRenderer(testScope) { rendererEvents.add(it) }

    // Setup: load and rasterize svg to cache it
    val r = manager.createRenderer(testScope) { }
    r.setRenderConfig(100f, 100f, 1.5f)
    r.setOrigin(AdaptiveImageOrigin.DataUrl(DataUrl.parse(generateSvgDataUrl(TEST_SVG_GREEN))))
    while (r.getRenderedImage() == null && !renderer.hasError()) {
      testScope.testScheduler.advanceUntilIdle()
    }

    reset(loaderSpy, rasterizerSpy)


    // Action: initial renderer setup and render request
    renderer.setRenderConfig(100f, 100f, 1.5f)
    renderer.setOrigin(AdaptiveImageOrigin.DataUrl(DataUrl.parse(generateSvgDataUrl(TEST_SVG_GREEN))))
    val renderedImage = renderer.getRenderedImage()

    // Verification: image returned from cache immediately
    assertThat(renderedImage).`as`("Rasterized image should have correct dimensions")
      .isInstanceOf(HiDPIImage::class.java)
      .extracting("width", "height", "userWidth", "userHeight", "scale")
      .containsExactly(150, 150, 100, 100, 1.5)
    assertThat(rendererEvents).`as`("Renderer should receive 'Load' and 'Render' events").hasSize(2).satisfiesExactly(
      {
        assertThat(it).isEqualTo(AdaptiveImageRendererEvent.Loaded(
          dimensions = ImageDimensions(100f, IDUnit.PX, 100f, IDUnit.PX, 100f, 100f),
          vector = true
        ))
      },
      {
        assertThat(it).isEqualTo(AdaptiveImageRendererEvent.Rasterized(
          dimensions = ImageDimensions(100f, IDUnit.PX, 100f, IDUnit.PX, 100f, 100f),
          vector = true
        ))
      }
    )
    verifyNoInteractions(loaderSpy)
    verifyNoInteractions(rasterizerSpy)

    rendererEvents.clear()
    reset(loaderSpy, rasterizerSpy)

    // Action: async operations after getting rendered image
    testScope.advanceUntilIdle()

    // Verification: nothing has happened
    verifyNoInteractions(loaderSpy)
    verifyNoInteractions(rasterizerSpy)
    assertThat(rendererEvents).isEmpty()
  }

  @Test
  fun `render svg, load cache hit, update view dimensions and raster cache hit`() = runTest {
    val loaderSpy = spy(::getImageSourceData)
    val rasterizerSpy = spy(::rasterizeSVGImage)
    val manager = spy(createAdaptiveImageManager(loaderSpy, rasterizerSpy))
    val rendererEvents = mutableListOf<AdaptiveImageRendererEvent>()
    val renderer = manager.createRenderer(testScope) { rendererEvents.add(it) }

    // Setup: load and rasterize svg to cache it
    val r = manager.createRenderer(testScope) { }
    r.setRenderConfig(100f, 100f, 1.5f)
    r.setOrigin(AdaptiveImageOrigin.DataUrl(DataUrl.parse(generateSvgDataUrl(TEST_SVG_GREEN))))
    while (r.getRenderedImage() == null && !renderer.hasError()) {
      testScope.testScheduler.advanceUntilIdle()
    }

    reset(loaderSpy, rasterizerSpy)


    // Action: initial renderer setup and render request
    renderer.setRenderConfig(32f, 32f, 1.5f)
    renderer.setOrigin(AdaptiveImageOrigin.DataUrl(DataUrl.parse(generateSvgDataUrl(TEST_SVG_GREEN))))
    val renderedAttempt1 = renderer.getRenderedImage()

    // Verification: 'Load' event fired immediately
    assertThat(renderedAttempt1).isNull()
    assertThat(rendererEvents).`as`("Renderer should receive 'Load' event").hasSize(1).satisfiesExactly(
      {
        assertThat(it).isEqualTo(AdaptiveImageRendererEvent.Loaded(
          dimensions = ImageDimensions(100f, IDUnit.PX, 100f, IDUnit.PX, 100f, 100f),
          vector = true
        ))
      }
    )
    verifyNoInteractions(loaderSpy)
    verifyNoInteractions(rasterizerSpy)

    rendererEvents.clear()
    reset(loaderSpy, rasterizerSpy)


    // Action: change view dimensions
    renderer.setRenderConfig(100f, 100f, 1.5f)
    testScope.advanceUntilIdle()

    // Verification: nothing has happened
    verifyNoInteractions(loaderSpy)
    verifyNoInteractions(rasterizerSpy)
    assertThat(rendererEvents).isEmpty()



    // Action: second render request
    val renderedImage = renderer.getRenderedImage()

    // Verification: rendered image returned immediately from cache
    assertThat(renderedImage).`as`("Rasterized image should have correct dimensions")
      .isInstanceOf(HiDPIImage::class.java)
      .extracting("width", "height", "userWidth", "userHeight", "scale")
      .containsExactly(150, 150, 100, 100, 1.5)
    assertThat(rendererEvents).`as`("Renderer should receive 'Render' event").hasSize(1).satisfiesExactly(
      {
        assertThat(it).isEqualTo(AdaptiveImageRendererEvent.Rasterized(
          dimensions = ImageDimensions(100f, IDUnit.PX, 100f, IDUnit.PX, 100f, 100f),
          vector = true
        ))
      }
    )

    rendererEvents.clear()


    // Action: post-render async operations
    testScope.advanceUntilIdle()

    // Verification: nothing has happened
    verifyNoInteractions(loaderSpy)
    verifyNoInteractions(rasterizerSpy)
    assertThat(rendererEvents).isEmpty()
  }

  @Test
  fun `load png, cache hit`() = runTest {
    val loaderSpy = spy(::getImageSourceData)
    val manager = spy(createAdaptiveImageManager(loaderSpy))
    val rendererEvents = mutableListOf<AdaptiveImageRendererEvent>()
    val renderer = manager.createRenderer(testScope) { rendererEvents.add(it) }

    // Setup: load png to cache it
    val r = manager.createRenderer(testScope) { }
    r.setRenderConfig(100f, 100f, 1.5f)
    r.setOrigin(AdaptiveImageOrigin.DataUrl(DataUrl.parse(generatePngDataUrl(generateTestPng()))))
    while (r.getRenderedImage() == null && !renderer.hasError()) {
      testScope.testScheduler.advanceUntilIdle()
    }

    reset(loaderSpy)


    // Action: initial renderer setup
    renderer.setRenderConfig(32f, 32f, 1f)
    renderer.setOrigin(AdaptiveImageOrigin.DataUrl(DataUrl.parse(generatePngDataUrl(generateTestPng()))))
    testScope.advanceUntilIdle()

    // Verification: loading is not started
    verifyNoInteractions(loaderSpy)
    assertThat(rendererEvents).isEmpty()


    // Action: request rendered image, it should be returned from cache immediately
    val renderedImage = renderer.getRenderedImage()

    // Verification: nothing has happened synchronously
    assertThat(renderedImage).`as`("Image should have correct dimensions")
      .isInstanceOf(BufferedImage::class.java)
      .extracting("width", "height")
      .containsExactly(100, 100)
    assertThat(rendererEvents).`as`("Should have single 'Load' event").hasSize(1).satisfiesExactly(
      {
        assertThat(it).isEqualTo(AdaptiveImageRendererEvent.Loaded(
          dimensions = ImageDimensions(100f, IDUnit.PX, 100f, IDUnit.PX, 100f, 100f),
          vector = false
        ))
      }
    )
    verifyNoInteractions(loaderSpy)

    rendererEvents.clear()


    // Action: async operations after rendering
    testScope.advanceUntilIdle()

    // Verification: nothing has happened
    verifyNoInteractions(loaderSpy)
    assertThat(rendererEvents).isEmpty()
  }

  @Test
  fun `vfs invalidation, loaded svg is reloaded automatically`() = runTest {
    val mockFileUrl = "file://test.svg"

    val virtualFileMock = mock<VirtualFile>(defaultAnswer = { throw UnsupportedOperationException() })
    doReturn("Mocked VirtualFile").`when`(virtualFileMock).toString()

    val loaderMock = mock<(AdaptiveImageSource) -> DataWithMimeType>(defaultAnswer = { throw UnsupportedOperationException() })
    doReturn(DataWithMimeType(TEST_SVG_GREEN.toByteArray(), "image/svg+xml"))
      .`when`(loaderMock).invoke(eq(VfsAdaptiveImageSource(virtualFileMock)))

    val sourceResolverMock = mock<(AdaptiveImageOrigin) -> AdaptiveImageSource?>(defaultAnswer = { throw UnsupportedOperationException() })
    doReturn(VfsAdaptiveImageSource(virtualFileMock))
      .`when`(sourceResolverMock).invoke(eq(AdaptiveImageOrigin.Url(mockFileUrl)))

    val rasterizerSpy = spy(::rasterizeSVGImage)
    val manager = spy(createAdaptiveImageManager(loaderMock, rasterizerSpy, sourceResolverMock))
    val rendererEvents = mutableListOf<AdaptiveImageRendererEvent>()
    val renderer = manager.createRenderer(testScope) { rendererEvents.add(it) }

    // Setup: load and rasterize svg
    renderer.setRenderConfig(100f, 100f, 1.5f)
    renderer.setOrigin(AdaptiveImageOrigin.Url(mockFileUrl))
    while (renderer.getRenderedImage() == null && !renderer.hasError()) {
      testScope.advanceUntilIdle()
    }

    // Verification: correct image dimensions in events
    assertThat(rendererEvents).`as`("Renderer should receive 'Load' and 'Render' events").hasSize(2).satisfiesExactly(
      {
        assertThat(it).isEqualTo(AdaptiveImageRendererEvent.Loaded(
          dimensions = ImageDimensions(100f, IDUnit.PX, 100f, IDUnit.PX, 100f, 100f),
          vector = true
        ))
      },
      {
        assertThat(it).isEqualTo(AdaptiveImageRendererEvent.Rasterized(
          dimensions = ImageDimensions(100f, IDUnit.PX, 100f, IDUnit.PX, 100f, 100f),
          vector = true
        ))
      }
    )
    assertThat(manager.getImagesBeingLoadedCount()).isEqualTo(0)

    rendererEvents.clear()


    // Action: emulate Vfs change event
    reset(loaderMock, rasterizerSpy)
    doReturn(DataWithMimeType(TEST_SVG_RED.toByteArray(), "image/svg+xml"))
      .`when`(loaderMock).invoke(eq(VfsAdaptiveImageSource(virtualFileMock)))
    manager.testProcessVfsEvents(mutableListOf(VFileContentChangeEvent(null, virtualFileMock, 10L, 20L)))

    // Verification: load request initiated, no image is rendered
    assertThat(manager.getImagesBeingLoadedCount()).isEqualTo(1)
    assertThat(renderer.getRenderedImage()).isNull()
    assertThat(rendererEvents).isEmpty()


    // Action: complete rasterizing the image
    while (renderer.getRenderedImage() == null && !renderer.hasError()) {
      testScope.advanceUntilIdle()
    }

    // Verification: 'Load' and 'Render' events received, image rasterized with correct dimensions
    assertThat(rendererEvents).`as`("Renderer should receive 'Load' and 'Render' events").hasSize(2).satisfiesExactly(
      {
        assertThat(it).isEqualTo(AdaptiveImageRendererEvent.Loaded(
          dimensions = ImageDimensions(200f, IDUnit.PX, 200f, IDUnit.PX, 200f, 200f),
          vector = true
        ))
      },
      {
        assertThat(it).isEqualTo(AdaptiveImageRendererEvent.Rasterized(
          dimensions = ImageDimensions(200f, IDUnit.PX, 200f, IDUnit.PX, 200f, 200f),
          vector = true
        ))
      }
    )
    assertThat(renderer.getRenderedImage()).`as`("Rasterized image should have correct dimensions")
      .isInstanceOf(HiDPIImage::class.java)
      .extracting("width", "height", "userWidth", "userHeight", "scale")
      .containsExactly(150, 150, 100, 100, 1.5)
  }

  @Test
  fun `vfs invalidation, unloaded svg is not reloaded automatically`() = runTest {
    val mockFileUrl = "file://test.svg"

    val virtualFileMock = mock<VirtualFile>(defaultAnswer = { throw UnsupportedOperationException() })
    doReturn("Mocked VirtualFile").`when`(virtualFileMock).toString()

    val loaderMock = mock<(AdaptiveImageSource) -> DataWithMimeType>(defaultAnswer = { throw UnsupportedOperationException() })
    doReturn(DataWithMimeType(TEST_SVG_GREEN.toByteArray(), "image/svg+xml"))
      .`when`(loaderMock).invoke(eq(VfsAdaptiveImageSource(virtualFileMock)))

    val sourceResolverMock = mock<(AdaptiveImageOrigin) -> AdaptiveImageSource?>(defaultAnswer = { throw UnsupportedOperationException() })
    doReturn(VfsAdaptiveImageSource(virtualFileMock))
      .`when`(sourceResolverMock).invoke(eq(AdaptiveImageOrigin.Url(mockFileUrl)))

    val rasterizerSpy = spy(::rasterizeSVGImage)
    val manager = spy(createAdaptiveImageManager(loaderMock, rasterizerSpy, sourceResolverMock))
    val rendererEvents = mutableListOf<AdaptiveImageRendererEvent>()
    val renderer = manager.createRenderer(testScope) { rendererEvents.add(it) }

    // Setup: load and rasterize svg
    renderer.setRenderConfig(100f, 100f, 1.5f)
    renderer.setOrigin(AdaptiveImageOrigin.Url(mockFileUrl))
    while (renderer.getRenderedImage() == null && !renderer.hasError()) {
      testScope.advanceUntilIdle()
    }

    // Verification: correct image dimensions in events
    assertThat(rendererEvents).`as`("Renderer should receive 'Load' and 'Render' events").hasSize(2).satisfiesExactly(
      {
        assertThat(it).isEqualTo(AdaptiveImageRendererEvent.Loaded(
          dimensions = ImageDimensions(100f, IDUnit.PX, 100f, IDUnit.PX, 100f, 100f),
          vector = true
        ))
      },
      {
        assertThat(it).isEqualTo(AdaptiveImageRendererEvent.Rasterized(
          dimensions = ImageDimensions(100f, IDUnit.PX, 100f, IDUnit.PX, 100f, 100f),
          vector = true
        ))
      }
    )
    assertThat(manager.getImagesBeingLoadedCount()).isEqualTo(0)

    rendererEvents.clear()


    // Action: unload image and emulate Vfs change event
    reset(loaderMock, rasterizerSpy)
    manager.conditionalUnloadImages { true }
    doReturn(DataWithMimeType(TEST_SVG_RED.toByteArray(), "image/svg+xml"))
      .`when`(loaderMock).invoke(eq(VfsAdaptiveImageSource(virtualFileMock)))
    manager.testProcessVfsEvents(mutableListOf(VFileContentChangeEvent(null, virtualFileMock, 10L, 20L)))
    testScope.advanceUntilIdle()

    // Verification: load request is not initiated
    assertThat(manager.getImagesBeingLoadedCount()).isEqualTo(0)
    assertThat(rendererEvents).isEmpty()


    // Action: load and rasterize the image
    while (renderer.getRenderedImage() == null && !renderer.hasError()) {
      testScope.advanceUntilIdle()
    }

    // Verification: 'Load' and 'Render' events received, image rasterized with correct dimensions
    assertThat(rendererEvents).`as`("Renderer should receive 'Load' and 'Render' events").hasSize(2).satisfiesExactly(
      {
        assertThat(it).isEqualTo(AdaptiveImageRendererEvent.Loaded(
          dimensions = ImageDimensions(200f, IDUnit.PX, 200f, IDUnit.PX, 200f, 200f),
          vector = true
        ))
      },
      {
        assertThat(it).isEqualTo(AdaptiveImageRendererEvent.Rasterized(
          dimensions = ImageDimensions(200f, IDUnit.PX, 200f, IDUnit.PX, 200f, 200f),
          vector = true
        ))
      }
    )
    assertThat(renderer.getRenderedImage()).`as`("Rasterized image should have correct dimensions")
      .isInstanceOf(HiDPIImage::class.java)
      .extracting("width", "height", "userWidth", "userHeight", "scale")
      .containsExactly(150, 150, 100, 100, 1.5)
  }

  @Test
  fun `VirtualFile to renderer mapping dropped if renderer GCed`() = runTest {
    val mockFileUrl = "file://test.svg"

    val virtualFileMock = mock<VirtualFile>(defaultAnswer = { throw UnsupportedOperationException() })
    doReturn("Mocked VirtualFile").`when`(virtualFileMock).toString()

    val loaderMock = mock<(AdaptiveImageSource) -> DataWithMimeType>(defaultAnswer = { throw UnsupportedOperationException() })
    doReturn(DataWithMimeType(TEST_SVG_GREEN.toByteArray(), "image/svg+xml"))
      .`when`(loaderMock).invoke(eq(VfsAdaptiveImageSource(virtualFileMock)))

    val sourceResolverMock = mock<(AdaptiveImageOrigin) -> AdaptiveImageSource?>(defaultAnswer = { throw UnsupportedOperationException() })
    doReturn(VfsAdaptiveImageSource(virtualFileMock))
      .`when`(sourceResolverMock).invoke(eq(AdaptiveImageOrigin.Url(mockFileUrl)))

    val rasterizerSpy = spy(::rasterizeSVGImage)
    val manager = createAdaptiveImageManager(loaderMock, rasterizerSpy, sourceResolverMock)

    val innerFunc = {
      val renderer: AdaptiveImageRenderer = manager.createRenderer(testScope) { }

      // Setup: load and rasterize svg
      renderer.setRenderConfig(100f, 100f, 1.5f)
      renderer.setOrigin(AdaptiveImageOrigin.Url(mockFileUrl))
      while (renderer.getRenderedImage() == null && !renderer.hasError()) {
        testScope.advanceUntilIdle()
      }

      // Verification: VirtualFile -> Renderer mapping is present
      assertThat(manager.getVirtualFileToRenderersMappingCount()).isEqualTo(1)
    }
    innerFunc()

    for (i in 0 until 1000) {
      System.gc()
      if (manager.getVirtualFileToRenderersMappingCount() == 0) break
      yield()
    }

    // Verification: VirtualFile -> Renderer mapping is removed
    assertThat(manager.getVirtualFileToRenderersMappingCount()).isEqualTo(0)
  }

  @Test
  fun `least recent used items unloaded when enforcing max cache size`() = runTest {
    val unloadedItems = mutableListOf<Unloadable<*,*>>()
    val manager = spy(createAdaptiveImageManager(cacheSize = 512L * 1024, unloadListener = { unloadedItems.add(it) }))

    // Setup: render svg and png and get actual images from the manager
    val pngRenderer = manager.createRenderer(testScope) { }
    pngRenderer.setRenderConfig(100f, 100f, 1.5f)
    pngRenderer.setOrigin(AdaptiveImageOrigin.DataUrl(DataUrl.parse(generatePngDataUrl(generateTestPng(Color(0))))))
    while (pngRenderer.getRenderedImage() == null && !pngRenderer.hasError()) {
      testScope.testScheduler.advanceUntilIdle()
    }

    val svgRenderer = manager.createRenderer(testScope) { }
    svgRenderer.setRenderConfig(200f, 200f, 1f)
    svgRenderer.setOrigin(AdaptiveImageOrigin.DataUrl(DataUrl.parse(generateSvgDataUrl(TEST_SVG_GREEN))))
    while (svgRenderer.getRenderedImage() == null && !svgRenderer.hasError()) {
      testScope.advanceUntilIdle()
    }

    val initialLoadedImages = manager.getCachedLoadedImages()
    val initialRasterizedImages = manager.getCachedRasterizedImages()
    val loadedSvgImage = initialLoadedImages.first { it.value is LoadedSVGImage }
    val loadedPngImage = initialLoadedImages.first { it.value is LoadedRasterImage }
    val rasterizedSvgImage = initialRasterizedImages.first()

    // use pngRenderer after svgRenderer to make it unload after svgRenderer
    svgRenderer.getRenderedImage()
    pngRenderer.getRenderedImage()

    // Start spamming PNGs
    var idx = 0
    val tmpRenderers = mutableListOf<AdaptiveImageRenderer>()
    while (loadedSvgImage.loaded || loadedPngImage.loaded || rasterizedSvgImage.loaded) {
      idx++
      val r = manager.createRenderer(testScope) { }
      r.setRenderConfig(100f, 100f, 1.5f)
      r.setOrigin(AdaptiveImageOrigin.DataUrl(DataUrl.parse(generatePngDataUrl(generateTestPng(Color(idx))))))
      while (r.getRenderedImage() == null && !r.hasError()) {
        testScope.testScheduler.advanceUntilIdle()
      }
      tmpRenderers.add(r) //keep renderers to make sure they are not GCed
    }

    // Verification: items are unloaded in correct order
    assertThat(unloadedItems).hasSize(3).containsExactly(loadedSvgImage, rasterizedSvgImage, loadedPngImage)
  }

  @Test
  fun `error when loading svg, then reload with no error`() = runTest {
    val loaderSpy = spy(::getImageSourceData)
    doAnswer { throw RuntimeException("SVG load error") }
      .`when`(loaderSpy).invoke(any())

    val manager = createAdaptiveImageManager(loaderSpy)
    val rendererEvents = mutableListOf<AdaptiveImageRendererEvent>()
    val renderer = manager.createRenderer(testScope) { rendererEvents.add(it) }

    // Action: try rendering svg
    renderer.setRenderConfig(64f, 64f, 2f)
    renderer.setOrigin(AdaptiveImageOrigin.DataUrl(DataUrl.parse(generateSvgDataUrl(TEST_SVG_GREEN))))
    while (!renderer.hasError() && renderer.getRenderedImage() == null) {
      testScope.advanceUntilIdle()
    }

    // Verification: 'Error' event is received
    assertThat(rendererEvents).`as`("Should have single 'Error' event").hasSize(1).satisfiesExactly(
      { assertThat(it).isInstanceOf(AdaptiveImageRendererEvent.Error::class.java) }
    )

    rendererEvents.clear()


    // Action: reset interception and try loading again
    reset(loaderSpy)
    renderer.resetError()
    while (!renderer.hasError() && renderer.getRenderedImage() == null) {
      testScope.advanceUntilIdle()
    }

    // Verify: image loaded successfully
    val renderedImage = renderer.getRenderedImage()
    assertThat(renderedImage).`as`("Rasterized image should have correct dimensions")
      .isInstanceOf(HiDPIImage::class.java)
      .extracting("width", "height", "userWidth", "userHeight", "scale")
      .containsExactly(128, 128, 64, 64, 2.0)
    assertThat(rendererEvents).`as`("Renderer should receive 'Load' and 'Render' events").hasSize(2).satisfiesExactly(
      {
        assertThat(it).isEqualTo(AdaptiveImageRendererEvent.Loaded(
          dimensions = ImageDimensions(100f, IDUnit.PX, 100f, IDUnit.PX, 100f, 100f),
          vector = true
        ))
      },
      {
        assertThat(it).isEqualTo(AdaptiveImageRendererEvent.Rasterized(
          dimensions = ImageDimensions(100f, IDUnit.PX, 100f, IDUnit.PX, 100f, 100f),
          vector = true
        ))
      }
    )
  }

  @Test
  fun `error when rasterizing svg, then re-render with no error`() = runTest {
    val rasterizerSpy = spy(::rasterizeSVGImage)
    doAnswer { throw RuntimeException("SVG rasterization error") }
      .`when`(rasterizerSpy).invoke(any())

    val manager = createAdaptiveImageManager(svgRasterizer = rasterizerSpy)
    val rendererEvents = mutableListOf<AdaptiveImageRendererEvent>()
    val renderer = manager.createRenderer(testScope) { rendererEvents.add(it) }

    // Action: try rendering svg
    renderer.setRenderConfig(64f, 64f, 2f)
    renderer.setOrigin(AdaptiveImageOrigin.DataUrl(DataUrl.parse(generateSvgDataUrl(TEST_SVG_GREEN))))
    while (!renderer.hasError() && renderer.getRenderedImage() == null) {
      testScope.advanceUntilIdle()
    }

    // Verification: 'Load' and 'Error' event is received
    assertThat(rendererEvents).`as`("Should have single 'Error' event").hasSize(2).satisfiesExactly(
      {
        assertThat(it).isEqualTo(AdaptiveImageRendererEvent.Loaded(
          dimensions = ImageDimensions(100f, IDUnit.PX, 100f, IDUnit.PX, 100f, 100f),
          vector = true
        ))
      },
      { assertThat(it).isInstanceOf(AdaptiveImageRendererEvent.Error::class.java) }
    )

    rendererEvents.clear()


    // Action: reset interception and try rendering again
    reset(rasterizerSpy)
    renderer.resetError()
    while (!renderer.hasError() && renderer.getRenderedImage() == null) {
      testScope.advanceUntilIdle()
    }

    // Verify: image loaded successfully
    val renderedImage = renderer.getRenderedImage()
    assertThat(renderedImage).`as`("Rasterized image should have correct dimensions")
      .isInstanceOf(HiDPIImage::class.java)
      .extracting("width", "height", "userWidth", "userHeight", "scale")
      .containsExactly(128, 128, 64, 64, 2.0)
    assertThat(rendererEvents).`as`("Renderer should receive 'Load' and 'Render' events").hasSize(2).satisfiesExactly(
      {
        assertThat(it).isEqualTo(AdaptiveImageRendererEvent.Loaded(
          dimensions = ImageDimensions(100f, IDUnit.PX, 100f, IDUnit.PX, 100f, 100f),
          vector = true
        ))
      },
      {
        assertThat(it).isEqualTo(AdaptiveImageRendererEvent.Rasterized(
          dimensions = ImageDimensions(100f, IDUnit.PX, 100f, IDUnit.PX, 100f, 100f),
          vector = true
        ))
      }
    )
  }

  /**
   * Custom runner that disposes testDisposable inside the TestScope.runTest {} coroutine
   * Required to gracefully cancel supervisor job in CachingAdaptiveImageManager
   * Otherwise tests fail with timeout waiting for that job to finish
   */
  private fun runTest(testBody: suspend TestScope.() -> Unit) {
    testDisposable = Disposer.newDisposable("testDisposable")
    testScope.runTest {
      try {
        testBody()
      } finally {
        Disposer.dispose(testDisposable)
      }
    }
  }

  private fun createAdaptiveImageManager(
    contentLoader: suspend (AdaptiveImageSource) -> DataWithMimeType = { getImageSourceData(it) },
    svgRasterizer: (SVGRasterizationConfig) -> RasterizedVectorImage = ::rasterizeSVGImage,
    sourceResolver: (AdaptiveImageOrigin) -> AdaptiveImageSource? = ::adaptiveImageOriginToSource,
    unloadListener: ((Unloadable<*,*>) -> Unit)? = null,
    cacheSize: Long = 1024L * 1024,
  ): CachingAdaptiveImageManager {
    val mgr = CachingAdaptiveImageManager(
      coroutineScope = testScope,
      contentLoader = contentLoader,
      svgRasterizer = svgRasterizer,
      sourceResolver = sourceResolver,
      unloadListener = unloadListener,
      timeProvider = { Milliseconds(testScope.currentTime) },
      maxSize = cacheSize,
    )
    Disposer.register(testDisposable, mgr)
    return mgr
  }

  companion object {
    private fun generateDataUrl(content: ByteArray, contentType: String) = "data:${contentType};base64,${Base64.getEncoder().encodeToString(content)}"

    private fun generateSvgDataUrl(content: String) = generateDataUrl(content.toByteArray(), "image/svg+xml")

    private fun generatePngDataUrl(content: ByteArray) = generateDataUrl(content, "image/png")

    val TEST_SVG_GREEN = """
      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100" width="100" height="100">
            <path d="M30,76q6-14,13-26q6-12,14-23q8-12,13-17q3-4,6-6q1-1,5-2q8-1,12-1q1,0,1,1q0,1-1,2q-13,11-27,33q-14,21-24,44q-4,9-5,11q-1,2-9,2q-5,0-6-1q-1-1-5-6q-5-8-12-15q-3-4-3-6q0-2,4-5q3-2,6-2q3,0,8,3q5,4,10,14z" 
              fill="green"
            />
      </svg>
    """.trimIndent()

    val TEST_SVG_RED = """
      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100" width="200" height="200">
            <path d="M30,76q6-14,13-26q6-12,14-23q8-12,13-17q3-4,6-6q1-1,5-2q8-1,12-1q1,0,1,1q0,1-1,2q-13,11-27,33q-14,21-24,44q-4,9-5,11q-1,2-9,2q-5,0-6-1q-1-1-5-6q-5-8-12-15q-3-4-3-6q0-2,4-5q3-2,6-2q3,0,8,3q5,4,10,14z" 
              fill="red"
            />
      </svg>
    """.trimIndent()

    fun generateTestPng(fillColor: Color = Color.BLACK): ByteArray {
      val img = BufferedImage(100, 100, BufferedImage.TYPE_4BYTE_ABGR)
      img.createGraphics().also {
        it.stroke = BasicStroke(3.0f)
        it.color = Color.RED
        it.fill2DRoundRect(Rectangle(25, 25, 50, 50), 6.0, fillColor)
      }
      val out = ByteArrayOutputStream()
      ImageIO.write(img, "png", out)
      return out.toByteArray()
    }
  }
}

