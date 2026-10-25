// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.icons.impl.intellij

import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.ImageResourceLocation
import com.intellij.platform.icons.iconDescriptor
import com.intellij.platform.icons.impl.intellij.rendering.SwingIcon
import com.intellij.platform.icons.impl.layers.ImageIconLayer
import com.intellij.platform.icons.impl.layers.LayoutIconLayer
import com.intellij.platform.icons.impl.layers.NestedIconLayer
import com.intellij.platform.icons.impl.rendering.DefaultIconRenderer
import com.intellij.platform.icons.impl.rendering.debug.ComposePaintInfo
import com.intellij.platform.icons.impl.rendering.debug.IconRenderDebugger
import com.intellij.platform.icons.impl.rendering.debug.IconRenderHit
import com.intellij.platform.icons.impl.rendering.debug.ImageResourceLocationDescriber
import com.intellij.platform.icons.impl.rendering.debug.ImageResourceLocationDescribers
import com.intellij.platform.icons.impl.rendering.debug.RenderCapture
import com.intellij.platform.icons.impl.rendering.debug.RenderNode
import com.intellij.platform.icons.impl.rendering.layers.ImageIconLayerRenderer
import com.intellij.platform.icons.impl.rendering.layers.LayoutIconLayerRenderer
import com.intellij.platform.icons.impl.rendering.layers.NestedIconLayerRenderer
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.lang.ref.WeakReference
import javax.swing.JLabel

@TestApplication
class IconRenderDebuggerTest {
  private val hits = mutableListOf<IconRenderHit>()
  private val handles = mutableListOf<AutoCloseable>()

  @BeforeEach
  fun setUp() {
    IconRenderDebugger.clearAll()
    handles.add(IconRenderDebugger.addHitListener { hits.add(it) })
  }

  @AfterEach
  fun tearDown() {
    handles.asReversed().forEach { it.close() }
    handles.clear()
    IconRenderDebugger.clearAll()
    assertFalse(IconRenderDebugger.isActive)
  }

  @Test
  fun `inactive debugger reports nothing`() {
    testIcons {
      assertFalse(IconRenderDebugger.isActive)
      pretendToRender(iconDescriptor { image(testImage(16, 16)) })
      assertTrue(hits.isEmpty())
    }
  }

  @Test
  fun `flagged renderer instance is hit once per render`() {
    testIcons {
      val icon = iconDescriptor { image(testImage(16, 16)) }
      val run = createTestRun(icon)
      IconRenderDebugger.flag(run.renderer)
      assertTrue(IconRenderDebugger.isActive)
      assertTrue(IconRenderDebugger.isFlagged(run.renderer))
      assertEquals(listOf<Any>(run.renderer), IconRenderDebugger.flaggedRenderers())

      run.performRender(density = 2f)

      assertEquals(1, IconRenderDebugger.hitCount(run.renderer))
      val hit = hits.single()
      assertSame(run.renderer, hit.renderer)
      assertSame(icon, hit.subject)
      assertEquals(2f, hit.scaling.displayDensity)
      // dimensions are in device pixels: 16px at density 2
      assertEquals(32, hit.dimensions?.width)
    }
  }

  @Test
  fun `renders inside untraced are not hit, and tracing resumes after it`() {
    testIcons {
      val run = createTestRun(iconDescriptor { image(testImage(16, 16)) })
      IconRenderDebugger.flag(run.renderer)

      IconRenderDebugger.untraced { IconRenderDebugger.untraced { run.performRender() } }
      assertEquals(0, IconRenderDebugger.hitCount(run.renderer))
      assertTrue(hits.isEmpty())

      run.performRender()
      assertEquals(1, IconRenderDebugger.hitCount(run.renderer))
    }
  }

  @Test
  fun `equal descriptor rendered by another renderer instance is not hit`() {
    testIcons {
      val image = testImage(16, 16)
      val flagged = createTestRun(iconDescriptor { image(image) })
      val other = createTestRun(iconDescriptor { image(image) })
      assertEquals(flagged.renderer.iconDescriptor, other.renderer.iconDescriptor)
      IconRenderDebugger.flag(flagged.renderer)

      other.performRender()
      assertTrue(hits.isEmpty())

      flagged.performRender()
      assertSame(flagged.renderer, hits.single().renderer)
    }
  }

