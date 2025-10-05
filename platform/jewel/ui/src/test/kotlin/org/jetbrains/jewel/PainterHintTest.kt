package org.jetbrains.jewel

import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Density
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.jetbrains.jewel.foundation.theme.OverrideDarkMode
import org.jetbrains.jewel.ui.component.CheckboxState
import org.jetbrains.jewel.ui.painter.PainterHint
import org.jetbrains.jewel.ui.painter.PainterPathHint
import org.jetbrains.jewel.ui.painter.PainterProviderScope
import org.jetbrains.jewel.ui.painter.PainterSvgPatchHint
import org.jetbrains.jewel.ui.painter.hints.ColorBasedPaletteReplacement
import org.jetbrains.jewel.ui.painter.hints.Dark
import org.jetbrains.jewel.ui.painter.hints.HiDpi
import org.jetbrains.jewel.ui.painter.hints.PathOverride
import org.jetbrains.jewel.ui.painter.hints.Selected
import org.jetbrains.jewel.ui.painter.hints.Size
import org.jetbrains.jewel.ui.painter.hints.Stateful
import org.jetbrains.jewel.ui.painter.hints.Stroke
import org.jetbrains.jewel.ui.painter.rememberResourcePainterProvider
import org.jetbrains.jewel.ui.painter.writeToString
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Test

@Suppress("ImplicitUnitReturnType")
public class PainterHintTest : BasicJewelUiTest() {
    @Test
    public fun `empty hint should be ignored`() {
        runComposeTest({
            OverrideDarkMode(isDark = false) {
                val provider = rememberResourcePainterProvider("icons/github.svg", PainterHintTest::class.java)

                val painter1 by provider.getPainter()
                // must be ignored the None and hit cache
                val painter2 by provider.getPainter(PainterHint.None)
                // must be ignored the None and hit cache too
                val painter3 by provider.getPainter(PainterHint.None, PainterHint.None)

                assertEquals(painter1, painter2)
                assertEquals(painter3, painter2)
            }
        }) {
            awaitIdle()
        }
    }

    private class TestPainterProviderScope(
        density: Density,
        override val rawPath: String,
        override val path: String = rawPath,
        override val acceptedHints: List<PainterHint> = emptyList(),
    ) : PainterProviderScope, Density by density {
        private val documentBuilderFactory =
            DocumentBuilderFactory.newDefaultInstance().apply {
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            }

        fun applyPathHints(vararg hints: PainterHint): String {
            var result = rawPath
            hints.forEach {
                if (it !is PainterPathHint) return@forEach
                with(it) {
                    if (!canApply()) return@forEach
                    result = patch()
                }
            }
            return result
        }

        fun applyPaletteHints(svg: String, vararg hints: PainterHint): String {
            val doc = documentBuilderFactory.newDocumentBuilder().parse(svg.toByteArray().inputStream())

            hints.filterIsInstance<PainterSvgPatchHint>().onEach {
                with(it) {
                    if (!canApply()) return@onEach
                    patch(doc.documentElement)
                }
            }

            return doc.writeToString()
        }
    }

    private fun testScope(path: String, density: Float = 1f): TestPainterProviderScope =
        TestPainterProviderScope(Density(density), path)

    @Test
    public fun `dark painter hint should append suffix when isDark is true`() {
        val basePath = "icons/github.svg"
        val patchedPath = testScope(basePath).applyPathHints(Dark(true))
        assertEquals("icons/github_dark.svg", patchedPath)
    }

    @Test
    public fun `dark painter hint should not append suffix when isDark is false`() {
        val basePath = "icons/github.svg"
        val patchedPath = testScope(basePath).applyPathHints(Dark(false))
        assertEquals(basePath, patchedPath)
    }

    @Test
    public fun `override painter hint should replace path entirely`() {
        val basePath = "icons/github.svg"
        val patchedPath =
            testScope(basePath).applyPathHints(PathOverride(mapOf("icons/github.svg" to "icons/search.svg")))
        assertEquals("icons/search.svg", patchedPath)
    }

    @Test
    public fun `override painter hint should not replace path when not matched`() {
        val basePath = "icons/github.svg"
        val patchedPath =
            testScope(basePath).applyPathHints(PathOverride(mapOf("icons/settings.svg" to "icons/search.svg")))
        assertEquals(basePath, patchedPath)
    }

    @Test
    public fun `selected painter hint should append suffix when selected is true`() {
        val basePath = "icons/checkbox.svg"
        val patchedPath = testScope(basePath).applyPathHints(Selected(true))
        assertEquals("icons/checkboxSelected.svg", patchedPath)
    }

    @Test
    public fun `selected painter hint should not append suffix when selected is false`() {
        val basePath = "icons/checkbox.svg"
        val patchedPath = testScope(basePath).applyPathHints(Selected(false))
        assertEquals(basePath, patchedPath)
    }

    @Test
    public fun `size painter hint should append suffix`() {
        val basePath = "icons/github.svg"
        val patchedPath = testScope(basePath).applyPathHints(Size(20))
        assertEquals("icons/github@20x20.svg", patchedPath)
    }

    @Test
    public fun `highDpi painter hint should append suffix when isHiDpi is true`() {
        val basePath = "icons/github.png"
        val patchedPath = testScope(basePath, 2f).applyPathHints(HiDpi())
        assertEquals("icons/github@2x.png", patchedPath)
    }

    @Test
    public fun `highDpi painter hint should not append suffix when isHiDpi is false`() {
        val basePath = "icons/github.png"
        val patchedPath = testScope(basePath, 1f).applyPathHints(HiDpi())
        assertEquals(basePath, patchedPath)
    }

