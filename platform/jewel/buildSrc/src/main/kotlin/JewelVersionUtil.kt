import org.gradle.api.GradleException
import org.gradle.api.Project

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

fun validateJewelVersion(version: String) {
    if (!version.matches("^\\d\\.\\d{2,}\\.\\d+$".toRegex())) {
        throw GradleException("Invalid Jewel version: $version")
    }
}

fun Project.getJewelVersion(): String =
    (properties["versionOverride"] as? String)?.trim()?.takeIf { it.isNotBlank() }
        ?: (properties["jewel.release.version"] as? String)?.trim().orEmpty()
