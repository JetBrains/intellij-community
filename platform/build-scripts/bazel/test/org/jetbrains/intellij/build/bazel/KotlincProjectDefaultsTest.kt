// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.bazel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText

class KotlincProjectDefaultsTest {
  @JvmField
  @Rule
  val tempDir: TemporaryFolder = TemporaryFolder()

  @Test
  fun `parses current community kotlinc xml`() {
    val defaults = parseKotlincProjectDefaultsFromXml(canonicalKotlincXml())

    assertEquals("25", defaults.jvmTarget)
    assertEquals("2.3", defaults.apiVersion)
    assertEquals("2.3", defaults.languageVersion)
    assertEquals(listOf("com.intellij.openapi.util.IntellijInternalApi"), defaults.optIn)
    assertTrue(defaults.progressive)
    assertEquals("no-compatibility", defaults.jvmDefault)
    assertEquals("all", defaults.rawJvmDefault)
    assertEquals(listOf("+AllowEagerSupertypeAccessibilityChecks"), defaults.xxLanguage)
  }

  @Test
  fun `parses opt-in tokens`() {
    val defaults = parseKotlincProjectDefaultsFromXml(writeKotlincXml(
      additionalArguments = "-Xjvm-default=all -opt-in=com.example.A -opt-in=com.example.B",
    ))
    assertEquals(listOf("com.example.A", "com.example.B"), defaults.optIn)
  }

  @Test
  fun `parses progressive flag`() {
    val withFlag = parseKotlincProjectDefaultsFromXml(writeKotlincXml(
      additionalArguments = "-Xjvm-default=all -progressive",
    ))
    assertTrue(withFlag.progressive)

    val withoutFlag = parseKotlincProjectDefaultsFromXml(writeKotlincXml(
      additionalArguments = "-Xjvm-default=all",
    ))
    assertEquals(false, withoutFlag.progressive)
  }

  @Test
  fun `parses XXLanguage tokens`() {
    val defaults = parseKotlincProjectDefaultsFromXml(writeKotlincXml(
      additionalArguments = "-Xjvm-default=all -XXLanguage:+Foo -XXLanguage:-Bar",
    ))
    assertEquals(listOf("+Foo", "-Bar"), defaults.xxLanguage)
  }

  @Test
  fun `maps Xjvm-default=all to no-compatibility`() {
    val defaults = parseKotlincProjectDefaultsFromXml(writeKotlincXml(additionalArguments = "-Xjvm-default=all"))
    assertEquals("all", defaults.rawJvmDefault)
    assertEquals("no-compatibility", defaults.jvmDefault)
  }

  @Test
  fun `maps Xjvm-default=all-compatibility to enable`() {
    val defaults = parseKotlincProjectDefaultsFromXml(writeKotlincXml(additionalArguments = "-Xjvm-default=all-compatibility"))
    assertEquals("all-compatibility", defaults.rawJvmDefault)
    assertEquals("enable", defaults.jvmDefault)
  }

  @Test
  fun `maps Xjvm-default=disable to disable`() {
    val defaults = parseKotlincProjectDefaultsFromXml(writeKotlincXml(additionalArguments = "-Xjvm-default=disable"))
    assertEquals("disable", defaults.rawJvmDefault)
    assertEquals("disable", defaults.jvmDefault)
  }

  @Test
  fun `generated bzl matches snapshot`() {
    val defaults = parseKotlincProjectDefaultsFromXml(canonicalKotlincXml())

    val communityRoot = tempDir.root.toPath()
    generateCompilerOptionsBzl(communityRoot, defaults)

    val expected = expectedCompilerOptionsBzl().readText()
    val actual = communityRoot.resolve("build/compiler-options.bzl").readText()
    assertEquals(expected, actual)
  }

  @Test
  fun `unknown additionalArguments token fails with descriptive message`() {
    val ex = assertThrows(IllegalStateException::class.java) {
      parseKotlincProjectDefaultsFromXml(writeKotlincXml(additionalArguments = "-Xjvm-default=all -Xfuture-flag=enabled"))
    }
    val message = ex.message ?: error("Exception had no message")
    assertTrue(message, message.contains("-Xfuture-flag=enabled"))
    assertTrue(message, message.contains("parseKotlincProjectDefaults"))
    assertTrue(message, message.contains("CompilerOptionsBzlGenerator"))
  }

