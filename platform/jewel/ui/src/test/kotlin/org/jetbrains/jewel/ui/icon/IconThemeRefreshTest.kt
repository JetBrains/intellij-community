// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.icon

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
import com.intellij.platform.icons.impl.rendering.layers.IconLayerManager
import com.intellij.platform.icons.impl.rendering.layers.IconLayerRenderer
import com.intellij.platform.icons.layers.IconLayer
import com.intellij.platform.icons.modifiers.IconModifier
import com.intellij.platform.icons.rendering.IconRenderer
import com.intellij.platform.icons.rendering.IconRendererManager
import com.intellij.platform.icons.rendering.ImageModifiers
import com.intellij.platform.icons.rendering.MutableIconUpdateFlow
import com.intellij.platform.icons.rendering.RenderingContext
import com.intellij.platform.icons.rendering.ThemeContext
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import org.jetbrains.jewel.BasicJewelUiTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * A Compose icon caches its drawing. So a change of the theme digest alone does not paint it again. An update of its
 * update flow paints it again, and the paint rebuilds the layer renderers once for the new digest. The IntelliJ
 * renderer manager gives its update flows an update after each LaF change for this reason.
 */
internal class IconThemeRefreshTest : BasicJewelUiTest() {
    private var previousManager: IconRendererManager? = null
    private var previousLayerManager: IconLayerManager? = null
    private var previousIconManager: IconManager? = null
    private val theme = MutableTheme()
    private val manager = CountingRendererManager(theme)

    @Before
    fun setUp() {
        previousManager = runCatching { IconRendererManager.getInstance() }.getOrNull()
        previousLayerManager = runCatching { IconLayerManager.getInstance() }.getOrNull()
        previousIconManager = runCatching { IconManager.getInstance() }.getOrNull()
        IconRendererManager.activate(manager)
        IconManager.activate(TestIconManager())
    }

    @After
    fun tearDown() {
        previousManager?.let { IconRendererManager.activate(it) }
        previousLayerManager?.let { IconLayerManager.setInstance(it) }
        previousIconManager?.let { IconManager.activate(it) }
    }

    @Test
    fun `a theme change paints a Compose icon again only after an update`() {
        val flow = SharedUpdateFlow()
        val renderer = manager.createRenderer(squareDescriptor(), manager.createRenderingContext(flow, null))
        assertEquals("the first build", 1, manager.layerBuilds.get())

        runComposeTest({ IconBox(renderer, flow) }) {
            waitForIdle()
            assertEquals("the first paint", 1, manager.layerBuilds.get())

            theme.digest = "second"
            waitForIdle()
            assertEquals("a digest change alone does not paint the icon", 1, manager.layerBuilds.get())

            runOnIdle { flow.triggerUpdate() }
            waitForIdle()
            assertEquals("the update paints the icon with the new digest", 2, manager.layerBuilds.get())

            runOnIdle { flow.triggerUpdate() }
            waitForIdle()
            assertEquals("an update without a digest change does not rebuild", 2, manager.layerBuilds.get())
        }
    }

    @Composable
    private fun IconBox(renderer: IconRenderer, flow: MutableIconUpdateFlow) {
        Box(Modifier.iconRender(renderer, 1f, null, flow))
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

    private class MutableTheme : ThemeContext {
        @Volatile var digest: String = "first"

        override fun digest(): String = digest
    }

    private class CountingRendererManager(private val theme: ThemeContext) : DefaultIconRendererManager() {
        private val imageProvider = ComposeImageResourceProvider()
        val layerBuilds = AtomicInteger()

        override fun createUpdateFlow(
            scope: CoroutineScope?,
            onUpdate: (suspend (Int) -> Unit)?,
        ): MutableIconUpdateFlow = SharedUpdateFlow()

        override fun createRenderingContext(
            updateFlow: MutableIconUpdateFlow,
            defaultImageModifiers: ImageModifiers?,
        ): RenderingContext =
            DefaultRenderingContext(updateFlow, defaultImageModifiers as? DefaultImageModifiers, theme, imageProvider)

        override fun createRenderer(layer: IconLayer, renderingContext: RenderingContext): IconLayerRenderer {
            layerBuilds.incrementAndGet()
            return super.createRenderer(layer, renderingContext)
        }
    }

    private class SharedUpdateFlow : MutableIconUpdateFlow {
        private val updates = MutableSharedFlow<Int>(extraBufferCapacity = 16)
        private val counter = AtomicInteger()

        override fun triggerUpdate() {
            updates.tryEmit(counter.incrementAndGet())
        }

        override fun triggerDelayedUpdate(delay: Long) {
            triggerUpdate()
        }

        override suspend fun collect(collector: FlowCollector<Int>) = updates.collect(collector)

        override fun collectDynamic(flow: Flow<IconDescriptor>, handler: (IconDescriptor) -> Unit) = Unit
    }

    /** Only provides the default units and modifier factories that the renderers need. */
    private class TestIconManager : DefaultIconManager() {
        override fun getResolverService(): DeferredIconResolverService = throw UnsupportedOperationException()

        override suspend fun sendDeferredNotifications(id: IconIdentifier, result: IconDescriptor) =
            throw UnsupportedOperationException()

        override fun markDeferredIconUnused(id: IconIdentifier) = throw UnsupportedOperationException()

        override fun iconDescriptor(designer: IconDesigner.() -> Unit): IconDescriptor =
            throw UnsupportedOperationException()
    }
}
