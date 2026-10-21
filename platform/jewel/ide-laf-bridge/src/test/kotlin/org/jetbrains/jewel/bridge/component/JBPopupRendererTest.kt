// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge.component

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.popup.ComponentPopupBuilder
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Pair as IjPair
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.replaceService
import com.intellij.ui.ScreenUtil
import com.intellij.ui.scale.JBUIScale
import io.mockk.every
import io.mockk.mockk
import java.awt.Component
import java.awt.Dimension
import java.awt.GraphicsConfiguration
import java.awt.GraphicsDevice
import java.awt.GraphicsEnvironment
import java.awt.Insets
import java.awt.Point
import java.awt.Rectangle
import java.awt.geom.AffineTransform
import java.awt.image.ColorModel
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JWindow
import javax.swing.SwingUtilities
import org.jetbrains.jewel.bridge.JewelComposePanelWrapper
import org.jetbrains.jewel.bridge.compose
import org.jetbrains.jewel.foundation.LocalComponent
import org.jetbrains.jewel.ui.component.PopupRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test

/**
 * Contract and initial-bounds unit tests for [JBPopupRenderer]. Full interactive end-to-end tests that build a real
 * `JBPopup` in a running IDE live in the `jewel-bridge-e2e-tests` lane.
 */
public class JBPopupRendererTest {
    @JvmField @Rule public val applicationRule: ApplicationRule = ApplicationRule()

    @JvmField @Rule public val disposableRule: DisposableRule = DisposableRule()

    @JvmField @Rule public val rule: ComposeContentTestRule = createComposeRule()

    @Test
    public fun `overrides every InputMode-aware Popup overload`() {
        assertEquals(
            "PopupRenderer is expected to declare exactly two InputMode-aware Popup overloads",
            2,
            inputModeAwareOverloads.size,
        )

        val inherited = inputModeAwareOverloads.filterNot { it.isOverriddenByBridge() }

        assertTrue(
            "JBPopupRenderer must declare its own override of every InputMode-aware PopupRenderer.Popup overload. An " +
                "inherited one falls through to the deprecated overloads, which drop the InputMode and end in an " +
                "error. Inherited: " +
                inherited.joinToString { it.toGenericString() },
            inherited.isEmpty(),
        )
    }

    @Test
    public fun `implements the windowShape-aware overload`() {
        val (withoutShape, windowShapeAware) = inputModeAwareOverloads.sortedBy { it.parameterCount }

        assertEquals(
            "The windowShape-aware overload is expected to take exactly one parameter more than the one without it",
            withoutShape.parameterCount + 1,
            windowShapeAware.parameterCount,
        )
        assertTrue(
            "The extra parameter is expected to be the IntSize -> Shape factory, but the overload is: " +
                windowShapeAware.toGenericString(),
            windowShapeAware.toGenericString().contains("IntSize") &&
                windowShapeAware.toGenericString().contains("java.awt.Shape"),
        )
        assertTrue(
            "JBPopupRenderer must implement the windowShape-aware overload, even though it ignores the shape: " +
                "JBPopup does not expose native window shaping",
            windowShapeAware.isOverriddenByBridge(),
        )
    }