  @Test
  fun `unflagged renderer is not hit`() {
    testIcons {
      val run = createTestRun(iconDescriptor { image(testImage(16, 16)) })
      IconRenderDebugger.flag(run.renderer)
      IconRenderDebugger.unflag(run.renderer)
      assertFalse(IconRenderDebugger.isActive)
      assertFalse(IconRenderDebugger.isFlagged(run.renderer))

      run.performRender()

      assertEquals(0, IconRenderDebugger.hitCount(run.renderer))
      assertTrue(hits.isEmpty())
    }
  }

  @Test
  fun `capture records the renderer tree of nested and layout layers`() {
    testIcons {
      val imageA = testImage(4, 4)
      val imageB = testImage(6, 6)
      val child = iconDescriptor { image(testImage(8, 8)) }
      val parent = iconDescriptor {
        icon(child)
        row {
          image(imageA)
          image(imageB)
        }
      }
      val painted = PaintedIcon(parent)

      val capture = painted.capture()

      assertSame(painted.component, capture.paintTarget)
      assertSame(parent, capture.descriptor)
      assertEquals(JLabel::class.java.name, capture.paintInfo.componentClass)
      val root = capture.roots.single()
      assertSame(capture.iconRenderer, root.renderer)
      assertInstanceOf(DefaultIconRenderer::class.java, root.renderer)
      assertTrue(root.isIconRenderer)
      assertSame(parent, root.subject)

      val (nestedLayer, layoutLayer) = root.children
      assertInstanceOf(NestedIconLayerRenderer::class.java, nestedLayer.renderer)
      assertInstanceOf(NestedIconLayer::class.java, nestedLayer.subject)
      val childRenderer = nestedLayer.children.single()
      assertInstanceOf(DefaultIconRenderer::class.java, childRenderer.renderer)
      assertSame(child, childRenderer.subject)
      assertInstanceOf(ImageIconLayerRenderer::class.java, childRenderer.children.single().renderer)

      assertInstanceOf(LayoutIconLayerRenderer::class.java, layoutLayer.renderer)
      assertInstanceOf(LayoutIconLayer::class.java, layoutLayer.subject)
      assertEquals(listOf(imageA, imageB), layoutLayer.children.map { (it.subject as ImageIconLayer).loader })
      assertTrue(layoutLayer.children.all { it.renderer is ImageIconLayerRenderer && it.children.isEmpty() })
    }
  }

  @Test
  fun `capture only records paints accepted by its filter`() {
    testIcons {
      val painted = PaintedIcon(iconDescriptor { image(testImage(16, 16)) })
      val captures = mutableListOf<RenderCapture>()
      handles.add(IconRenderDebugger.capture({ false }) { captures.add(it) })
      painted.paint()
      assertTrue(captures.isEmpty())
    }
  }

  @Test
  fun `closed capture records nothing`() {
    testIcons {
      val painted = PaintedIcon(iconDescriptor { image(testImage(16, 16)) })
      val captures = mutableListOf<RenderCapture>()
      IconRenderDebugger.capture({ it === painted.component }) { captures.add(it) }.close()
      assertFalse(IconRenderDebugger.isActive)
      painted.paint()
      assertTrue(captures.isEmpty())
    }
  }

  @Test
  fun `nested icon renderer found via capture is hit only for itself`() {
    testIcons {
      val image = testImage(8, 8)
      val child = iconDescriptor { image(image) }
      // the sibling is an equal descriptor, rendered by a different renderer instance
      val parent = iconDescriptor {
        row {
          icon(child)
          icon(iconDescriptor { image(image) })
        }
      }
      val painted = PaintedIcon(parent)
      val nestedRenderers = painted.capture().allNodes().filter { it.renderer is DefaultIconRenderer && it.subject == child }
      assertEquals(2, nestedRenderers.size)
      assertNotSame(nestedRenderers[0].renderer, nestedRenderers[1].renderer)

      IconRenderDebugger.flag(nestedRenderers[1].renderer)
      painted.paint()

      assertSame(nestedRenderers[1].renderer, hits.single().renderer)
      assertEquals(1, IconRenderDebugger.hitCount(nestedRenderers[1].renderer))
    }
  }

