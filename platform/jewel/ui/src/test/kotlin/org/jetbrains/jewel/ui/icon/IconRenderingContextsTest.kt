// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.ui.icon

import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.impl.rendering.DefaultIconRendererManager
import com.intellij.platform.icons.impl.rendering.DefaultImageModifiers
import com.intellij.platform.icons.impl.rendering.DefaultRenderingContext
import com.intellij.platform.icons.impl.rendering.layers.IconLayerManager
import com.intellij.platform.icons.rendering.IconRendererManager
import com.intellij.platform.icons.rendering.ImageModifiers
import com.intellij.platform.icons.rendering.MutableIconUpdateFlow
import com.intellij.platform.icons.rendering.RenderingContext
import com.intellij.platform.icons.rendering.ThemeContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * The dark state that Jewel's `Icon(IconDescriptor)` gives its rendering context: the icons follow the theme of the
 * renderer manager if it has the dark state of the Jewel theme, and get an explicit dark state only if it differs.
 */
internal class IconRenderingContextsTest {
    private var previousManager: IconRendererManager? = null
    private var previousLayerManager: IconLayerManager? = null

    @Before
    fun setUp() {
        previousManager = runCatching { IconRendererManager.getInstance() }.getOrNull()
        previousLayerManager = runCatching { IconLayerManager.getInstance() }.getOrNull()
    }

    @After
    fun tearDown() {
        previousManager?.let { IconRendererManager.activate(it) }
        previousLayerManager?.let { IconLayerManager.setInstance(it) }
    }

    @Test
    fun `the icons follow a theme with the same dark state`() {
        IconRendererManager.activate(TestRendererManager(DarkTheme(dark = true)))
        val modifiers = modifiersOf(createIconRenderingContext(NoUpdates, isDark = true))
        assertNull(modifiers.darkOverride)
        assertEquals(true, modifiers.isDark)
    }

    @Test
    fun `a different dark state of the Jewel theme is an explicit dark state`() {
        IconRendererManager.activate(TestRendererManager(DarkTheme(dark = false)))
        val dark = modifiersOf(createIconRenderingContext(NoUpdates, isDark = true))
        assertEquals(true, dark.darkOverride)
        assertEquals(true, dark.isDark)

        IconRendererManager.activate(TestRendererManager(DarkTheme(dark = true)))
        val light = modifiersOf(createIconRenderingContext(NoUpdates, isDark = false))
        assertEquals(false, light.darkOverride)
        assertEquals(false, light.isDark)
    }

    @Test
    fun `a theme without a dark state uses the dark state of the Jewel theme`() {
        IconRendererManager.activate(TestRendererManager(ThemeContext.None))
        for (isDark in listOf(true, false)) {
            val modifiers = modifiersOf(createIconRenderingContext(NoUpdates, isDark))
            assertNull(modifiers.darkOverride)
            assertEquals(isDark, modifiers.isDark)
        }
    }

    private fun modifiersOf(context: RenderingContext): DefaultImageModifiers =
        context.defaultImageModifiers as DefaultImageModifiers

    private class DarkTheme(private val dark: Boolean) : ThemeContext {
        override fun digest(): String = "dark=$dark"

        override fun isDark(): Boolean = dark
    }

    private class TestRendererManager(private val theme: ThemeContext) : DefaultIconRendererManager() {
        private val imageProvider = ComposeImageResourceProvider()

        override fun createUpdateFlow(
            scope: CoroutineScope?,
            onUpdate: (suspend (Int) -> Unit)?,
        ): MutableIconUpdateFlow = NoUpdates

        override fun createRenderingContext(
            updateFlow: MutableIconUpdateFlow,
            defaultImageModifiers: ImageModifiers?,
        ): RenderingContext =
            DefaultRenderingContext(updateFlow, defaultImageModifiers as? DefaultImageModifiers, theme, imageProvider)
    }

    private object NoUpdates : MutableIconUpdateFlow {
        override fun triggerUpdate() = Unit

        override fun triggerDelayedUpdate(delay: Long) = Unit

        override suspend fun collect(collector: FlowCollector<Int>) = Unit

        override fun collectDynamic(flow: Flow<IconDescriptor>, handler: (IconDescriptor) -> Unit) = Unit
    }
}
