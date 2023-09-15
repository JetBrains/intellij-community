package org.jetbrains.jewel.bridge

import androidx.compose.ui.res.ResourceLoader
import org.jetbrains.jewel.IntelliJThemeIconData

interface IconMapper {

    fun mapPath(originalPath: String, iconData: IntelliJThemeIconData, resourceLoader: ResourceLoader): String
}
