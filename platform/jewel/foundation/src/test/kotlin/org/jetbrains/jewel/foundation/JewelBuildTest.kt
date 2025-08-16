// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.foundation

import java.io.File
import java.util.Properties
import org.junit.Test

internal class JewelBuildTest {
    @Test
    fun `apiVersionString should have the same value as the one in the gradle properties`() {
        val file = File("../gradle.properties")
        if (!file.isFile) {
            error("Cannot load the gradle.properties file from ${file.absolutePath}")
        }

        val expected = loadApiVersion(file)
        if (expected.isBlank()) {
            error("The jewel.release.version value in the gradle.properties file must not be blank")
        }

        assert(JewelBuild.apiVersionString == expected) {
            "The version defined by the jewel.release.version value ($expected) in the gradle.properties file does " +
                "not match the one defined in the JewelBuild.apiVersionString (${JewelBuild.apiVersionString}).\n\n" +
                "You can fix this by running the Jewel version updater script in the jewel/scripts folder."
        }
    }

    private fun loadApiVersion(file: File): String {
        val properties = Properties().apply { file.inputStream().use { load(it) } }
        return properties.getProperty("jewel.release.version").orEmpty()
    }
}
