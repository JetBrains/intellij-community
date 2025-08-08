// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation.code

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class MimeTypeTest {
    @Test
    fun `displayName should handle simple cases`() {
        assertEquals("Kotlin", MimeType.Known.KOTLIN.displayName())
        assertEquals("Java", MimeType.Known.JAVA.displayName())
        assertEquals("XML", MimeType.Known.XML.displayName())
        assertEquals("HTML", MimeType.Known.HTML.displayName())
        assertEquals("XHTML", MimeType.Known.XHTML.displayName())
        assertEquals("JSON", MimeType.Known.JSON.displayName())
        assertEquals("JSON5", MimeType.Known.JSON5.displayName())
        assertEquals("JSON Lines", MimeType.Known.JSON_LINES.displayName())
        assertEquals("Text", MimeType.Known.TEXT.displayName())
        assertEquals("Regular Expression", MimeType.Known.REGEX.displayName())
        assertEquals("Groovy", MimeType.Known.GROOVY.displayName())
        assertEquals("TOML", MimeType.Known.TOML.displayName())
        assertEquals("C", MimeType.Known.C.displayName())
        assertEquals("C++", MimeType.Known.CPP.displayName())
        assertEquals("SVG", MimeType.Known.SVG.displayName())
        assertEquals("AIDL", MimeType.Known.AIDL.displayName())
        assertEquals("SQL", MimeType.Known.SQL.displayName())
        assertEquals("Shrinker Config", MimeType.Known.PROGUARD.displayName())
        assertEquals("Properties", MimeType.Known.PROPERTIES.displayName())
        assertEquals("Protobuf", MimeType.Known.PROTO.displayName())
        assertEquals("Python", MimeType.Known.PYTHON.displayName())
        assertEquals("Dart", MimeType.Known.DART.displayName())
        assertEquals("Rust", MimeType.Known.RUST.displayName())
        assertEquals("JavaScript", MimeType.Known.JAVASCRIPT.displayName())
        assertEquals("Android Graphics Shading Language", MimeType.Known.AGSL.displayName())
        assertEquals("Shell Script", MimeType.Known.SHELL.displayName())
        assertEquals("YAML", MimeType.Known.YAML.displayName())
        assertEquals("Go", MimeType.Known.GO.displayName())
    }

    @Test
    fun `displayName should handle conditional cases`() {
        assertEquals("Gradle DSL", MimeType.Known.GRADLE_KTS.displayName())
        assertEquals("Gradle", MimeType.Known.GRADLE.displayName())
        assertEquals("Version Catalog", MimeType.Known.VERSION_CATALOG.displayName())
    }

    @Test
    fun `displayName should parse attributes correctly for XML roles`() {
        // Basic Manifest role
        assertEquals("Manifest", MimeType.Known.MANIFEST.displayName())

        // Resource role with specified folderType
        val resourceWithFolderType = MimeType("text/xml; role=resource folderType=layout")
        assertEquals("Layout", resourceWithFolderType.displayName())

        // Resource file without a folderType
        val resourceWithoutFolderType = MimeType("text/xml; role=resource")
        assertEquals("XML", resourceWithoutFolderType.displayName())

        // XML base without role
        assertEquals("XML", MimeType.Known.XML.displayName())

        // XML with unknown role
        val resourceWithUnknownRole = MimeType("text/xml; role=randomRole")
        assertEquals("XML", resourceWithoutFolderType.displayName())
    }

    @Test
    fun `fromMarkdownLanguageName should resolve expected names`() {
        assertEquals(MimeType.Known.KOTLIN, MimeType.Known.fromMarkdownLanguageName("kotlin"))
        assertEquals(MimeType.Known.KOTLIN, MimeType.Known.fromMarkdownLanguageName("kt"))
        assertEquals(MimeType.Known.KOTLIN, MimeType.Known.fromMarkdownLanguageName("kts"))

        assertEquals(MimeType.Known.JAVA, MimeType.Known.fromMarkdownLanguageName("java"))
        assertEquals(MimeType.Known.HTML, MimeType.Known.fromMarkdownLanguageName("html"))
        assertEquals(MimeType.Known.XHTML, MimeType.Known.fromMarkdownLanguageName("xhtml"))
        assertEquals(MimeType.Known.CSS, MimeType.Known.fromMarkdownLanguageName("css"))
        assertEquals(MimeType.Known.JSON, MimeType.Known.fromMarkdownLanguageName("json"))
        assertEquals(MimeType.Known.JSON5, MimeType.Known.fromMarkdownLanguageName("json5"))

        assertEquals(MimeType.Known.JSON_LINES, MimeType.Known.fromMarkdownLanguageName("json lines"))
        assertEquals(MimeType.Known.JSON_LINES, MimeType.Known.fromMarkdownLanguageName("jsonl"))

        assertEquals(MimeType.Known.REGEX, MimeType.Known.fromMarkdownLanguageName("regex"))
        assertEquals(MimeType.Known.REGEX, MimeType.Known.fromMarkdownLanguageName("regexp"))

        assertEquals(MimeType.Known.GROOVY, MimeType.Known.fromMarkdownLanguageName("groovy"))
        assertEquals(MimeType.Known.TOML, MimeType.Known.fromMarkdownLanguageName("toml"))
        assertEquals(MimeType.Known.C, MimeType.Known.fromMarkdownLanguageName("c"))
        assertEquals(MimeType.Known.CPP, MimeType.Known.fromMarkdownLanguageName("c++"))
        assertEquals(MimeType.Known.SVG, MimeType.Known.fromMarkdownLanguageName("svg"))
        assertEquals(MimeType.Known.AIDL, MimeType.Known.fromMarkdownLanguageName("aidl"))
        assertEquals(MimeType.Known.SQL, MimeType.Known.fromMarkdownLanguageName("sql"))
        assertEquals(MimeType.Known.PROPERTIES, MimeType.Known.fromMarkdownLanguageName("properties"))
        assertEquals(MimeType.Known.PROTO, MimeType.Known.fromMarkdownLanguageName("protobuf"))

        assertEquals(MimeType.Known.PYTHON, MimeType.Known.fromMarkdownLanguageName("python"))
        assertEquals(MimeType.Known.PYTHON, MimeType.Known.fromMarkdownLanguageName("python2"))
        assertEquals(MimeType.Known.PYTHON, MimeType.Known.fromMarkdownLanguageName("python3"))
        assertEquals(MimeType.Known.PYTHON, MimeType.Known.fromMarkdownLanguageName("py"))

        assertEquals(MimeType.Known.DART, MimeType.Known.fromMarkdownLanguageName("dart"))
        assertEquals(MimeType.Known.RUST, MimeType.Known.fromMarkdownLanguageName("rust"))

        assertEquals(MimeType.Known.JAVASCRIPT, MimeType.Known.fromMarkdownLanguageName("javascript"))
        assertEquals(MimeType.Known.JAVASCRIPT, MimeType.Known.fromMarkdownLanguageName("js"))

        assertEquals(MimeType.Known.TYPESCRIPT, MimeType.Known.fromMarkdownLanguageName("typescript"))
        assertEquals(MimeType.Known.TYPESCRIPT, MimeType.Known.fromMarkdownLanguageName("ts"))

        assertEquals(MimeType.Known.AGSL, MimeType.Known.fromMarkdownLanguageName("sksl"))

        assertEquals(MimeType.Known.SHELL, MimeType.Known.fromMarkdownLanguageName("sh"))
        assertEquals(MimeType.Known.SHELL, MimeType.Known.fromMarkdownLanguageName("bash"))
        assertEquals(MimeType.Known.SHELL, MimeType.Known.fromMarkdownLanguageName("zsh"))
        assertEquals(MimeType.Known.SHELL, MimeType.Known.fromMarkdownLanguageName("shell"))

        assertEquals(MimeType.Known.YAML, MimeType.Known.fromMarkdownLanguageName("yaml"))
        assertEquals(MimeType.Known.YAML, MimeType.Known.fromMarkdownLanguageName("yml"))

        assertEquals(MimeType.Known.GO, MimeType.Known.fromMarkdownLanguageName("golang"))
        assertEquals(MimeType.Known.GO, MimeType.Known.fromMarkdownLanguageName("go"))

        assertEquals(MimeType.Known.DIFF, MimeType.Known.fromMarkdownLanguageName("diff"))
        assertEquals(MimeType.Known.PATCH, MimeType.Known.fromMarkdownLanguageName("patch"))
    }

    @Test
    fun `fromMarkdownLanguageName should return null for unknown languages`() {
        assertNull(MimeType.Known.fromMarkdownLanguageName("unknown-language"))
    }

    @Test
    fun `isKotlin should be true for both plain Kotlin and Gradle KTS`() {
        assertTrue(MimeType.Known.KOTLIN.isKotlin())
        assertTrue(MimeType.Known.GRADLE_KTS.isKotlin())
    }

    @Test
    fun `isJava should be true for MimeTypes with Java`() {
        val mimeType = MimeType("text/java")
        assertTrue(mimeType.isJava())
    }

    @Test
    fun `isJava should be false for MimeTypes without Java`() {
        val mimeType = MimeType("text/not-java")
        assertFalse(mimeType.isJava())
    }

    @Test
    fun `isXml should be true for MimeTypes with Xml`() {
        val mimeType = MimeType("text/xml")
        assertTrue(mimeType.isXml())
    }

    @Test
    fun `isXml should be false for MimeTypes without Xml`() {
        val mimeType = MimeType("text/not-xml")
        assertFalse(mimeType.isXml())
    }

    @Test
    fun `isGradle should be true for MimeTypes with Gradle role`() {
        val mimeType = MimeType("text/x-cool-type; role=gradle")
        assertTrue(mimeType.isGradle())
    }

    @Test
    fun `isGradle should be false for MimeTypes without Gradle role`() {
        val mimeType = MimeType("text/x-cool-type")
        assertFalse(mimeType.isGradle())
    }

    @Test
    fun `isVersionCatalog should be true for MimeTypes with version-catalog role`() {
        val mimeType = MimeType("text/x-cool-type; role=version-catalog")
        assertTrue(mimeType.isVersionCatalog())
    }

    @Test
    fun `isVersionCatalog should be false for MimeTypes without version-catalog role`() {
        val mimeType = MimeType("text/x-cool-type")
        assertFalse(mimeType.isVersionCatalog())
    }

    @Test
    fun `isManifest should be true for MimeTypes with manifest role`() {
        val mimeType = MimeType("text/x-cool-type; role=manifest")
        assertTrue(mimeType.isManifest())
    }

    @Test
    fun `isManifest should be false for MimeTypes without manifest role`() {
        val mimeType = MimeType("text/x-cool-type")
        assertFalse(mimeType.isManifest())
    }

    @Test
    fun `isSql should be true for MimeTypes with SQL base`() {
        val mimeType = MimeType("text/x-sql")
        assertTrue(mimeType.isSql())
    }

    @Test
    fun `isSql should be false for MimeTypes without SQL base`() {
        val mimeType = MimeType("text/x-not-sql")
        assertFalse(mimeType.isSql())
    }

    @Test
    fun `isRegex should be true for MimeTypes with RegEx base`() {
        val mimeType = MimeType("text/x-regex-source")
        assertTrue(mimeType.isRegex())
    }

    @Test
    fun `isRegex should be false for MimeTypes without RegEx base`() {
        val mimeType = MimeType("text/x-not-regex-source")
        assertFalse(mimeType.isRegex())
    }

    @Test
    fun `isProto should be true for MimeTypes with Protobuf base`() {
        val mimeType = MimeType("text/x-protobuf")
        assertTrue(mimeType.isProto())
    }

    @Test
    fun `isProto should be false for MimeTypes without Protobuf base`() {
        val mimeType = MimeType("text/x-not-protobuf")
        assertFalse(mimeType.isProto())
    }
}
