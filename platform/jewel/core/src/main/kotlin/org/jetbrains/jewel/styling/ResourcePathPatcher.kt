package org.jetbrains.jewel.styling

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.ResourceLoader

interface ResourcePathPatcher<T> {

    @Composable
    fun patchPath(basePath: String, resourceLoader: ResourceLoader, extraData: T?): String
}