  @Test
  fun `unknown Xjvm-default value fails`() {
    val ex = assertThrows(IllegalStateException::class.java) {
      parseKotlincProjectDefaultsFromXml(writeKotlincXml(additionalArguments = "-Xjvm-default=tomorrow"))
    }
    val message = ex.message ?: error("Exception had no message")
    assertTrue(message, message.contains("tomorrow"))
    assertTrue(message, message.contains("parseKotlincProjectDefaults"))
  }

  @Test
  fun `unknown KotlinCommonCompilerArguments option fails`() {
    val xml = writeRawKotlincXml(
      """
        <?xml version="1.0" encoding="UTF-8"?>
        <project version="4">
          <component name="Kotlin2JvmCompilerArguments">
            <option name="jvmTarget" value="25" />
          </component>
          <component name="KotlinCommonCompilerArguments">
            <option name="apiVersion" value="2.3" />
            <option name="languageVersion" value="2.3" />
            <option name="someUnknownFutureOption" value="true" />
          </component>
          <component name="KotlinCompilerSettings">
            <option name="additionalArguments" value="-Xjvm-default=all" />
          </component>
          <component name="KotlinJpsPluginSettings">
            <option name="version" value="2.3.21-RC2" />
          </component>
        </project>
      """.trimIndent(),
    )
    val ex = assertThrows(IllegalStateException::class.java) { parseKotlincProjectDefaultsFromXml(xml) }
    val message = ex.message ?: error("Exception had no message")
    assertTrue(message, message.contains("someUnknownFutureOption"))
    assertTrue(message, message.contains("KotlinCommonCompilerArguments"))
  }

  @Test
  fun `unknown component name fails`() {
    val xml = writeRawKotlincXml(
      """
        <?xml version="1.0" encoding="UTF-8"?>
        <project version="4">
          <component name="Kotlin2JvmCompilerArguments">
            <option name="jvmTarget" value="25" />
          </component>
          <component name="KotlinCommonCompilerArguments">
            <option name="apiVersion" value="2.3" />
            <option name="languageVersion" value="2.3" />
          </component>
          <component name="KotlinCompilerSettings">
            <option name="additionalArguments" value="-Xjvm-default=all" />
          </component>
          <component name="KotlinJpsPluginSettings">
            <option name="version" value="2.3.21-RC2" />
          </component>
          <component name="KotlinFutureCompilerArguments">
            <option name="something" value="x" />
          </component>
        </project>
      """.trimIndent(),
    )
    val ex = assertThrows(IllegalStateException::class.java) { parseKotlincProjectDefaultsFromXml(xml) }
    val message = ex.message ?: error("Exception had no message")
    assertTrue(message, message.contains("KotlinFutureCompilerArguments"))
    assertTrue(message, message.contains("KNOWN_COMPONENTS"))
    assertTrue(message, message.contains("parseKotlincProjectDefaults"))
  }

  @Test
  fun `unknown attribute on component fails`() {
    val xml = writeRawKotlincXml(
      """
        <?xml version="1.0" encoding="UTF-8"?>
        <project version="4">
          <component name="Kotlin2JvmCompilerArguments" futureFlag="x">
            <option name="jvmTarget" value="25" />
          </component>
          <component name="KotlinCommonCompilerArguments">
            <option name="apiVersion" value="2.3" />
            <option name="languageVersion" value="2.3" />
          </component>
          <component name="KotlinCompilerSettings">
            <option name="additionalArguments" value="-Xjvm-default=all" />
          </component>
          <component name="KotlinJpsPluginSettings">
            <option name="version" value="2.3.21-RC2" />
          </component>
        </project>
      """.trimIndent(),
    )
    val ex = assertThrows(IllegalStateException::class.java) { parseKotlincProjectDefaultsFromXml(xml) }
    val message = ex.message ?: error("Exception had no message")
    assertTrue(message, message.contains("futureFlag"))
    assertTrue(message, message.contains("Kotlin2JvmCompilerArguments"))
    assertTrue(message, message.contains("parseKotlincProjectDefaults"))
  }

