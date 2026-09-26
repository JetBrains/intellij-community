// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.intui.standalone

import java.io.File
import java.util.jar.JarFile
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

// Guards the property that makes this lane worth having, and the reason it is not a jps_test: that runner puts the
// IntelliJ test runtime on the classpath itself, so it could never assert this.
class StandaloneRuntimeSmokeTest {
    @Test
    fun `only the icons API is on the classpath from com dot intellij`() {
        val offenders = classpathJars().map { it to it.unexpectedIntellijClassCount() }.filter { (_, n) -> n > 0 }

        assertTrue(
            offenders.isEmpty(),
            "The IntelliJ Platform has leaked into the standalone lane via: " +
                offenders.joinToString { (jar, n) -> "${jar.name} ($n classes)" },
        )
    }

    @Test
    fun `the IntelliJ Application class cannot be loaded`() {
        val loaded = runCatching { Class.forName("com.intellij.openapi.application.Application") }.isSuccess

        assertTrue(!loaded, "com.intellij.openapi.application.Application must not be reachable from this lane")
    }

    private fun classpathJars(): List<File> =
        System.getProperty("java.class.path").split(File.pathSeparatorChar).map(::File).filter {
            it.isFile && it.name.endsWith(".jar")
        }

    private fun File.unexpectedIntellijClassCount(): Int =
        JarFile(this).use { jar ->
            jar.entries().asSequence().count { entry ->
                entry.name.startsWith("com/intellij/") &&
                    entry.name.endsWith(".class") &&
                    ALLOWED_PACKAGES.none { entry.name.startsWith(it) }
            }
        }

    private companion object {
        // Standalone Jewel depends on the icons API and its implementation on purpose (the jb-icons-* modules), so
        // this is the one com.intellij package the lane is allowed to see. Anything else is a leak.
        private val ALLOWED_PACKAGES = listOf("com/intellij/platform/icons/")
    }
}
