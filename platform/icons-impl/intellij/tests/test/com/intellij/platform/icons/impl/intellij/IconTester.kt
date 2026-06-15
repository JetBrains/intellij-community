// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import com.intellij.platform.icons.Icon
import com.intellij.platform.icons.IconIdentifier
import com.intellij.platform.icons.IconManager
import com.intellij.platform.icons.ImageResourceLocation
import com.intellij.platform.icons.design.Color
import com.intellij.platform.icons.design.IconDesigner
import com.intellij.platform.icons.filters.ColorFilter
import com.intellij.platform.icons.impl.DefaultIconManager
import com.intellij.platform.icons.impl.DeferredIconResolverService
import com.intellij.platform.icons.impl.intellij.design.IntelliJIconDesigner
import com.intellij.platform.icons.impl.rendering.DefaultIconRendererManager
import com.intellij.platform.icons.impl.rendering.DefaultImageModifiers
import com.intellij.platform.icons.impl.rendering.DefaultRenderingContext
import com.intellij.platform.icons.impl.rendering.DefaultScalingContext
import com.intellij.platform.icons.impl.rendering.resolve
import com.intellij.platform.icons.rendering.Dimensions
import com.intellij.platform.icons.rendering.DrawMode
import com.intellij.platform.icons.rendering.IconRenderer
import com.intellij.platform.icons.rendering.IconRendererManager
import com.intellij.platform.icons.rendering.ImageModifiers
import com.intellij.platform.icons.rendering.ImageResource
import com.intellij.platform.icons.rendering.ImageResourceProvider
import com.intellij.platform.icons.rendering.LayerPaintingContext
import com.intellij.platform.icons.rendering.MutableIconUpdateFlow
import com.intellij.platform.icons.rendering.RenderingContext
import com.intellij.platform.icons.rendering.ScalingContext
import com.intellij.platform.icons.rendering.ThemeContext
import com.intellij.platform.icons.scale.IconScale
import com.intellij.platform.icons.scale.factor
import org.junit.Assert

class IconTester {
  private val rendererManager = TestIconRendererManager()

  fun testImage(width: Int, height: Int): ImageResourceLocation {
    return TestImageResourceLocation(width, height)
  }

  fun pretendToRender(icon: Icon, scale: IconScale = factor(1f), density: Float = 1f): TestRenderResult {
    val run = createTestRun(icon)
    return run.performRender(scale, density)
  }

  fun createTestRun(icon: Icon, defaultScale: IconScale = factor(1f), defaultDensity: Float = 1f): IconTestRun {
    val renderer = rendererManager.createRenderer(
      icon,
      rendererManager.createRenderingContext(EmptyMutableIconUpdateFlow, null),
    )
    return IconTestRun(renderer, defaultScale, defaultDensity)
  }
}

class IconTestRun(
  val renderer: IconRenderer,
  val defaultScale: IconScale = factor(1f),
  val defaultDensity: Float = 1f
) {
  fun performRender(scale: IconScale = defaultScale, density: Float = defaultDensity): TestRenderResult {
    val scaling = renderer.resolve(density, scale)
    val used = renderer.calculateUsedDimensions(scaling.context)
    val result = TestRenderResult(used)
    val context = TestPaintingContext(scaling.context, result)
    renderer.render(context)
    return result
  }
}

class TestIconManager: DefaultIconManager() {
  override val resolverService: DeferredIconResolverService
    get() = throw NotImplementedError()

  override suspend fun sendDeferredNotifications(id: IconIdentifier, result: Icon) {
    throw NotImplementedError()
  }

  override fun markDeferredIconUnused(id: IconIdentifier) {
    throw NotImplementedError()
  }

  override fun icon(designer: IconDesigner.() -> Unit): Icon {
    val ijIconDesigner = IntelliJIconDesigner()
    ijIconDesigner.designer()
    return ijIconDesigner.build()
  }

}

class TestIconRendererManager: DefaultIconRendererManager() {
  override fun createUpdateFlow(
    scope: CoroutineScope?,
    onUpdate: (suspend (Int) -> Unit)?,
  ): MutableIconUpdateFlow {
    return EmptyMutableIconUpdateFlow
  }