  @Test
  fun `unknown attribute on option fails`() {
    val xml = writeRawKotlincXml(
      """
        <?xml version="1.0" encoding="UTF-8"?>
        <project version="4">
          <component name="Kotlin2JvmCompilerArguments">
            <option name="jvmTarget" value="25" futureFlag="x" />
          </component>
          <component name="KotlinCommonCompilerArguments">
            <option name="apiVersion" value="2.3" />
            <option name="languageVersion" value="2.3" />
          </component>
          <component name="KotlinCompilerSettings">
            <option name="additionalArguments" value="-Xjvm-default=all" />
          </component>
          <component name="KotlinJpsPluginSettings">
            <option name="version" value="2.3.21-RC2" />
          </component>
        </project>
      """.trimIndent(),
    )
    val ex = assertThrows(IllegalStateException::class.java) { parseKotlincProjectDefaultsFromXml(xml) }
    val message = ex.message ?: error("Exception had no message")
    assertTrue(message, message.contains("futureFlag"))
    assertTrue(message, message.contains("jvmTarget"))
    assertTrue(message, message.contains("Kotlin2JvmCompilerArguments"))
  }

  @Test
  fun `duplicate component fails`() {
    val xml = writeRawKotlincXml(
      """
        <?xml version="1.0" encoding="UTF-8"?>
        <project version="4">
          <component name="Kotlin2JvmCompilerArguments">
            <option name="jvmTarget" value="25" />
          </component>
          <component name="Kotlin2JvmCompilerArguments">
            <option name="jvmTarget" value="21" />
          </component>
          <component name="KotlinCommonCompilerArguments">
            <option name="apiVersion" value="2.3" />
            <option name="languageVersion" value="2.3" />
          </component>
          <component name="KotlinCompilerSettings">
            <option name="additionalArguments" value="-Xjvm-default=all" />
          </component>
          <component name="KotlinJpsPluginSettings">
            <option name="version" value="2.3.21-RC2" />
          </component>
        </project>
      """.trimIndent(),
    )
    val ex = assertThrows(IllegalStateException::class.java) { parseKotlincProjectDefaultsFromXml(xml) }
    val message = ex.message ?: error("Exception had no message")
    assertTrue(message, message.contains("Duplicate"))
    assertTrue(message, message.contains("Kotlin2JvmCompilerArguments"))
  }

  @Test
  fun `duplicate option in component fails`() {
    val xml = writeRawKotlincXml(
      """
        <?xml version="1.0" encoding="UTF-8"?>
        <project version="4">
          <component name="Kotlin2JvmCompilerArguments">
            <option name="jvmTarget" value="25" />
            <option name="jvmTarget" value="21" />
          </component>
          <component name="KotlinCommonCompilerArguments">
            <option name="apiVersion" value="2.3" />
            <option name="languageVersion" value="2.3" />
          </component>
          <component name="KotlinCompilerSettings">
            <option name="additionalArguments" value="-Xjvm-default=all" />
          </component>
          <component name="KotlinJpsPluginSettings">
            <option name="version" value="2.3.21-RC2" />
          </component>
        </project>
      """.trimIndent(),
    )
    val ex = assertThrows(IllegalStateException::class.java) { parseKotlincProjectDefaultsFromXml(xml) }
    val message = ex.message ?: error("Exception had no message")
    assertTrue(message, message.contains("Duplicate"))
    assertTrue(message, message.contains("jvmTarget"))
    assertTrue(message, message.contains("Kotlin2JvmCompilerArguments"))
  }

  @Test
  fun `component without name attribute fails`() {
    val xml = writeRawKotlincXml(
      """
        <?xml version="1.0" encoding="UTF-8"?>
        <project version="4">
          <component>
            <option name="jvmTarget" value="25" />
          </component>
        </project>
      """.trimIndent(),
    )
    val ex = assertThrows(IllegalStateException::class.java) { parseKotlincProjectDefaultsFromXml(xml) }
    val message = ex.message ?: error("Exception had no message")
    assertTrue(message, message.contains("'name'"))
    assertTrue(message, message.contains("<component>"))
  }

