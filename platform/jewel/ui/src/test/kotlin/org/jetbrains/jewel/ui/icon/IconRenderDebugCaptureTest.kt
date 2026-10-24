// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.icon

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.IconIdentifier
import com.intellij.platform.icons.IconManager
import com.intellij.platform.icons.design.IconDesigner
import com.intellij.platform.icons.impl.DefaultIconManager
import com.intellij.platform.icons.impl.DefaultLayeredIconDescriptor
import com.intellij.platform.icons.impl.DefaultUnitsFactory
import com.intellij.platform.icons.impl.DeferredIconResolverService
import com.intellij.platform.icons.impl.layers.ShapeIconLayer
import com.intellij.platform.icons.impl.rendering.DefaultIconRendererManager
import com.intellij.platform.icons.impl.rendering.DefaultImageModifiers
import com.intellij.platform.icons.impl.rendering.DefaultRenderingContext
import com.intellij.platform.icons.impl.rendering.debug.IconRenderDebugger
import com.intellij.platform.icons.impl.rendering.debug.RenderCapture
import com.intellij.platform.icons.impl.rendering.layers.ShapeIconLayerRenderer
import com.intellij.platform.icons.modifiers.IconModifier
import com.intellij.platform.icons.rendering.IconRenderer
import com.intellij.platform.icons.rendering.IconRendererManager
import com.intellij.platform.icons.rendering.ImageModifiers
import com.intellij.platform.icons.rendering.MutableIconUpdateFlow
import com.intellij.platform.icons.rendering.RenderingContext
import com.intellij.platform.icons.rendering.ThemeContext
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import org.jetbrains.jewel.BasicJewelUiTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The Compose draw of an icon renderer (`Modifier.iconRender`, used by Jewel's `Icon(IconDescriptor)`) is a capture
 * root of the icon render debugger, and redraw requests make cached Compose drawings reach an open capture.
 */
internal class IconRenderDebugCaptureTest : BasicJewelUiTest() {
    private var previousManager: IconRendererManager? = null
    private var previousIconManager: IconManager? = null
    private val handles = ArrayList<AutoCloseable>()
    private val captures = CopyOnWriteArrayList<RenderCapture>()

    @Before
    fun setUp() {
        previousManager = runCatching { IconRendererManager.getInstance() }.getOrNull()
        previousIconManager = runCatching { IconManager.getInstance() }.getOrNull()
        IconRendererManager.activate(TestRendererManager())
        IconManager.activate(TestIconManager())
        IconRenderDebugger.clearAll()
    }

    @After
    fun tearDown() {
        handles.asReversed().forEach { it.close() }
        IconRenderDebugger.clearAll()
        previousManager?.let { IconRendererManager.activate(it) }
        previousIconManager?.let { IconManager.activate(it) }
    }

    @Test
    fun `compose draw of an icon renderer is captured with its renderer tree and bounds`() {
        val descriptor = squareDescriptor()
        val renderer = createRenderer(descriptor)
        handles.add(IconRenderDebugger.capture({ true }) { captures.add(it) })

        runComposeTest({ IconBox(renderer, offsetX = 10, offsetY = 20) }) {
            waitForIdle()

            val capture = captures.lastOrNull()
            assertTrue("expected a captured Compose draw", capture != null)
            capture!!
            assertTrue(capture.isCompose)
            assertSame(renderer, capture.iconRenderer)
            assertSame(descriptor, capture.descriptor)
            // no LocalComponent in a plain test composition
            assertNull(capture.paintTarget)
            val info = capture.composePaintInfo!!
            assertEquals(2f, info.density)
            assertEquals(20f, info.x)
            assertEquals(40f, info.y)
            assertEquals(16, info.finalWidth)
            assertEquals(16, info.finalHeight)
            assertEquals(16f, info.width)
            assertEquals(16f, info.height)
            assertTrue(info.contains(25f, 45f))
            assertFalse(info.contains(5f, 5f))

            val root = capture.roots.single()
            assertSame(renderer, root.renderer)
            assertTrue(root.children.single().renderer is ShapeIconLayerRenderer)
        }
    }

    @Test
    fun `redraw request makes a cached compose drawing reach a capture opened later`() {
        val renderer = createRenderer(squareDescriptor())

        runComposeTest({ IconBox(renderer, offsetX = 0, offsetY = 0) }) {
            waitForIdle()
            // drawn before any capture existed: nothing recorded, and Compose keeps the drawing
            handles.add(IconRenderDebugger.capture({ true }) { captures.add(it) })
            waitForIdle()
            assertTrue("nothing redraws the icon by itself", captures.isEmpty())

            runOnIdle { IconRenderDebugger.requestRedraw(null) }
            waitForIdle()

            assertTrue("expected a capture after the redraw request", captures.isNotEmpty())
            assertSame(renderer, captures.last().iconRenderer)
        }
    }

    @Test
    fun `inactive debugger records nothing`() {
        val renderer = createRenderer(squareDescriptor())
        var drawn by mutableStateOf(false)
        runComposeTest({
            drawn = true
            IconBox(renderer, offsetX = 0, offsetY = 0)
        }) {
            waitForIdle()
            assertTrue(drawn)
            assertFalse(IconRenderDebugger.isActive)
            assertTrue(captures.isEmpty())
        }
    }

    @Composable
    private fun IconBox(renderer: IconRenderer, offsetX: Int, offsetY: Int) {
        CompositionLocalProvider(LocalDensity provides Density(2f)) {
            val flow = remember { IconRendererManager.createUpdateFlow(null) }
            Box(Modifier.offset(offsetX.dp, offsetY.dp).iconRender(renderer, 2f, null, flow))
        }
    }

    private fun squareDescriptor(): IconDescriptor =
        DefaultLayeredIconDescriptor(
            listOf(
                ShapeIconLayer(
                    DefaultUnitsFactory.sRGB(0f, 0f, 0f, 1f),
                    DefaultUnitsFactory.rectangle(DefaultUnitsFactory.dp(8.0), DefaultUnitsFactory.dp(8.0)),
                    IconModifier,
                )
            )
        )

    private fun createRenderer(descriptor: IconDescriptor): IconRenderer {
        val manager = IconRendererManager.getInstance()
        return manager.createRenderer(descriptor, manager.createRenderingContext(manager.createUpdateFlow(null), null))
    }

    private class TestRendererManager : DefaultIconRendererManager() {
        private val imageProvider = ComposeImageResourceProvider()

        override fun createUpdateFlow(
            scope: CoroutineScope?,
            onUpdate: (suspend (Int) -> Unit)?,
        ): MutableIconUpdateFlow = NoUpdates

        override fun createRenderingContext(
            updateFlow: MutableIconUpdateFlow,
            defaultImageModifiers: ImageModifiers?,
        ): RenderingContext =
            DefaultRenderingContext(
                updateFlow,
                defaultImageModifiers as? DefaultImageModifiers,
                ThemeContext.None,
                imageProvider,
            )
    }

    /** Only provides the default units/modifier factories the renderers need. */
    private class TestIconManager : DefaultIconManager() {
        override fun getResolverService(): DeferredIconResolverService = throw UnsupportedOperationException()

        override suspend fun sendDeferredNotifications(id: IconIdentifier, result: IconDescriptor) =
            throw UnsupportedOperationException()

        override fun markDeferredIconUnused(id: IconIdentifier) = throw UnsupportedOperationException()

        override fun iconDescriptor(designer: IconDesigner.() -> Unit): IconDescriptor =
            throw UnsupportedOperationException()
    }

    private object NoUpdates : MutableIconUpdateFlow {
        override fun triggerUpdate() = Unit

        override fun triggerDelayedUpdate(delay: Long) = Unit

        override suspend fun collect(collector: FlowCollector<Int>) = Unit

        override fun collectDynamic(flow: Flow<IconDescriptor>, handler: (IconDescriptor) -> Unit) = Unit
    }
}
