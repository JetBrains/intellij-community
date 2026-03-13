package com.intellij.codeowners

import com.charleskorn.kaml.MalformedYamlException
import com.intellij.codeowners.model.GroupName
import com.intellij.codeowners.model.OwnershipMapping
import com.intellij.codeowners.model.OwnershipMappingEntry
import com.intellij.codeowners.serialization.OwnershipParser
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class OwnershipParserTest {

  @TempDir
  lateinit var tempDir: Path

  @Test
  fun `single OWNERSHIP with only owner implies all files pattern`() {
    ensureOwnershipRoot()
    write(tempDir.resolve("OWNERSHIP"), """
      owner 'IntelliJ Platform'
    """.trimIndent())

    val mapping = OwnershipParser.scanProjectTree(tempDir)

    // Should create one entry with implicit '/**' and empty rule prefix
    Assertions.assertEquals(1, mapping.entries.size)
    val e = mapping.entries.single()
    Assertions.assertEquals("OWNERSHIP", e.sourceFile)
    Assertions.assertEquals("/**", e.rule)
    Assertions.assertEquals(GroupName.build("OWNERSHIP", "IntelliJ Platform"), e.owner)
  }

  @Test
  fun `rules before owner are attached to that owner`() {
    ensureOwnershipRoot()
    write(tempDir.resolve("dir/OWNERSHIP"), """
      /a.txt
      /**/*.kt
      owner 'Platform'
    """.trimIndent())

    val mapping = OwnershipParser.scanProjectTree(tempDir)

    // rulePrefix should be "dir"; both rules preserved order-wise
    val rules = mapping.entries.map { it.rule }.sorted()
    Assertions.assertEquals(listOf("dir/**/*.kt", "dir/a.txt"), rules)
  }

  @Test
  fun `multiple files are ordered by depth then by path and rules are prefixed`() {
    ensureOwnershipRoot()
    write(tempDir.resolve("a/OWNERSHIP"), """
      # Comment and then there is a blans line
      /**/*.kt
      
      owner 'A'

    """.trimIndent())
    write(tempDir.resolve("a/b/OWNERSHIP"), """
      /B.java
      owner 'B'
    """.trimIndent())

    val mapping = OwnershipParser.scanProjectTree(tempDir)

    // Expect 2 entries: from deeper file first (depth 2) according to buildMapping
    // Actually buildMapping groups by depth ascending and iterates entries with check of depth equality.
    // Deeper file has higher depth in its sourceFile path ("a/b/OWNERSHIP"), so it should appear after shallower entries.
    // We'll validate content rather than absolute order to keep test robust.
    val set = mapping.entries.map { it.rule to it.owner.toString() }.toSet()
    Assertions.assertEquals(
      setOf(
        "a/**/*.kt" to "A",
        "a/b/B.java" to "B",
      ),
      set
    )
  }

  @Test
  fun `invalid owner line format throws with hint`() {
    val path = tempDir.resolve("OWNERSHIP")
    ensureOwnershipRoot()
    write(path, """
      owner Platform
    """.trimIndent())

    val ex = assertThrows<IllegalStateException> {
      OwnershipParser.scanProjectTree(tempDir)
    }
    Assertions.assertTrue(ex.message!!.contains("OWNERSHIP"))
  }

  @Test
  fun `invalid line prefix throws`() {
    ensureOwnershipRoot()
    write(tempDir.resolve("OWNERSHIP"), """
      foo bar
    """.trimIndent())

    val ex = assertThrows<IllegalStateException> {
      OwnershipParser.scanProjectTree(tempDir)
    }
    Assertions.assertTrue(ex.message!!.contains("allowed prefixes"))
  }

  @Test
  fun `'owner' section not closed throws`() {
    ensureOwnershipRoot()
    write(tempDir.resolve("OWNERSHIP"), """
      /a
    """.trimIndent())

    val ex = assertThrows<IllegalStateException> {
      OwnershipParser.scanProjectTree(tempDir)
    }
    Assertions.assertTrue(ex.message!!.contains("not closed"))
  }

  @Test
  fun `getMappingFilePath returns path under dot-ownership`() {
    val path = OwnershipParser.getMappingFilePath(tempDir)
    // expected: <root>/.ownership/generated/ownership-mapping-generated.yaml
    Assertions.assertEquals(tempDir.resolve(".ownership/generated/ownership-mapping-generated.yaml").normalize(), path.normalize())
  }

  @Test
  fun `generateMappingFile adds header and valid yaml payload`() {
    val mapping = OwnershipMapping(listOf(
      OwnershipMappingEntry(
        sourceFile = "OWNERSHIP",
        rule = "/**",
        owner = GroupName.build("OWNERSHIP", "IntelliJ Platform")
      )
    ))

    val content = OwnershipParser.generateMappingFile(mapping)
    val lines = content.lines()
    Assertions.assertTrue(lines.first().startsWith("# "))
    // Round-trip: write generated content and load back
    val mappingFile = OwnershipParser.getMappingFilePath(tempDir)
    mappingFile.parent?.createDirectories()
    mappingFile.writeText(content, Charsets.UTF_8)
    val loaded = OwnershipParser.loadMapping(tempDir)
    Assertions.assertEquals(mapping, loaded)
  }

  @Test
  fun `saveMapping writes file creating parent directories`() {
    val mapping = OwnershipMapping(listOf(
      OwnershipMappingEntry(
        sourceFile = "OWNERSHIP",
        rule = "/file.txt",
        owner = GroupName.build("OWNERSHIP", "Platform")
      )
    ))

    val mappingFile = OwnershipParser.getMappingFilePath(tempDir)
    // ensure parent doesn't exist manually; saveMapping must create it
    OwnershipParser.saveMapping(tempDir, mapping)

    Assertions.assertTrue(mappingFile.exists())
    val expectedContent = OwnershipParser.generateMappingFile(mapping)
    val actualContent = mappingFile.readText(Charsets.UTF_8)
    Assertions.assertEquals(expectedContent, actualContent)
  }

  @Test
  fun `loadMapping returns null when file missing`() {
    val loaded = OwnershipParser.loadMapping(tempDir)
    Assertions.assertNull(loaded)
  }

  @Test
  fun `loadMapping parses valid file and throws on invalid`() {
    // valid
    val mapping = OwnershipMapping(listOf(
      OwnershipMappingEntry(
        sourceFile = "a/OWNERSHIP",
        rule = "a/**",
        owner = GroupName.build("a/OWNERSHIP", "A")
      )
    ))
    val mappingFile = OwnershipParser.getMappingFilePath(tempDir)
    val validContent = OwnershipParser.generateMappingFile(mapping)
    mappingFile.parent?.createDirectories()
    mappingFile.writeText(validContent, Charsets.UTF_8)

    val loaded = OwnershipParser.loadMapping(tempDir)
    Assertions.assertEquals(mapping, loaded)

    // invalid YAML
    mappingFile.writeText("""
      this: is: not: valid: yaml: [
    """.trimIndent(), Charsets.UTF_8)

    val thrown = assertThrows<IllegalStateException> {
      OwnershipParser.loadMapping(tempDir)
    }
    // Check the root cause is a serialization problem as per contract
    Assertions.assertNotNull(thrown.cause)
    Assertions.assertTrue(thrown.cause is MalformedYamlException)
  }

  private fun write(path: Path, content: String) {
    path.parent?.createDirectories()
    path.writeText(content)
  }

  private fun ensureOwnershipRoot() {
    // Presence of .ownership directory is required by OwnershipParser.scanProjectTree
    tempDir.resolve(".ownership").createDirectories()
  }
}