  override fun createRenderingContext(
    updateFlow: MutableIconUpdateFlow,
    defaultImageModifiers: ImageModifiers?,
  ): RenderingContext {
    return DefaultRenderingContext(updateFlow, defaultImageModifiers as? DefaultImageModifiers, ThemeContext.None, TestImageResourceProvider)
  }
}

object TestImageResourceProvider: ImageResourceProvider {
  override fun loadImage(
    location: ImageResourceLocation,
    imageModifiers: ImageModifiers?,
  ): ImageResource {
    if (location !is TestImageResourceLocation) {
      throw IllegalArgumentException("Unsupported location: $location")
    }
    return TestImageResource(location)
  }
}

class TestImageResource(
  val location: TestImageResourceLocation
): ImageResource {
  override val width: Int = location.width
  override val height: Int = location.height
}

class TestImageResourceLocation(
  val width: Int,
  val height: Int
): ImageResourceLocation

class TestRenderResult(
  val expectedSize: Dimensions
) {
  private val images = mutableMapOf<ImageResourceLocation, MutableList<ImageReport>>()

  internal fun reportImage(image: ImageResource, x: Int, y: Int, width: Int?, height: Int?) {
    val location = (image as TestImageResource).location
    val places = images.getOrPut(location) { mutableListOf() }
    places.add(ImageReport(x, y, width, height))
  }

  fun assertSize(width: Int, height: Int) {
    Assert.assertEquals("Width of the icon should be $width, but was ${expectedSize.width}", width, expectedSize.width)
    Assert.assertEquals("Height of the icon should be $height, but was ${expectedSize.height}", height, expectedSize.height)
  }

  fun assertImage(x: Int, y: Int, width: Int, height: Int, img: ImageResourceLocation) {
    Assert.assertTrue("Image was not rendered at all.", images.containsKey(img))
    val places = images[img]!!

    Assert.assertTrue(
      "Image should render at ($x, $y) with size ($width, $height), but was at:\n${places.joinToString(",\n") { "(${it.x}, ${it.y}) with size (${it.width}, ${it.height})" }}",
                  places.any { it.x == x && it.y == y && it.width == width && it.height == height }
    )
  }

  fun clear() {
    images.clear()
  }

  class ImageReport(
    val x: Int,
    val y: Int,
    val width: Int?,
    val height: Int?
  )
}

class TestPaintingContext(
  override val scaling: ScalingContext,
  private val result: TestRenderResult,
  override val offsetX: Int = 0,
  override val offsetY: Int = 0,
  override val slotWidth: Int? = null,
  override val slotHeight: Int? = null,
) : LayerPaintingContext {
  override fun drawImage(
    image: ImageResource,
    x: Int,
    y: Int,
    width: Int?,
    height: Int?,
    srcX: Int,
    srcY: Int,
    srcWidth: Int?,
    srcHeight: Int?,
    alpha: Float,
    colorFilter: ColorFilter?,
  ) {
    result.reportImage(image, x, y, width, height)
  }

  override fun drawCircle(
    color: Color,
    x: Int,
    y: Int,
    radius: Float,
    alpha: Float,
    mode: DrawMode,
  ) {
    throw NotImplementedError()
  }

  override fun drawRect(
    color: Color,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    alpha: Float,
    mode: DrawMode,
  ) {
    throw NotImplementedError()
  }

  override fun createNestedLayer(
    x: Int?,
    y: Int?,
    slotWidth: Int?,
    slotHeight: Int?,
    scale: Float,
    overrideColorFilter: ColorFilter?,
  ): LayerPaintingContext {
    return TestPaintingContext(
      DefaultScalingContext(scaling.displayDensity, scaling.contextScale * scale),
      result,
      x ?: offsetX,
      y ?: offsetY,
      slotWidth,
      slotHeight
    )
  }

}

fun testIcons(tester: IconTester.() -> Unit) {
  IconManager.activate(TestIconManager())
  IconRendererManager.activate(TestIconRendererManager())
  val tester = IconTester()
  tester.tester()
}

private object EmptyMutableIconUpdateFlow : MutableIconUpdateFlow {
  override fun triggerUpdate() {
    // Do nothing
  }

  override fun triggerDelayedUpdate(delay: Long) {
    // Do nothing
  }

  override suspend fun collect(collector: FlowCollector<Int>) {
    // Do nothing
  }

  override fun collectDynamic(flow: Flow<Icon>, handler: (Icon) -> Unit) {
    // Do nothing
  }
}