  @Test
  fun `parses Kotlin2JsCompilerArguments without using its values`() {
    // Sanity check that the validator accepts the canonical-shape Kotlin2JsCompilerArguments component.
    val defaults = parseKotlincProjectDefaultsFromXml(writeRawKotlincXml(
      """
        <?xml version="1.0" encoding="UTF-8"?>
        <project version="4">
          <component name="Kotlin2JsCompilerArguments">
            <option name="moduleKind" value="plain" />
          </component>
          <component name="Kotlin2JvmCompilerArguments">
            <option name="jvmTarget" value="25" />
          </component>
          <component name="KotlinCommonCompilerArguments">
            <option name="apiVersion" value="2.3" />
            <option name="languageVersion" value="2.3" />
          </component>
          <component name="KotlinCompilerSettings">
            <option name="additionalArguments" value="-Xjvm-default=all" />
          </component>
          <component name="KotlinJpsPluginSettings">
            <option name="version" value="2.3.21-RC2" />
          </component>
        </project>
      """.trimIndent(),
    ))
    assertEquals("25", defaults.jvmTarget)
  }

  @Test
  fun `missing KotlinJpsPluginSettings fails`() {
    val xml = writeRawKotlincXml(
      """
        <?xml version="1.0" encoding="UTF-8"?>
        <project version="4">
          <component name="Kotlin2JvmCompilerArguments">
            <option name="jvmTarget" value="25" />
          </component>
          <component name="KotlinCommonCompilerArguments">
            <option name="apiVersion" value="2.3" />
            <option name="languageVersion" value="2.3" />
          </component>
          <component name="KotlinCompilerSettings">
            <option name="additionalArguments" value="-Xjvm-default=all" />
          </component>
        </project>
      """.trimIndent(),
    )
    val ex = assertThrows(IllegalStateException::class.java) { parseKotlincProjectDefaultsFromXml(xml) }
    val message = ex.message ?: error("Exception had no message")
    assertTrue(message, message.contains("KotlinJpsPluginSettings"))
  }

  private fun canonicalKotlincXml(): Path = testDataRoot().resolve("input/.idea/kotlinc.xml")
  private fun expectedCompilerOptionsBzl(): Path = testDataRoot().resolve("expected/compiler-options.bzl")

  private fun testDataRoot(): Path {
    val srcDir = System.getenv("TEST_SRCDIR")
                 ?: error("Missing TEST_SRCDIR env variable in bazel test environment")
    val markerRel = System.getenv(KOTLINC_DEFAULTS_TEST_DATA_MARKER_ENV)
                    ?: error("Missing $KOTLINC_DEFAULTS_TEST_DATA_MARKER_ENV env variable in bazel test environment")
    return Path.of(srcDir, markerRel).parent.normalize()
  }

  private fun writeRawKotlincXml(content: String): Path {
    val xml = tempDir.root.toPath().resolve(".idea/kotlinc.xml")
    xml.parent.createDirectories()
    xml.writeText(content)
    return xml
  }

  private fun writeKotlincXml(additionalArguments: String): Path = writeRawKotlincXml(
    """
      <?xml version="1.0" encoding="UTF-8"?>
      <project version="4">
        <component name="Kotlin2JvmCompilerArguments">
          <option name="jvmTarget" value="25" />
        </component>
        <component name="KotlinCommonCompilerArguments">
          <option name="apiVersion" value="2.3" />
          <option name="languageVersion" value="2.3" />
        </component>
        <component name="KotlinCompilerSettings">
          <option name="additionalArguments" value="$additionalArguments" />
        </component>
        <component name="KotlinJpsPluginSettings">
          <option name="version" value="2.3.21-RC2" />
        </component>
      </project>
    """.trimIndent(),
  )

  companion object {
    private const val KOTLINC_DEFAULTS_TEST_DATA_MARKER_ENV = "BAZEL_GENERATOR_KOTLINC_DEFAULTS_TEST_DATA_MARKER"
  }
}
