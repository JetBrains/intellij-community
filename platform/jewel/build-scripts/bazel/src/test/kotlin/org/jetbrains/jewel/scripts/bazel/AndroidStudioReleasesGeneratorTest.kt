package org.jetbrains.jewel.scripts.bazel

import com.squareup.kotlinpoet.ClassName
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Test

private fun item(
    name: String,
    channel: String = "Canary",
    date: String = "August 20, 2026",
    build: String = "AI-262.9437.185.2621.16128175",
    platformBuild: String = "262.9437.185",
    platformVersion: String? = "2026.2.1",
    version: String = "2026.2.1.2",
) =
    ApiAndroidStudioReleases.Content.Item(
        build = build,
        channel = channel,
        date = date,
        name = name,
        platformBuild = platformBuild,
        platformVersion = platformVersion,
        version = version,
    )

class AndroidStudioReleasesGeneratorTest {
    private val tmpDir = createSafeTempDir("android-studio-releases-generator-test")

    @After
    fun tearDown() {
        tmpDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    @Test
    fun `withoutDropCounter removes a trailing numeric token`() {
        assertEquals("Quail", "Quail 3".withoutDropCounter())
    }

    @Test
    fun `withoutDropCounter leaves a name with no numeric token untouched`() {
        assertEquals("Rabbit", "Rabbit".withoutDropCounter())
    }

    @Test
    fun `displayTextFor drops the counter only from the animal part before the separator`() {
        assertEquals("Quail | 2026.1.3", displayTextFor("Quail 3 | 2026.1.3"))
    }

    @Test
    fun `displayTextFor leaves a version-only name untouched`() {
        assertEquals("Android Studio 2.4 Preview 4", displayTextFor("Android Studio 2.4 Preview 4"))
    }

    @Test
    fun `translateDate parses a long-form date into year, month, day`() {
        assertEquals("2026, 8, 20", translateDate("August 20, 2026"))
    }

    @Test
    fun `readChannel maps stable-like channels to ReleaseChannel Stable`() {
        assertEquals("ReleaseChannel.Stable", readChannel("Release"))
        assertEquals("ReleaseChannel.Stable", readChannel("patch"))
        assertEquals("ReleaseChannel.Stable", readChannel("STABLE"))
    }

    @Test
    fun `readChannel maps Beta and Canary directly`() {
        assertEquals("ReleaseChannel.Beta", readChannel("beta"))
        assertEquals("ReleaseChannel.Canary", readChannel("canary"))
    }

    @Test
    fun `readChannel falls back to Other for an unrecognized channel`() {
        assertEquals("ReleaseChannel.Other", readChannel("nightly"))
    }

    @Test
    fun `readChannel trims and is case-insensitive`() {
        assertEquals("ReleaseChannel.Stable", readChannel("  Release  "))
    }

    @Test
    fun `imagePathForOrNull returns null when the release name carries no animal`() {
        assertNull(imagePathForOrNull(item(name = "2.4 Preview 4"), emptySet()))
    }

    @Test
    fun `imagePathForOrNull returns null when the animal part still contains a digit`() {
        assertNull(imagePathForOrNull(item(name = "Android Studio 2.4 Preview 4"), emptySet()))
    }

    @Test
    fun `imagePathForOrNull returns null for a channel with no splash screens`() {
        assertNull(imagePathForOrNull(item(name = "Android Studio Rabbit | 2026.2.1", channel = "RC"), emptySet()))
    }

    @Test
    fun `imagePathForOrNull returns null when the expected splash file does not exist`() {
        val emptyDir = tmpDir.resolve("no-splash").also { it.mkdirs() }

        assertNull(imagePathForOrNull(item(name = "Android Studio Rabbit | 2026.2.1"), setOf(emptyDir)))
    }

    @Test
    fun `imagePathForOrNull returns the splash path when the file exists`() {
        val dir = tmpDir.resolve("with-splash").also { it.mkdirs() }
        File(dir, "studio-splash-screens").mkdirs()
        File(dir, "studio-splash-screens/Rabbit-canary.png").writeText("")

        val result = imagePathForOrNull(item(name = "Android Studio Rabbit | 2026.2.1"), setOf(dir))

        assertEquals("\"/studio-splash-screens/Rabbit-canary.png\"", result)
    }

    @Test
    fun `imagePathForOrNull maps beta releases to the stable splash screen`() {
        val dir = tmpDir.resolve("beta-splash").also { it.mkdirs() }
        File(dir, "studio-splash-screens").mkdirs()
        File(dir, "studio-splash-screens/Rabbit-stable.png").writeText("")

        val result = imagePathForOrNull(item(name = "Android Studio Rabbit | 2026.2.1", channel = "Beta"), setOf(dir))

        assertEquals("\"/studio-splash-screens/Rabbit-stable.png\"", result)
    }

    @Test
    fun `readRelease renders every field of the release`() {
        val block = readRelease(item(name = "Android Studio Rabbit | 2026.2.1"), emptySet()).toString()

        assertTrue(block.contains("displayText = \"Android Studio Rabbit | 2026.2.1\""))
        assertTrue(block.contains("versionName = \"2026.2.1.2\""))
        assertTrue(block.contains("build = \"AI-262.9437.185.2621.16128175\""))
        assertTrue(block.contains("channel = ReleaseChannel.Canary"))
        assertTrue(block.contains("releaseDate = LocalDate(2026, 8, 20)"))
        assertTrue(block.contains("key = \"AI-262.9437.185.2621.16128175\""))
    }

    @Test
    fun `readRelease falls back to N-A when the platform version is missing`() {
        val block = readRelease(item(name = "Android Studio Rabbit | 2026.2.1", platformVersion = null), emptySet())

        assertTrue(block.toString().contains("platformVersion = \"N/A\""))
    }

    @Test
    fun `readReleases wraps every release in a single listOf call`() {
        val releases =
            ApiAndroidStudioReleases(
                ApiAndroidStudioReleases.Content(item = listOf(item(name = "A"), item(name = "B")))
            )

        val block = readReleases(releases, emptySet()).toString()

        assertTrue(block.trim().startsWith("listOf("))
        assertTrue(block.contains("\"A\""))
        assertTrue(block.contains("\"B\""))
    }

    @Test
    fun `readFrom emits the header comment, imports and internal object`() {
        val releases =
            ApiAndroidStudioReleases(
                ApiAndroidStudioReleases.Content(item = listOf(item(name = "Android Studio Rabbit | 2026.2.1")))
            )
        val className = ClassName("com.intellij.devkit.compose.demo.releasessample", "AndroidStudioReleases")

        val output =
            readFrom(
                    releases = releases,
                    className = className,
                    url = "https://jb.gg/android-studio-releases-list.json",
                    resourceDirs = emptySet(),
                    modelPackage = "com.intellij.devkit.compose.demo.releasessample",
                    indentString = "  ",
                )
                .toString()

        assertTrue(output.contains("Generated by the Jewel Android Studio Releases Generator"))
        assertTrue(output.contains("Generated from https://jb.gg/android-studio-releases-list.json"))
        assertTrue(output.contains("internal object AndroidStudioReleases"))
        assertTrue(output.contains("displayName: String = \"Android Studio releases\""))
    }
}