    @Test
    public fun `popup is shown at measured screen coordinates and size on initial show`() {
        val owner = FakeShowingOwnerComponent(screenLocation = Point(300, 200))
        val showEvents = installFakePopupFactory(owner)
        val noOpDismiss: ((InputMode) -> Unit)? = null
        val disposableEffectRuns = AtomicInteger(0)
        val disposableEffectDisposals = AtomicInteger(0)
        val launchedEffectRuns = AtomicInteger(0)
        var showPopup by mutableStateOf(true)

        rule.setContent {
            CompositionLocalProvider(
                LocalComponent provides owner,
                LocalDensity provides Density(JBUIScale.sysScale(owner)),
            ) {
                Box(modifier = Modifier.size(400.dp, 300.dp)) {
                    Box(modifier = Modifier.offset(50.dp, 60.dp).size(100.dp, 40.dp)) {
                        if (showPopup) {
                            JBPopupRenderer.Popup(
                                popupPositionProvider = BottomLeftPopupPositionProvider,
                                properties = PopupProperties(),
                                onDismissRequest = noOpDismiss,
                                onPreviewKeyEvent = null,
                                onKeyEvent = null,
                                cornerSize = ZeroCornerSize,
                            ) {
                                DisposableEffect(Unit) {
                                    disposableEffectRuns.incrementAndGet()
                                    onDispose { disposableEffectDisposals.incrementAndGet() }
                                }
                                LaunchedEffect(Unit) { launchedEffectRuns.incrementAndGet() }

                                Box(modifier = Modifier.size(160.dp, 90.dp))
                            }
                        }
                    }
                }
            }
        }

        rule.waitForIdle()

        assertTrue("Expected popup.showInScreenCoordinates to be called", showEvents.isNotEmpty())
        val (initialShowPoint, initialShowSize) = showEvents.first()
        assertEquals(
            "Popup must be shown directly at its computed screen coordinates, not (0, 0)",
            Point(350, 300),
            initialShowPoint,
        )
        assertEquals(
            "Popup must have its measured content size before initial show, not 1x1",
            Dimension(160, 90),
            initialShowSize,
        )
        assertEquals("DisposableEffect in popup content must run only once", 1, disposableEffectRuns.get())
        assertEquals("LaunchedEffect in popup content must run only once", 1, launchedEffectRuns.get())
        assertEquals("DisposableEffect must not dispose while popup is open", 0, disposableEffectDisposals.get())

        showPopup = false
        rule.waitForIdle()
        assertEquals("DisposableEffect must dispose once when popup closes", 1, disposableEffectDisposals.get())
    }

    @Test
    public fun `popup inside intrinsic size parent with scrollbar does not fail intrinsic measurement`() {
        val owner = FakeShowingOwnerComponent(screenLocation = Point(300, 200))
        val showEvents = installFakePopupFactory(owner)
        val noOpDismiss: ((InputMode) -> Unit)? = null

        rule.setContent {
            CompositionLocalProvider(
                LocalComponent provides owner,
                LocalDensity provides Density(JBUIScale.sysScale(owner)),
            ) {
                Column(modifier = Modifier.width(IntrinsicSize.Max)) {
                    Box(modifier = Modifier.size(120.dp, 32.dp)) {
                        JBPopupRenderer.Popup(
                            popupPositionProvider = BottomLeftPopupPositionProvider,
                            properties = PopupProperties(),
                            onDismissRequest = noOpDismiss,
                            onPreviewKeyEvent = null,
                            onKeyEvent = null,
                            cornerSize = ZeroCornerSize,
                        ) {
                            Box(modifier = Modifier.size(180.dp, 110.dp)) {
                                VerticalScrollbar(
                                    adapter = rememberScrollbarAdapter(rememberScrollState()),
                                    modifier = Modifier.fillMaxHeight(),
                                )
                            }
                        }
                    }
                }
            }
        }

        rule.waitForIdle()

        assertTrue("Expected popup.showInScreenCoordinates to be called", showEvents.isNotEmpty())
        val (initialShowPoint, initialShowSize) = showEvents.first()
        assertEquals(Point(300, 232), initialShowPoint)
        assertEquals(Dimension(180, 110), initialShowSize)
    }