  @Test
  fun `layer renderer found via capture is hit`() {
    testIcons {
      val imageA = testImage(4, 4)
      val imageB = testImage(6, 6)
      val painted = PaintedIcon(iconDescriptor {
        row {
          image(imageA)
          image(imageB)
        }
      })
      val target = painted.capture().allNodes().single { (it.subject as? ImageIconLayer)?.loader === imageB }

      IconRenderDebugger.flag(target.renderer)
      painted.paint()

      val hit = hits.single()
      assertSame(target.renderer, hit.renderer)
      assertSame(imageB, (hit.subject as ImageIconLayer).loader)
    }
  }

  @Test
  fun `flagged renderers are not strongly retained`() {
    testIcons {
      val ref = flagUnreachableRenderer()
      val deadline = System.currentTimeMillis() + 10_000
      while (ref.get() != null && System.currentTimeMillis() < deadline) {
        System.gc()
        Thread.sleep(10)
      }
      assertNull(ref.get(), "flagged renderer was not garbage collected")
      assertTrue(IconRenderDebugger.flaggedRenderers().isEmpty())
    }
  }

  private fun IconTester.flagUnreachableRenderer(): WeakReference<Any> {
    val renderer = createTestRun(iconDescriptor { image(testImage(16, 16)) }).renderer
    IconRenderDebugger.flag(renderer)
    return WeakReference(renderer)
  }

  @Test
  fun `failing listeners and filters do not break rendering`() {
    testIcons {
      val image = testImage(16, 16)
      val painted = PaintedIcon(iconDescriptor { image(image) })
      val captures = mutableListOf<RenderCapture>()
      handles.add(IconRenderDebugger.addHitListener { throw IllegalStateException("hit listener failure") })
      handles.add(IconRenderDebugger.capture({ throw IllegalStateException("filter failure") }) { captures.add(it) })
      handles.add(IconRenderDebugger.capture({ true }) { throw IllegalStateException("capture listener failure") })
      handles.add(IconRenderDebugger.capture({ true }) { captures.add(it) })
      val run = createTestRun(iconDescriptor { image(image) })
      IconRenderDebugger.flag(run.renderer)

      run.performRender().assertImage(0, 0, 16, 16, image)
      painted.paint()

      assertEquals(1, hits.size)
      assertEquals(1, captures.size)
    }
  }

  @Test
  fun `rendering inside listeners is not traced recursively`() {
    testIcons {
      val run = createTestRun(iconDescriptor { image(testImage(16, 16)) })
      val painted = PaintedIcon(iconDescriptor { image(testImage(16, 16)) })
      var captureCalls = 0
      handles.add(IconRenderDebugger.addHitListener { run.performRender() })
      handles.add(IconRenderDebugger.capture({ true }) {
        captureCalls++
        painted.paint()
      })
      IconRenderDebugger.flag(run.renderer)

      run.performRender()
      painted.paint()

      assertEquals(1, IconRenderDebugger.hitCount(run.renderer))
      assertEquals(1, captureCalls)
    }
  }

  // ---- Compose paints ----

