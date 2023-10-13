package org.jetbrains.jewel

import androidx.compose.ui.res.ResourceLoader

interface IconMapper {

    fun mapPath(originalPath: String, iconData: IntelliJThemeIconData, resourceLoader: ResourceLoader): String
}

object IntelliJIconMapper : IconMapper {

    private const val VERBOSE = false

    override fun mapPath(
        originalPath: String,
        iconData: IntelliJThemeIconData,
        resourceLoader: ResourceLoader,
    ): String {
        val normalized = "/${originalPath.trimStart('/')}"
        val overriddenPath = iconData.iconOverrides[normalized] ?: normalized

        if (overriddenPath != normalized && VERBOSE) {
            println("Found theme icon override: '$originalPath' -> '$overriddenPath'")
        }

        return overriddenPath
    }
}
