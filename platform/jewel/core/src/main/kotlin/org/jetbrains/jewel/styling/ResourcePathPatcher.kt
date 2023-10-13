package org.jetbrains.jewel.styling

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.ResourceLoader

interface ResourcePathPatcher<T> {

    @Composable
    fun patchVariant(basePath: String, resourceLoader: ResourceLoader, extraData: T?): String

    @Composable
    fun patchTheme(basePath: String, resourceLoader: ResourceLoader): String
}