  @Test
  fun `compose paint starts a capture with the renderer tree and the drawn bounds`() {
    testIcons {
      val image = testImage(8, 8)
      val child = iconDescriptor { image(image) }
      val parent = iconDescriptor { icon(child) }
      val run = createTestRun(parent)
      val host = JLabel()
      val captures = mutableListOf<RenderCapture>()
      handles.add(IconRenderDebugger.capture({ it === host }) { captures.add(it) })

      run.composePaint(host, composeInfo(x = 12f, y = 30f))

      val capture = captures.single()
      assertSame(host, capture.paintTarget)
      assertSame(parent, capture.descriptor)
      assertSame(run.renderer, capture.iconRenderer)
      assertTrue(capture.isCompose)
      val info = capture.composePaintInfo!!
      assertEquals(12f, info.x)
      assertEquals(30f, info.y)
      assertTrue(info.contains(12f, 30f))
      assertTrue(info.contains(27.9f, 45.9f))
      assertFalse(info.contains(28f, 30f))
      assertFalse(info.contains(11.9f, 30f))
      // the Swing-shaped paint info is derived from the Compose one
      assertEquals(JLabel::class.java.name, capture.paintInfo.componentClass)
      assertEquals(12, capture.paintInfo.x)
      assertEquals(30, capture.paintInfo.y)
      assertEquals(2f, capture.paintInfo.density)

      val root = capture.roots.single()
      assertSame(run.renderer, root.renderer)
      assertSame(parent, root.subject)
      val nestedLayer = root.children.single()
      assertInstanceOf(NestedIconLayerRenderer::class.java, nestedLayer.renderer)
      val childNode = nestedLayer.children.single()
      assertSame(child, childNode.subject)
      assertInstanceOf(ImageIconLayerRenderer::class.java, childNode.children.single().renderer)
    }
  }

  @Test
  fun `compose paint info is only computed for accepted paints`() {
    testIcons {
      val run = createTestRun(iconDescriptor { image(testImage(16, 16)) })
      val captures = mutableListOf<RenderCapture>()
      var infoCalls = 0
      handles.add(IconRenderDebugger.capture({ false }) { captures.add(it) })

      IconRenderDebugger.traceComposePaint(run.renderer, run.renderer.iconDescriptor, JLabel(), { infoCalls++; composeInfo() }) {
        run.performRender()
      }

      assertEquals(0, infoCalls)
      assertTrue(captures.isEmpty())
    }
  }

  @Test
  fun `failing compose paint info does not break the paint`() {
    testIcons {
      val run = createTestRun(iconDescriptor { image(testImage(16, 16)) })
      val captures = mutableListOf<RenderCapture>()
      var painted = false
      handles.add(IconRenderDebugger.capture({ true }) { captures.add(it) })

      IconRenderDebugger.traceComposePaint(run.renderer, run.renderer.iconDescriptor, null, { error("no coordinates") }) {
        painted = true
        run.performRender()
      }

      assertTrue(painted)
      assertTrue(captures.isEmpty())
    }
  }

  @Test
  fun `compose rendered renderer found via capture is hit when flagged`() {
    testIcons {
      val run = createTestRun(iconDescriptor { image(testImage(16, 16)) })
      val host = JLabel()
      val captures = mutableListOf<RenderCapture>()
      IconRenderDebugger.capture({ it === host }) { captures.add(it) }.use { run.composePaint(host, composeInfo()) }
      val layerRenderer = captures.single().roots.single().children.single().renderer
      IconRenderDebugger.flag(layerRenderer)

      run.composePaint(host, composeInfo())

      assertEquals(1, IconRenderDebugger.hitCount(layerRenderer))
      assertSame(layerRenderer, hits.single().renderer)
      assertInstanceOf(ImageIconLayerRenderer::class.java, hits.single().renderer)
    }
  }

  @Test
  fun `inactive debugger is a single check on the compose path`() {
    testIcons {
      assertFalse(IconRenderDebugger.isActive)
      val run = createTestRun(iconDescriptor { image(testImage(16, 16)) })
      // what the Compose draw node does: trace only when active
      var traced = false
      if (IconRenderDebugger.isActive) {
        traced = true
        run.composePaint(null, composeInfo())
      }
      else {
        run.performRender()
      }
      assertFalse(traced)
      assertTrue(hits.isEmpty())
    }
  }

  @Test
  fun `redraw requests reach registered listeners until closed`() {
    val requests = mutableListOf<Any?>()
    val target = JLabel()
    val handle = IconRenderDebugger.addRedrawListener { requests.add(it) }
    handles.add(IconRenderDebugger.addRedrawListener { error("failing listener") })
    try {
      IconRenderDebugger.requestRedraw(target)
      IconRenderDebugger.requestRedraw(null)
    }
    finally {
      handle.close()
    }
    IconRenderDebugger.requestRedraw(target)
    assertEquals(listOf(target, null), requests)
    // redraw listeners do not activate the debugger
    assertFalse(IconRenderDebugger.isActive)
  }

