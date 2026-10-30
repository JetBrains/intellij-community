package org.jetbrains.jewel.ui.painter

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import org.jetbrains.jewel.intui.standalone.theme.IntUiTheme
import org.junit.Rule
import org.junit.Test

class ResourcePainterProviderTest {
    @get:Rule val rule = createComposeRule()

    private val classLoader = ResourcePainterProviderTest::class.java.classLoader

    @Test
    fun `providers of the same SVG share its decoded painter`() {
        lateinit var first: Painter
        lateinit var second: Painter
        rule.setContent {
            IntUiTheme {
                // rememberResourcePainterProvider creates a provider per composition (each checkbox, each icon of a
                // list row): the SVG must not be parsed again for each of them.
                first = ResourcePainterProvider(ICON, classLoader).getPainter().value
                second = ResourcePainterProvider(ICON, classLoader).getPainter().value
            }
        }
        rule.waitForIdle()

        assertSame(first, second)
    }

    @Test
    fun `another density decodes another painter`() {
        lateinit var normal: Painter
        lateinit var dense: Painter
        rule.setContent {
            IntUiTheme {
                normal = ResourcePainterProvider(ICON, classLoader).getPainter().value
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density * 2)) {
                    dense = ResourcePainterProvider(ICON, classLoader).getPainter().value
                }
            }
        }
        rule.waitForIdle()

        assertNotSame(normal, dense)
    }

    private companion object {
        const val ICON = "drawables/test_icon.svg"
    }
}
