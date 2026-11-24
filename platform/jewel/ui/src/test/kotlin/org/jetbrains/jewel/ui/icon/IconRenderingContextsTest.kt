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
 * renderer manager in a Jewel theme that follows the IDE theme, and get an explicit dark state in any other Jewel
 * theme.
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
    fun `in a Jewel theme that follows the IDE theme, the icons follow the theme`() {
        for (themeDark in listOf(true, false)) {
            IconRendererManager.activate(TestRendererManager(DarkTheme(dark = themeDark)))
            for (isDark in listOf(true, false)) {
                // e.g. the 20 ms before the bridge reads a theme switch: the theme of the IDE wins
                val modifiers = modifiersOf(createIconRenderingContext(NoUpdates, isDark, forcedDark = null))
                assertNull(modifiers.forcedDark)
                assertEquals(isDark, modifiers.isDark)
            }
        }
    }

    /**
     * A fixed `IntUiTheme(isDark = true)` keeps its dark artwork when the IDE switches to a light theme. The dark state
     * must not depend on the theme of the IDE when the context was created.
     */
    @Test
    fun `in a fixed Jewel theme, the icons have its dark state whatever the theme of the IDE is`() {
        for (themeDark in listOf(true, false)) {
            IconRendererManager.activate(TestRendererManager(DarkTheme(dark = themeDark)))
            for (isDark in listOf(true, false)) {
                val modifiers = modifiersOf(createIconRenderingContext(NoUpdates, isDark, forcedDark = isDark))
                assertEquals(isDark, modifiers.forcedDark)
                assertEquals(isDark, modifiers.isDark)
            }
        }
    }

    @Test
    fun `a theme without a dark state uses the dark state of the Jewel theme`() {
        IconRendererManager.activate(TestRendererManager(ThemeContext.None))
        for (isDark in listOf(true, false)) {
            assertEquals(isDark, modifiersOf(createIconRenderingContext(NoUpdates, isDark, forcedDark = null)).isDark)
            assertEquals(isDark, modifiersOf(createIconRenderingContext(NoUpdates, isDark, forcedDark = isDark)).isDark)
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
