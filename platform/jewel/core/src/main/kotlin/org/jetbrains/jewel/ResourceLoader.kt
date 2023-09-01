package org.jetbrains.jewel

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.ResourceLoader

val LocalResourceLoader = staticCompositionLocalOf<ResourceLoader> {
    ResourceLoader.Default
}
