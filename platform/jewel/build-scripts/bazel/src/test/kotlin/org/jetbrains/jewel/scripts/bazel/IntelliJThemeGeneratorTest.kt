package org.jetbrains.jewel.scripts.bazel

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeSpec
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Test

private val testClassName = ClassName("org.jetbrains.jewel.intui.core.theme", "IntUiLightTheme")

class IntelliJThemeGeneratorTest {
    @Test
    fun `readThemeFrom emits isDark and a name suffixed with Int UI`() {
        val descriptor = IntellijThemeDescriptor(name = "Light", editorScheme = "/Light.xml", dark = false)

        val output = readThemeFrom(descriptor, testClassName, "some/path.json", "253").toString()

        assertTrue(output.contains("isDark: Boolean = false"))
        assertTrue(output.contains("\"Light (Int UI)\""))
    }

    @Test
    fun `readThemeFrom includes the source path and IJP major in the header comment`() {
        val descriptor = IntellijThemeDescriptor(name = "Light", editorScheme = "/Light.xml")

        val output = readThemeFrom(descriptor, testClassName, "some/path.json", "253").toString()

        assertTrue(output.contains("some/path.json"))
        assertTrue(output.contains("IJP 253"))
        assertTrue(output.contains("DO NOT EDIT MANUALLY"))
    }

    @Test
    fun `addColorsProperty references DefaultColorPalette Light for a light theme`() {
        val typeSpec = TypeSpec.objectBuilder(testClassName).apply { addColorsProperty(isDark = false) }.build()

        assertTrue(typeSpec.toString().contains("DefaultColorPalette.Light"))
    }

    @Test
    fun `addColorsProperty references DefaultColorPalette Dark for a dark theme`() {
        val typeSpec = TypeSpec.objectBuilder(testClassName).apply { addColorsProperty(isDark = true) }.build()

        assertTrue(typeSpec.toString().contains("DefaultColorPalette.Dark"))
    }

    @Test
    fun `addColorsProperty opts in to InternalJewelApi`() {
        val typeSpec = TypeSpec.objectBuilder(testClassName).apply { addColorsProperty(isDark = false) }.build()

        assertTrue(typeSpec.toString().contains("InternalJewelApi"))
    }

    @Test
    fun `addIconDataProperty captures string icon overrides`() {
        val descriptor =
            IntellijThemeDescriptor(
                name = "Light",
                editorScheme = "/Light.xml",
                icons = mapOf("Actions.Close" to JsonPrimitive("close.svg")),
            )

        val typeSpec = TypeSpec.objectBuilder(testClassName).apply { addIconDataProperty(descriptor) }.build()

        assertTrue(typeSpec.toString().contains("\"Actions.Close\" to \"close.svg\""))
    }

    @Test
    fun `addIconDataProperty captures nested ColorPalette string entries`() {
        val descriptor =
            IntellijThemeDescriptor(
                name = "Light",
                editorScheme = "/Light.xml",
                icons = mapOf("ColorPalette" to buildJsonObject { put("Actions.Red", JsonPrimitive("#FF0000")) }),
            )

        val typeSpec = TypeSpec.objectBuilder(testClassName).apply { addIconDataProperty(descriptor) }.build()

        assertTrue(typeSpec.toString().contains("\"Actions.Red\" to \"#FF0000\""))
    }

    @Test
    fun `addIconDataProperty ignores non-string ColorPalette entries`() {
        val descriptor =
            IntellijThemeDescriptor(
                name = "Light",
                editorScheme = "/Light.xml",
                icons = mapOf("ColorPalette" to buildJsonObject { put("Actions.Alpha", JsonPrimitive(42)) }),
            )

        val typeSpec = TypeSpec.objectBuilder(testClassName).apply { addIconDataProperty(descriptor) }.build()

        assertTrue(typeSpec.toString().contains("emptyMap()"))
    }

    @Test
    fun `addIconDataProperty captures the selection color palette`() {
        val descriptor =
            IntellijThemeDescriptor(
                name = "Light",
                editorScheme = "/Light.xml",
                iconColorsOnSelection = mapOf("Actions.Red" to 0xFF0000),
            )

        val typeSpec = TypeSpec.objectBuilder(testClassName).apply { addIconDataProperty(descriptor) }.build()

        assertTrue(typeSpec.toString().contains("\"Actions.Red\" to \"16_711_680\""))
    }

    @Test
    fun `toMapCodeBlock renders an empty map as emptyMap`() {
        assertEquals("emptyMap()", emptyMap<String, String>().toMapCodeBlock().toString())
    }

    @Test
    fun `toMapCodeBlock renders entries as a mapOf call`() {
        val block: CodeBlock = mapOf("a" to "1").toMapCodeBlock()

        assertTrue(block.toString().contains("\"a\" to \"1\""))
    }
}