  private fun composeInfo(x: Float = 0f, y: Float = 0f): ComposePaintInfo =
    ComposePaintInfo(JLabel::class.java.name, x, y, 16f, 16f, density = 2f, scale = null, finalWidth = 16, finalHeight = 16)

  private fun IconTestRun.composePaint(host: Any?, info: ComposePaintInfo) {
    IconRenderDebugger.traceComposePaint(renderer, renderer.iconDescriptor, host, { info }) { performRender() }
  }

  /** A [SwingIcon] painted into its own component, so captures can filter on that component. */
  private class PaintedIcon(descriptor: IconDescriptor) {
    val component = JLabel()
    private val icon = SwingIcon(descriptor)

    fun paint() {
      val image = BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB)
      val graphics = image.createGraphics()
      try {
        icon.paintIcon(component, graphics, 0, 0)
      }
      finally {
        graphics.dispose()
      }
    }

    fun capture(): RenderCapture {
      val captures = mutableListOf<RenderCapture>()
      IconRenderDebugger.capture({ it === component }) { captures.add(it) }.use { paint() }
      return captures.single()
    }
  }

  private fun RenderCapture.allNodes(): List<RenderNode> {
    val result = mutableListOf<RenderNode>()
    fun visit(node: RenderNode) {
      result.add(node)
      node.children.forEach(::visit)
    }
    roots.forEach(::visit)
    return result
  }

  @Test
  fun `describe falls back to class and toString without a describer`() {
    val location = UnknownLocation()
    assertNull(ImageResourceLocationDescribers.find(location))
    assertEquals(listOf("class" to UnknownLocation::class.java.name, "toString" to "unknown-location"),
                 ImageResourceLocationDescribers.describe(location))
  }

  @Test
  fun `describer lookup prefers exact class over assignable`() {
    val baseDescriber = NamedDescriber(BaseLocation::class.java, "base")
    val subDescriber = NamedDescriber(SubLocation::class.java, "sub")
    // registered base first, so an exact match must win over registration order
    handles.add(ImageResourceLocationDescribers.register(baseDescriber))
    handles.add(ImageResourceLocationDescribers.register(subDescriber))

    assertSame(subDescriber, ImageResourceLocationDescribers.find(SubLocation()))
    assertSame(baseDescriber, ImageResourceLocationDescribers.find(OtherSubLocation()))
    assertSame(baseDescriber, ImageResourceLocationDescribers.find(BaseLocation()))
    assertEquals(listOf("describer" to "sub"), ImageResourceLocationDescribers.describe(SubLocation()))
    assertEquals(listOf("describer" to "base"), ImageResourceLocationDescribers.describe(OtherSubLocation()))
  }

  @Test
  fun `closed describer is no longer found`() {
    val describer = NamedDescriber(BaseLocation::class.java, "base")
    ImageResourceLocationDescribers.register(describer).close()
    assertNull(ImageResourceLocationDescribers.find(BaseLocation()))
  }

  @Test
  fun `IntelliJ icon manager registers the module location describer`() {
    IntelliJIconManager()
    val location = ModuleImageResourceLocation("expui/general/save.svg", "com.intellij", "intellij.platform.ide")
    assertEquals(listOf("path" to "expui/general/save.svg", "pluginId" to "com.intellij", "moduleId" to "intellij.platform.ide"),
                 ImageResourceLocationDescribers.describe(location))
  }

  private class UnknownLocation : ImageResourceLocation {
    override fun toString(): String = "unknown-location"
  }

  private open class BaseLocation : ImageResourceLocation
  private class SubLocation : BaseLocation()
  private class OtherSubLocation : BaseLocation()

  private class NamedDescriber<T : ImageResourceLocation>(
    override val locationClass: Class<T>,
    private val name: String,
  ) : ImageResourceLocationDescriber<T> {
    override fun describe(location: T): List<Pair<String, String?>> = listOf("describer" to name)
  }
}