    @Test
    public fun `ComposePanel attached to displayable invisible window measures content on preferredSize`() {
        assumeFalse("ComposePanel requires a non-headless AWT environment", GraphicsEnvironment.isHeadless())
        SwingUtilities.invokeAndWait {
            var measuredSize: Dimension? = null
            val wrapper =
                compose {
                    Layout(content = { Box(modifier = Modifier.size(140.dp, 75.dp)) }) { measurables, constraints ->
                        val placeable = measurables.single().measure(constraints)
                        measuredSize = Dimension(placeable.width, placeable.height)
                        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    }
                }
                    as JewelComposePanelWrapper
            val window = JWindow()
            try {
                assertNull(
                    "Unattached ComposePanel must have null graphicsConfiguration before attachment",
                    wrapper.composePanel.graphicsConfiguration,
                )
                window.contentPane.add(wrapper)
                window.addNotify()
                assertFalse("Popup window must remain invisible during pre-measurement", window.isVisible)
                val preferredSize = wrapper.preferredSize
                assertNotNull("ComposePanel must measure its content on getPreferredSize()", measuredSize)
                assertEquals(Dimension(140, 75), preferredSize)
            } finally {
                window.dispose()
                wrapper.composePanel.dispose()
            }
        }
    }

    /**
     * The overloads declared by [PopupRenderer] itself whose `onDismissRequest` reports the dismissal `InputMode`,
     * minus the synthetic bridge the Compose compiler emits to carry the `windowShape` default value. The deprecated
     * overloads without an `InputMode` only exist for renderers written before it, which the bridge is not.
     */
    private val inputModeAwareOverloads: List<Method>
        get() =
            PopupRenderer::class.java.declaredMethods.filter {
                it.name == "Popup" && !it.isSynthetic && it.genericParameterTypes[2].typeName.contains("InputMode")
            }

    /**
     * Whether [JBPopupRenderer] declares this overload itself. A compiler-generated bridge does not count: depending on
     * the JVM default-method mode, Kotlin emits one in the implementing class for every inherited interface method, and
     * it would make an override that is missing from the source look present.
     */
    private fun Method.isOverriddenByBridge(): Boolean =
        runCatching { JBPopupRenderer::class.java.getDeclaredMethod(name, *parameterTypes) }
            .getOrNull()
            ?.let { !it.isBridge && !it.isSynthetic } == true

    private fun installFakePopupFactory(owner: Component): List<Pair<Point, Dimension>> {
        cacheZeroScreenInsets(owner.graphicsConfiguration)
        val showEvents = mutableListOf<Pair<Point, Dimension>>()
        val setSizeEvents = mutableListOf<Pair<Point, Dimension>>()
        var capturedContentComponent: JComponent? = null
        var attachContentBeforeMeasuring = false
        var preMeasureWindow: JWindow? = null

        val jbPopupInterface = Class.forName("com.intellij.openapi.ui.popup.JBPopup")

        val popupProxy =
            Proxy.newProxyInstance(jbPopupInterface.classLoader, arrayOf(jbPopupInterface)) { proxy, method, args ->
                when (method.name) {
                    "setSize" -> {
                        if (args != null && args.size == 2) {
                            val point = args[0] as Point
                            val size = args[1] as Dimension
                            setSizeEvents += Point(point) to Dimension(size)
                        }
                        null
                    }
                    "showInScreenCoordinates" -> {
                        val point = args?.get(1) as Point
                        val content = capturedContentComponent ?: error("Popup content component was not captured")
                        val shouldAttachBeforeMeasuring =
                            attachContentBeforeMeasuring ||
                                content.getClientProperty("AttachContentBeforeMeasuring") == true
                        if (shouldAttachBeforeMeasuring && !GraphicsEnvironment.isHeadless()) {
                            val window =
                                JWindow().apply {
                                    contentPane.add(content)
                                    addNotify()
                                }
                            preMeasureWindow = window
                            Disposer.register(disposableRule.disposable) { window.dispose() }
                        }
                        val currentPreferredSize = content.preferredSize
                        val effectivePoint = setSizeEvents.lastOrNull()?.first ?: point
                        val effectiveSize = setSizeEvents.lastOrNull()?.second ?: currentPreferredSize
                        showEvents += Point(effectivePoint) to Dimension(effectiveSize)
                        null
                    }
                    "cancel" -> {
                        preMeasureWindow?.dispose()
                        preMeasureWindow = null
                        null
                    }
                    "isDisposed",
                    "isVisible" -> false
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.firstOrNull()
                    "toString" -> "FakeJBPopup"
                    else -> null
                }
            }

        lateinit var builderProxy: ComponentPopupBuilder
        builderProxy =
            Proxy.newProxyInstance(
                ComponentPopupBuilder::class.java.classLoader,
                arrayOf(ComponentPopupBuilder::class.java),
            ) { proxy, method, args ->
                when (method.name) {
                    "setAttachContentBeforeMeasuring" -> {
                        attachContentBeforeMeasuring = args?.firstOrNull() as? Boolean ?: false
                        builderProxy
                    }
                    "createPopup" -> popupProxy
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.firstOrNull()
                    "toString" -> "FakeComponentPopupBuilder"
                    else -> builderProxy
                }
            } as ComponentPopupBuilder

        val factory = mockk<JBPopupFactory>(relaxed = true)
        every { factory.createComponentPopupBuilder(any(), any()) } answers
            {
                capturedContentComponent = firstArg()
                builderProxy
            }
        ApplicationManager.getApplication()
            .replaceService(JBPopupFactory::class.java, factory, disposableRule.disposable)

        return showEvents
    }

