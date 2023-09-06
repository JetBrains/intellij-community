package org.jetbrains.jewel.bridge

import androidx.compose.ui.res.ResourceLoader

interface IconMapper {

    fun mapPath(originalPath: String, resourceLoader: ResourceLoader): String
}
