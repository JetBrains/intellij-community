package org.jetbrains.jewel.scripts.bazel

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.Test

class DefaultColorPaletteGeneratorTest {
    @Test
    fun `resolveHex returns a literal hex value as-is`() {
        assertEquals("#FF0000", resolveHex("#FF0000", emptyMap()))
    }

    @Test
    fun `resolveHex follows a multi-level alias chain to its literal hex value`() {
        val colors = mapOf("a" to "b", "b" to "c", "c" to "#123456")

        assertEquals("#123456", resolveHex("a", colors))
    }

    @Test
    fun `resolveHex throws when an alias points at a key that does not exist`() {
        assertFailsWith<IllegalStateException> { resolveHex("missing-key", emptyMap()) }
    }

    @Test
    fun `resolveHex throws instead of looping forever on a cyclic alias chain`() {
        val colors = mapOf("a" to "b", "b" to "a")

        assertFailsWith<IllegalStateException> { resolveHex("a", colors) }
    }

    @Test
    fun `buildColorGroupBlocks groups exp-UI style keys by family, stripping the trailing digit`() {
        val colors = mapOf("Gray1" to "#111111", "Gray2" to "#222222")

        val blocks = buildColorGroupBlocks(colors)

        assertEquals(1, blocks.size)
        assertTrue(blocks.single().toString().contains("gray = listOf"))
        assertTrue(blocks.single().toString().contains("Color(0xFF111111)"))
        assertTrue(blocks.single().toString().contains("Color(0xFF222222)"))
    }

    @Test
    fun `buildColorGroupBlocks sorts entries numerically, not lexicographically, by their trailing index`() {
        val colors = mapOf("gray-160" to "#222222", "gray-40" to "#111111")

        val block = buildColorGroupBlocks(colors).single().toString()

        assertTrue(block.indexOf("0xFF111111") < block.indexOf("0xFF222222"))
    }

    @Test
    fun `buildColorGroupBlocks resolves aliased values before emitting them`() {
        val colors = mapOf("Gray1" to "Gray2", "Gray2" to "#222222")

        val block = buildColorGroupBlocks(colors).single().toString()

        assertTrue(block.contains("Color(0xFF222222)"))
    }

    @Test
    fun `buildColorGroupBlocks omits families that are not in the recognized color groups`() {
        assertTrue(buildColorGroupBlocks(mapOf("Foo1" to "#111111")).isEmpty())
    }

    @Test
    fun `buildRawMapBlock emits every color keyed by its original name`() {
        val block = buildRawMapBlock(mapOf("Gray1" to "#111111")).toString()

        assertTrue(block.contains("\"Gray1\""))
        assertTrue(block.contains("Color(0xFF111111)"))
    }

    @Test
    fun `readPaletteFrom emits one property per source annotated as internal`() {
        val lightFile = File.createTempFile("light-theme", ".json")
        val darkFile = File.createTempFile("dark-theme", ".json")
        try {
            lightFile.writeText("""{"colors": {"Gray1": "#111111"}}""")
            darkFile.writeText("""{"colors": {"Gray1": "#000000"}}""")

            val sources =
                listOf(
                    PaletteSource(lightFile, "Light", islands = false),
                    PaletteSource(darkFile, "Dark", islands = false),
                )

            val output = readPaletteFrom(sources, "253").toString()

            assertTrue(output.contains("object DefaultColorPalette"))
            assertTrue(output.contains("val Light:"))
            assertTrue(output.contains("val Dark:"))
            assertTrue(output.contains("isIslands = false"))
            assertTrue(output.contains("IJP 253"))
            assertTrue(output.contains("DO NOT EDIT MANUALLY"))
        } finally {
            lightFile.delete()
            darkFile.delete()
        }
    }

    @Test
    fun `readPaletteFrom marks isIslands true for islands sources`() {
        val islandsFile = File.createTempFile("islands-theme", ".json")
        try {
            islandsFile.writeText("""{"colors": {"gray-40": "#111111"}}""")

            val output =
                readPaletteFrom(listOf(PaletteSource(islandsFile, "IslandsLight", islands = true)), "253").toString()

            assertTrue(output.contains("isIslands = true"))
        } finally {
            islandsFile.delete()
        }
    }
}