    private fun cacheZeroScreenInsets(graphicsConfiguration: GraphicsConfiguration) {
        val insetCacheField = ScreenUtil::class.java.getDeclaredField("insetCache").apply { isAccessible = true }

        @Suppress("UNCHECKED_CAST")
        val insetCache =
            insetCacheField.get(null) as? MutableMap<GraphicsConfiguration, IjPair<Insets, Long>>
                ?: error("ScreenUtil.insetCache must be enabled via ide.cache.screen.insets")
        synchronized(insetCache) {
            insetCache[graphicsConfiguration] = IjPair.create(Insets(0, 0, 0, 0), System.currentTimeMillis())
        }
        Disposer.register(disposableRule.disposable) {
            synchronized(insetCache) { insetCache.remove(graphicsConfiguration) }
        }
    }

    private companion object {
        init {
            System.setProperty("ide.cache.screen.insets", "true")
            System.setProperty("ide.insets.cache.timeout", "3600000")
        }
    }
}

private object BottomLeftPopupPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset(anchorBounds.left, anchorBounds.bottom)
}

private class FakeShowingOwnerComponent(private val screenLocation: Point) : JPanel() {
    private val configuration = FakeGraphicsConfiguration()

    init {
        setLocation(screenLocation)
    }

    override fun isShowing(): Boolean = true

    override fun getLocationOnScreen(): Point = Point(screenLocation)

    override fun getGraphicsConfiguration(): GraphicsConfiguration = configuration
}

private class FakeGraphicsConfiguration : GraphicsConfiguration() {
    private val graphicsDevice =
        object : GraphicsDevice() {
            override fun getType(): Int = TYPE_RASTER_SCREEN

            override fun getIDstring(): String = "test-screen"

            override fun getConfigurations(): Array<GraphicsConfiguration> = arrayOf(this@FakeGraphicsConfiguration)

            override fun getDefaultConfiguration(): GraphicsConfiguration = this@FakeGraphicsConfiguration
        }

    override fun getDevice(): GraphicsDevice = graphicsDevice

    override fun getColorModel(): ColorModel = ColorModel.getRGBdefault()

    override fun getColorModel(transparency: Int): ColorModel = ColorModel.getRGBdefault()

    override fun getDefaultTransform(): AffineTransform =
        if (GraphicsEnvironment.isHeadless()) {
            AffineTransform()
        } else {
            GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration.defaultTransform
        }

    override fun getNormalizingTransform(): AffineTransform = AffineTransform()

    override fun getBounds(): Rectangle = Rectangle(0, 0, 1920, 1080)
}