    @Test
    public fun `size painter hint should not append suffix for bitmap`() {
        val basePath = "icons/github.png"
        val patchedPath = testScope(basePath).applyPathHints(Size(20))
        assertEquals(basePath, patchedPath)
    }

    @Test
    public fun `size painter hint should throw with wrong width or height`() {
        val basePath = "icons/github.svg"

        Assert.assertThrows(IllegalArgumentException::class.java) { testScope(basePath).applyPathHints(Size(-1, 20)) }

        Assert.assertThrows(IllegalArgumentException::class.java) { testScope(basePath).applyPathHints(Size(20, 0)) }
    }

    @Test
    public fun `stateful painter hint should append Disabled suffix when enabled is false`() {
        val basePath = "icons/checkbox.svg"
        val state = CheckboxState.of(toggleableState = ToggleableState.Off)
        val patchedPath = testScope(basePath).applyPathHints(Stateful(state.copy(enabled = false)))
        assertEquals("icons/checkboxDisabled.svg", patchedPath)

        testScope(basePath)
            .applyPathHints(Stateful(state.copy(enabled = false, pressed = true, hovered = true, focused = true)))
            .let { assertEquals("icons/checkboxDisabled.svg", it) }
    }

    @Test
    public fun `stateful painter hint disabled state takes higher priority over other states`() {
        val basePath = "icons/checkbox.svg"
        val state = CheckboxState.of(toggleableState = ToggleableState.Off)
        val patchedPath =
            testScope(basePath)
                .applyPathHints(Stateful(state.copy(enabled = false, pressed = true, hovered = true, focused = true)))
        assertEquals("icons/checkboxDisabled.svg", patchedPath)
    }

    @Test
    public fun `stateful painter hint should append Focused suffix when focused is true`() {
        val basePath = "icons/checkbox.svg"
        val state = CheckboxState.of(toggleableState = ToggleableState.Off)
        val patchedPath = testScope(basePath).applyPathHints(Stateful(state.copy(focused = true)))
        assertEquals("icons/checkboxFocused.svg", patchedPath)
    }

    @Test
    public fun `stateful painter hint focused state takes higher priority over pressed and hovered states`() {
        val basePath = "icons/checkbox.svg"
        val state = CheckboxState.of(toggleableState = ToggleableState.Off)
        val patchedPath =
            testScope(basePath).applyPathHints(Stateful(state.copy(pressed = true, hovered = true, focused = true)))
        assertEquals("icons/checkboxFocused.svg", patchedPath)
    }

    @Test
    public fun `stateful painter hint should append Pressed suffix when pressed is true`() {
        val basePath = "icons/checkbox.svg"
        val state = CheckboxState.of(toggleableState = ToggleableState.Off)
        val patchedPath = testScope(basePath).applyPathHints(Stateful(state.copy(pressed = true)))
        assertEquals("icons/checkboxPressed.svg", patchedPath)
    }

    @Test
    public fun `stateful painter hint pressed state takes higher priority over hovered state`() {
        val basePath = "icons/checkbox.svg"
        val state = CheckboxState.of(toggleableState = ToggleableState.Off)
        val patchedPath = testScope(basePath).applyPathHints(Stateful(state.copy(pressed = true, hovered = true)))
        assertEquals("icons/checkboxPressed.svg", patchedPath)
    }

    @Test
    public fun `stateful painter hint should append Hovered suffix when hovered is true`() {
        val basePath = "icons/checkbox.svg"
        val state = CheckboxState.of(toggleableState = ToggleableState.Off)
        val patchedPath = testScope(basePath).applyPathHints(Stateful(state.copy(hovered = true)))
        assertEquals("icons/checkboxHovered.svg", patchedPath)
    }

    @Test
    public fun `stroke painter hint should append suffix when color is Specified`() {
        val basePath = "icons/rerun.svg"
        val patchedPath = testScope(basePath).applyPathHints(Stroke(Color.White))
        assertEquals("icons/rerun_stroke.svg", patchedPath)
    }

    @Test
    public fun `stroke painter hint should not append suffix when color is Unspecified`() {
        val basePath = "icons/rerun.svg"
        val patchedPath = testScope(basePath).applyPathHints(Stroke(Color.Unspecified))
        assertEquals(basePath, patchedPath)
    }

    @Test
    public fun `palette painter hint should patch colors correctly in SVG`() {
        val baseSvg =
            """
            |<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
            |    <rect fill="#000000" height="20" stroke="#000000" stroke-opacity="0.5" width="20" x="2" y="2"/>
            |    <rect fill="#00ff00" height="16" width="16" x="4" y="4"/>
            |    <rect fill="#123456" height="12" width="12" x="6" y="6"/>
            |</svg>
            """
                .trimMargin()

        val patchedSvg =
            testScope("fake_icon.svg")
                .applyPaletteHints(
                    baseSvg,
                    ColorBasedPaletteReplacement(
                        mapOf(
                            Color(0x80000000) to Color(0xFF123456),
                            Color.Black to Color.White,
                            Color.Green to Color.Red,
                        )
                    ),
                )
                .replace("\r\n", "\n")

        assertEquals(
            """
            |<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
            |    <rect fill="#ffffff" height="20" stroke="#123456" stroke-opacity="1.0" width="20" x="2" y="2"/>
            |    <rect fill="#ff0000" height="16" width="16" x="4" y="4"/>
            |    <rect fill="#123456" height="12" width="12" x="6" y="6"/>
            |</svg>
            """
                .trimMargin(),
            patchedSvg,
        )
    }
}
